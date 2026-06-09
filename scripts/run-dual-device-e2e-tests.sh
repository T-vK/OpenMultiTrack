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
#   -h, --help

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
source "$ROOT/scripts/lib/common.sh"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK/platform-tools/adb"

HOST_SERIAL=""
CLIENT_SERIAL=""
SKIP_BUILD=false

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
    -h|--help)
      sed -n '2,18p' "$0"
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
  while IFS= read -r serial; do
    [[ -z "$serial" ]] && continue
    if "$ADB" -s "$serial" shell dumpsys usb 2>/dev/null | grep -qi 'X18/XR18\|XR18'; then
      echo "$serial"
      return 0
    fi
  done < <("$ADB" devices 2>/dev/null | awk '/\tdevice$/{print $1}')
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

wake_device() {
  local serial="$1"
  "$ADB" -s "$serial" shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true
  "$ADB" -s "$serial" shell svc power stayon usb 2>/dev/null || true
  "$ADB" -s "$serial" shell wm dismiss-keyguard 2>/dev/null || true
}

wake_device "$HOST_SERIAL"
wake_device "$CLIENT_SERIAL"
sleep 2

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

install_on "$HOST_SERIAL"
install_on "$CLIENT_SERIAL"

log "Force-stopping app processes before tests..."
"$ADB" -s "$HOST_SERIAL" shell am force-stop org.openmultitrack 2>/dev/null || true
"$ADB" -s "$HOST_SERIAL" shell am force-stop org.openmultitrack.test 2>/dev/null || true
"$ADB" -s "$CLIENT_SERIAL" shell am force-stop org.openmultitrack 2>/dev/null || true
"$ADB" -s "$CLIENT_SERIAL" shell am force-stop org.openmultitrack.test 2>/dev/null || true
sleep 2

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

COMMON_ARGS=(
  -e pairing_pin 424242
  -e host_ip "$HOST_IP"
)

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

log "Running dual-device remote e2e (host + client in parallel)..."
HOST_LOG="$(mktemp)"
CLIENT_LOG="$(mktemp)"
trap 'rm -f "$HOST_LOG" "$CLIENT_LOG"' EXIT

(
  run_instrument "$HOST_SERIAL" \
    -e class org.openmultitrack.app.e2e.RemoteE2eHostTest \
    "${COMMON_ARGS[@]}"
) >"$HOST_LOG" 2>&1 &
HOST_PID=$!

sleep 4

set +e
run_instrument "$CLIENT_SERIAL" \
  -e class org.openmultitrack.app.e2e.RemoteE2eClientTest \
  "${COMMON_ARGS[@]}" >"$CLIENT_LOG" 2>&1
CLIENT_EXIT=$?
set -e

set +e
wait "$HOST_PID"
HOST_EXIT=$?
set -e

cat "$HOST_LOG"
echo "--- client output ---"
cat "$CLIENT_LOG"

[[ "$HOST_EXIT" -eq 0 ]] || die "RemoteE2eHostTest failed"
[[ "$CLIENT_EXIT" -eq 0 ]] || die "RemoteE2eClientTest failed"

log "All dual-device e2e tests passed."
