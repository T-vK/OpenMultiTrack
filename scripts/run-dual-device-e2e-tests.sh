#!/usr/bin/env bash
# Dual-device end-to-end tests for OpenMultiTrack.
#
# Requires two adb-connected tablets on the same LAN:
#   - Host: USB OTG to XR18 (multitrack recording / soundcheck engine)
#   - Client: remote control mirror (no USB mixer required)
#
# Usage:
#   ./scripts/run-dual-device-e2e-tests.sh [host-serial] [client-serial]
#   ./scripts/run-dual-device-e2e-tests.sh --host 192.168.3.62:40889 --client 192.168.3.42:42513
#
# Options:
#   --host SERIAL       adb serial for USB host tablet
#   --client SERIAL     adb serial for remote client tablet
#   --skip-build        Use existing debug + androidTest APKs
#   --no-install        Skip adb install (use when APKs are already on devices)
#   --remote-only       Run only dual-device remote e2e (skip other host tests)
#   -h, --help
#
# Wireless adb: this script never touches Wi-Fi or wireless debugging settings.
# Avoid --no-install only when you must push new APKs; adb install restarts adbd
# and often changes the wireless debugging port.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
source "$ROOT/scripts/lib/common.sh"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK/platform-tools/adb"

HOST_SERIAL=""
CLIENT_SERIAL=""
SKIP_BUILD=false
SKIP_INSTALL=false
REMOTE_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      [[ $# -ge 2 ]] || { echo "ERROR: --host requires SERIAL" >&2; exit 1; }
      HOST_SERIAL="$2"
      shift 2
      ;;
    --client)
      [[ $# -ge 2 ]] || { echo "ERROR: --client requires SERIAL" >&2; exit 1; }
      CLIENT_SERIAL="$2"
      shift 2
      ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    --no-install) SKIP_INSTALL=true; shift ;;
    --remote-only) REMOTE_ONLY=true; shift ;;
    -h|--help)
      sed -n '2,19p' "$0"
      exit 0
      ;;
    -*)
      echo "ERROR: unknown option: $1" >&2
      exit 1
      ;;
    *)
      [[ -z "$HOST_SERIAL" ]] && HOST_SERIAL="$1" && shift && continue
      [[ -z "$CLIENT_SERIAL" ]] && CLIENT_SERIAL="$1" && shift && continue
      echo "ERROR: unexpected argument: $1" >&2
      exit 1
      ;;
  esac
done

log()  { echo "[e2e] $*"; }
die()  { echo "[e2e] ERROR: $*" >&2; exit 1; }

adb_host() {
  if [[ -n "$HOST_SERIAL" ]]; then
    "$ADB" -s "$HOST_SERIAL" "$@"
  else
    "$ADB" "$@"
  fi
}

adb_client() {
  if [[ -n "$CLIENT_SERIAL" ]]; then
    "$ADB" -s "$CLIENT_SERIAL" "$@"
  else
    "$ADB" "$@"
  fi
}

detect_host_with_xr18() {
  local fallback=""
  while IFS= read -r serial; do
    [[ -z "$serial" ]] && continue
    local usb_dump
    usb_dump="$("$ADB" -s "$serial" shell dumpsys usb 2>/dev/null | strings)"
    if ! grep -qi 'X18/XR18\|XR18' <<<"$usb_dump"; then
      continue
    fi
    # Prefer tablets where Android exposes the mixer as a full UAC device (visible to UsbManager).
    if grep -q 'X18/XR18 Audio In' <<<"$usb_dump" && grep -q 'X18/XR18 Audio Out' <<<"$usb_dump"; then
      echo "$serial"
      return 0
    fi
    [[ -z "$fallback" ]] && fallback="$serial"
  done < <("$ADB" devices 2>/dev/null | awk '/\tdevice$/{print $1}')
  if [[ -n "$fallback" ]]; then
    echo "$fallback"
    return 0
  fi
  return 1
}

detect_client_without_xr18() {
  local host="$1"
  while IFS= read -r serial; do
    [[ -z "$serial" || "$serial" == "$host" ]] && continue
    if ! "$ADB" -s "$serial" shell dumpsys usb 2>/dev/null | grep -qi 'X18/XR18\|XR18'; then
      echo "$serial"
      return 0
    fi
  done < <("$ADB" devices 2>/dev/null | awk '/\tdevice$/{print $1}')
  return 1
}

if [[ -z "$HOST_SERIAL" ]]; then
  HOST_SERIAL="$(detect_host_with_xr18)" || die "No adb device with XR18 found. Pass --host SERIAL."
fi
if [[ -z "$CLIENT_SERIAL" ]]; then
  CLIENT_SERIAL="$(detect_client_without_xr18 "$HOST_SERIAL")" || die "No second adb device found for remote client. Pass --client SERIAL."
fi

log "Host (USB/XR18): $HOST_SERIAL"
log "Client (remote): $CLIENT_SERIAL"

for serial in "$HOST_SERIAL" "$CLIENT_SERIAL"; do
  "$ADB" -s "$serial" get-state 2>/dev/null | grep -q '^device$' || die "Device $serial not ready"
done

adb_host -s "$HOST_SERIAL" shell dumpsys usb 2>/dev/null | grep -qi 'X18/XR18\|XR18' \
  || die "XR18 not visible on host $HOST_SERIAL"

is_wireless_serial() {
  [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+$ ]]
}

wireless_ip() {
  [[ "$1" =~ ^([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+): ]] && echo "${BASH_REMATCH[1]}"
}

# Reconnect wireless adb without touching Wi-Fi or wireless debugging settings.
reconnect_wireless_adb() {
  local serial="$1"
  is_wireless_serial "$serial" || return 0
  if "$ADB" -s "$serial" get-state 2>/dev/null | grep -q '^device$'; then
    return 0
  fi
  local ip port
  ip="$(wireless_ip "$serial")"
  port="${serial##*:}"
  log "Wireless adb offline for $serial — reconnecting (Wi-Fi left unchanged)"
  "$ADB" disconnect "$serial" 2>/dev/null || true
  "$ADB" connect "$serial" 2>/dev/null || true
  if "$ADB" -s "$serial" get-state 2>/dev/null | grep -q '^device$'; then
    log "Reconnected $serial"
    return 0
  fi
  # Try current wireless debugging port from the device (rooted builds).
  local live_port
  live_port="$("$ADB" -s "$serial" shell dumpsys adb 2>/dev/null \
    | grep -oE 'port=[0-9]+' | head -1 | cut -d= -f2 | tr -d '\r')" || true
  if [[ -n "$live_port" && "$live_port" != "$port" ]]; then
    serial="${ip}:${live_port}"
    log "Trying updated wireless port ${serial}"
    "$ADB" connect "$serial" 2>/dev/null || true
  fi
  if "$ADB" -s "$serial" get-state 2>/dev/null | grep -q '^device$'; then
    log "Reconnected $serial"
    return 0
  fi
  # mDNS fallback for paired tablets (same host, new TLS port).
  while IFS= read -r mdns; do
    [[ -z "$mdns" ]] && continue
    if "$ADB" -s "$mdns" shell ip -4 addr show wlan0 2>/dev/null | grep -q "$ip"; then
      log "Falling back to mDNS transport $mdns"
      if [[ "$serial" == "$HOST_SERIAL" ]]; then
        HOST_SERIAL="$mdns"
      elif [[ "$serial" == "$CLIENT_SERIAL" ]]; then
        CLIENT_SERIAL="$mdns"
      fi
      return 0
    fi
  done < <("$ADB" devices 2>/dev/null | awk '/_adb-tls-connect/ && /device$/{print $1}')
  return 1
}

client_can_reach_host() {
  local serial="$1"
  local host_ip="$2"
  "$ADB" -s "$serial" shell ping -c 1 -W 3 "$host_ip" 2>/dev/null | grep -qE '1 (packets )?received'
}

client_wlan_up() {
  local serial="$1"
  ! "$ADB" -s "$serial" shell ip link show wlan0 2>/dev/null | grep -qE 'state DOWN|NO-CARRIER'
}

LAST_CLIENT_WIFI_RESTART=0

# USB client tablets sometimes drop wlan0 during long runs; rooted builds can bounce Wi-Fi.
restart_client_wifi_if_needed() {
  local serial="$1"
  local host_ip="$2"
  is_wireless_serial "$serial" && return 0
  if client_can_reach_host "$serial" "$host_ip"; then
    return 0
  fi
  # wlan0 down: recover immediately; otherwise require repeated ping loss (transient drops happen).
  if client_wlan_up "$serial"; then
    local attempt
    for attempt in 1 2; do
      sleep 3
      if client_can_reach_host "$serial" "$host_ip"; then
        return 0
      fi
    done
  fi
  local now
  now="$(date +%s)"
  if (( now - LAST_CLIENT_WIFI_RESTART < 90 )); then
    log "Skipping client Wi-Fi restart (cooldown; last $((now - LAST_CLIENT_WIFI_RESTART))s ago)"
    return 1
  fi
  log "Client $serial lost LAN reachability to $host_ip — restarting Wi-Fi (su)"
  "$ADB" -s "$serial" shell su -c 'svc wifi disable ; svc wifi enable' 2>/dev/null || true
  LAST_CLIENT_WIFI_RESTART="$now"
  local i
  for ((i = 1; i <= 12; i++)); do
    sleep 5
    if client_can_reach_host "$serial" "$host_ip"; then
      log "Client Wi-Fi restored after ${i}x5s"
      return 0
    fi
  done
  log "WARNING: client still cannot reach $host_ip after Wi-Fi restart"
  return 1
}

require_device_online() {
  local serial="$1"
  local label="$2"
  if "$ADB" -s "$serial" get-state 2>/dev/null | grep -q '^device$'; then
    return 0
  fi
  reconnect_wireless_adb "$serial" || die "$label $serial is not online (reconnect wireless adb manually)"
}

if [[ "$SKIP_BUILD" != true ]]; then
  log "Building APKs..."
  ensure_java_for_gradle
  (cd "$ROOT" && ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon)
else
  [[ -f "$ROOT/app/build/outputs/apk/debug/app-debug.apk" ]] || die "Debug APK missing"
fi

install_on() {
  local serial="$1"
  log "Installing on $serial..."
  "$ADB" -s "$serial" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  "$ADB" -s "$serial" install -r "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
  "$ADB" -s "$serial" shell pm grant org.openmultitrack android.permission.RECORD_AUDIO 2>/dev/null || true
  "$ADB" -s "$serial" shell pm grant org.openmultitrack.test android.permission.RECORD_AUDIO 2>/dev/null || true
}

if [[ "$SKIP_INSTALL" != true ]]; then
  install_on "$HOST_SERIAL"
  install_on "$CLIENT_SERIAL"
  log "APK install done — if wireless adb dropped, reconnect (port may have changed)"
else
  log "Skipping APK install (--no-install)"
fi

if [[ "$REMOTE_ONLY" != true ]]; then
  log "Force-stopping app processes before non-remote tests..."
  "$ADB" -s "$HOST_SERIAL" shell am force-stop org.openmultitrack 2>/dev/null || true
  "$ADB" -s "$HOST_SERIAL" shell am force-stop org.openmultitrack.test 2>/dev/null || true
  "$ADB" -s "$CLIENT_SERIAL" shell am force-stop org.openmultitrack 2>/dev/null || true
  "$ADB" -s "$CLIENT_SERIAL" shell am force-stop org.openmultitrack.test 2>/dev/null || true
  sleep 2
fi

run_instrument() {
  local serial="$1"
  shift
  set +e
  local output
  output="$("$ADB" -s "$serial" shell am instrument -w -r "$@" \
    org.openmultitrack.test/androidx.test.runner.AndroidJUnitRunner 2>&1)"
  local exit_code=$?
  set -e
  printf '%s\n' "$output"
  if [[ "$exit_code" -ne 0 ]] \
    || grep -qE 'FAILURES!!!|Tests run:.*Failures: [1-9]' <<<"$output" \
    || grep -qE 'INSTRUMENTATION_FAILED|Process crashed' <<<"$output"; then
    return 1
  fi
  return 0
}

HOST_IP="$("$ADB" -s "$HOST_SERIAL" shell ip -4 addr show wlan0 2>/dev/null \
  | awk '/inet /{print $2}' | cut -d/ -f1 | head -1 | tr -d '\r')"
[[ -n "$HOST_IP" ]] || HOST_IP="$("$ADB" -s "$HOST_SERIAL" shell ip route get 1.1.1.1 2>/dev/null \
  | awk '{for (i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}' | tr -d '\r')"
[[ -n "$HOST_IP" ]] || die "Could not resolve host tablet LAN IP"

log "Host LAN IP: $HOST_IP"

REMOTE_PORT=8765
ADB_FORWARD_PORT=18765
CLIENT_HOST_IP="$HOST_IP"
BRIDGE_PID=""

cleanup_remote_bridge() {
  [[ -n "$BRIDGE_PID" ]] && kill "$BRIDGE_PID" 2>/dev/null || true
  "$ADB" -s "$HOST_SERIAL" forward --remove "tcp:$ADB_FORWARD_PORT" 2>/dev/null || true
}

setup_remote_bridge_if_needed() {
  if client_can_reach_host "$CLIENT_SERIAL" "$HOST_IP"; then
    log "Client reaches host directly on LAN"
    return 0
  fi
  local pc_ip
  pc_ip="$(ip -4 route get "$HOST_IP" 2>/dev/null | awk '{for (i=1;i<=NF;i++) if ($i=="src") {print $(i+1); exit}}')"
  [[ -n "$pc_ip" ]] || die "Could not resolve PC LAN IP for remote bridge"
  log "Tablet-to-tablet traffic blocked (AP isolation?) — bridging via PC at ${pc_ip}:${REMOTE_PORT}"
  "$ADB" -s "$HOST_SERIAL" forward --remove "tcp:$ADB_FORWARD_PORT" 2>/dev/null || true
  "$ADB" -s "$HOST_SERIAL" forward "tcp:$ADB_FORWARD_PORT" "tcp:$REMOTE_PORT"
  python3 "$ROOT/scripts/tcp-bridge.py" "$pc_ip" "$REMOTE_PORT" "$ADB_FORWARD_PORT" &
  BRIDGE_PID=$!
  sleep 1
  CLIENT_HOST_IP="$pc_ip"
}

COMMON_ARGS=(
  -e pairing_pin 424242
  -e host_ip "$HOST_IP"
)
CLIENT_ARGS=(
  -e pairing_pin 424242
  -e host_ip "$CLIENT_HOST_IP"
)

if [[ "$REMOTE_ONLY" != true ]]; then
  log "Running host zoom e2e on $HOST_SERIAL (fresh USB)..."
  run_instrument "$HOST_SERIAL" \
    -e class org.openmultitrack.app.e2e.HostZoomE2eTest \
    "${COMMON_ARGS[@]}" \
    || die "HostZoomE2eTest failed"

  log "Running host-local e2e on $HOST_SERIAL..."
  "$ADB" -s "$HOST_SERIAL" shell am force-stop org.openmultitrack 2>/dev/null || true
  "$ADB" -s "$HOST_SERIAL" shell am force-stop org.openmultitrack.test 2>/dev/null || true
  sleep 5
  run_instrument "$HOST_SERIAL" \
    -e class org.openmultitrack.app.e2e.HostLocalE2eTest \
    "${COMMON_ARGS[@]}" \
    || die "HostLocalE2eTest failed"

  log "Running interrupted-recording prep on $HOST_SERIAL..."
  run_instrument "$HOST_SERIAL" \
    -e class org.openmultitrack.app.e2e.InterruptedRecordingPrepE2eTest \
    "${COMMON_ARGS[@]}" \
    || die "InterruptedRecordingPrepE2eTest failed"

  log "Force-stopping host app mid-recording..."
  "$ADB" -s "$HOST_SERIAL" shell am force-stop org.openmultitrack.test 2>/dev/null || true
  "$ADB" -s "$HOST_SERIAL" shell am force-stop org.openmultitrack 2>/dev/null || true
  sleep 5

  log "Running interrupted-recording resume on $HOST_SERIAL..."
  run_instrument "$HOST_SERIAL" \
    -e class org.openmultitrack.app.e2e.InterruptedRecordingResumeE2eTest \
    "${COMMON_ARGS[@]}" \
    || die "InterruptedRecordingResumeE2eTest failed"
else
  log "Skipping non-remote e2e (--remote-only)"
fi

log "Running dual-device remote e2e (host + client in parallel)..."
require_device_online "$HOST_SERIAL" "Host"
require_device_online "$CLIENT_SERIAL" "Client"
restart_client_wifi_if_needed "$CLIENT_SERIAL" "$HOST_IP"
setup_remote_bridge_if_needed
CLIENT_ARGS=(
  -e pairing_pin 424242
  -e host_ip "$CLIENT_HOST_IP"
)
[[ "$CLIENT_HOST_IP" == "$HOST_IP" ]] || log "Client will connect via bridge host_ip=$CLIENT_HOST_IP"
HOST_LOG="$(mktemp)"
CLIENT_LOG="$(mktemp)"
trap 'cleanup_remote_bridge; rm -f "$HOST_LOG" "$CLIENT_LOG"' EXIT

# Start the host first (records + soundcheck before opening remote port).
# The client test blocks on awaitHostRemoteReady until the host is listening.
set +e
(
  run_instrument "$HOST_SERIAL" \
    -e class org.openmultitrack.app.e2e.RemoteE2eHostTest \
    "${COMMON_ARGS[@]}"
) >"$HOST_LOG" 2>&1 &
HOST_PID=$!
set -e

sleep 3

set +e
run_instrument "$CLIENT_SERIAL" \
  -e class org.openmultitrack.app.e2e.RemoteE2eClientTest \
  "${CLIENT_ARGS[@]}" >"$CLIENT_LOG" 2>&1 &
CLIENT_PID=$!
set -e

ADB_KEEPALIVE_PID=""
start_adb_keepalive() {
  (
    sleep 45
    while kill -0 "$CLIENT_PID" 2>/dev/null || kill -0 "$HOST_PID" 2>/dev/null; do
      reconnect_wireless_adb "$HOST_SERIAL" || true
      restart_client_wifi_if_needed "$CLIENT_SERIAL" "$HOST_IP" || true
      sleep 30
    done
  ) &
  ADB_KEEPALIVE_PID=$!
}

start_adb_keepalive

set +e
wait "$CLIENT_PID"
CLIENT_EXIT=$?
wait "$HOST_PID"
HOST_EXIT=$?
[[ -n "$ADB_KEEPALIVE_PID" ]] && kill "$ADB_KEEPALIVE_PID" 2>/dev/null || true
set -e

cat "$HOST_LOG"
echo "--- client output ---"
cat "$CLIENT_LOG"

[[ "$HOST_EXIT" -eq 0 ]] || die "RemoteE2eHostTest failed"
[[ "$CLIENT_EXIT" -eq 0 ]] || die "RemoteE2eClientTest failed"

log "All dual-device e2e tests passed."
