# Shared Flow 8 + Android emulator helpers for OpenMultiTrack scripts.
# Source: source "$(dirname "$0")/lib/flow8-emulator.sh"
#
# Expects FLOW8_* and SDK paths to be set by the caller, or uses defaults below.

: "${FLOW8_VID_DEC:=1397}"
: "${FLOW8_PID_DEC:=050c}"
: "${FLOW8_VID_HEX:=0x1397}"
: "${FLOW8_PID_HEX:=0x050c}"
: "${SDK:=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}}"

flow8__adb() {
  local serial="${1:-}"
  shift
  if [[ -n "$serial" ]]; then
    "$SDK/platform-tools/adb" -s "$serial" "$@"
  else
    "$SDK/platform-tools/adb" "$@"
  fi
}

flow8__log()  { echo "[flow8] $*"; }
flow8__warn() { echo "[flow8] WARNING: $*" >&2; }
flow8__die()  { echo "[flow8] ERROR: $*" >&2; exit "${2:-1}"; }

# Detect Behringer Flow 8 on the host (bus + device address change on replug).
flow8_detect_host() {
  local line bus dev
  line="$(lsusb -d "${FLOW8_VID_DEC}:${FLOW8_PID_DEC}" 2>/dev/null | head -1)" || return 1
  if [[ "$line" =~ Bus\ ([0-9]+)\ Device\ ([0-9]+) ]]; then
    FLOW8_HOST_BUS=$((10#${BASH_REMATCH[1]}))
    FLOW8_HOST_ADDR=$((10#${BASH_REMATCH[2]}))
    FLOW8_BUS_DEV="/dev/bus/usb/$(printf '%03d' "$FLOW8_HOST_BUS")/$(printf '%03d' "$FLOW8_HOST_ADDR")"
    return 0
  fi
  return 1
}

flow8_resolve_avd() {
  local emulator="$SDK/emulator/emulator"
  local avd="${1:-}"
  if [[ -z "$avd" ]]; then
    avd="$("$emulator" -list-avds 2>/dev/null | head -1)"
  fi
  [[ -n "$avd" ]] || return 1
  echo "$avd"
}

# Return adb serial for a running QEMU instance of [avd], or empty (any adb state).
emulator_running_serial() {
  local avd="$1"
  pgrep -f "qemu-system.*-avd ${avd}( |$)" >/dev/null 2>&1 || return 1
  flow8__adb "" devices 2>/dev/null | awk '/^emulator-/{print $1; exit}'
}

# Parse hostbus/hostaddr from the running emulator QEMU command line.
emulator_passthrough_addrs() {
  local avd="$1"
  local cmdline
  cmdline="$(pgrep -af "qemu-system.*-avd ${avd}" 2>/dev/null | head -1)" || return 1
  if [[ "$cmdline" =~ hostbus=([0-9]+) ]]; then
    EMU_PT_BUS="${BASH_REMATCH[1]}"
  else
    return 1
  fi
  if [[ "$cmdline" =~ hostaddr=([0-9]+) ]]; then
    EMU_PT_ADDR="${BASH_REMATCH[1]}"
  else
    return 1
  fi
  [[ "$cmdline" =~ -usb-passthrough ]] || return 1
  return 0
}

emulator_flow8_visible() {
  local serial="$1"
  flow8__adb "$serial" shell dumpsys usb 2>/dev/null | grep -q "product_name=FLOW 8"
}

# True when the running emulator matches current host Flow 8 passthrough and guest sees the device.
emulator_passthrough_ok() {
  local avd="$1" serial="$2"
  flow8_detect_host || return 1
  emulator_passthrough_addrs "$avd" || return 1
  [[ "$EMU_PT_BUS" == "$FLOW8_HOST_BUS" && "$EMU_PT_ADDR" == "$FLOW8_HOST_ADDR" ]] || return 1
  emulator_flow8_visible "$serial" || return 1
  return 0
}

emulator_boot_timeout() {
  # Cold boot without KVM can exceed 15 minutes on CI hosts.
  if [[ ! -e /dev/kvm ]]; then
    echo 2400
  else
    echo 900
  fi
}

emulator_wait_for_device() {
  local avd="$1" timeout="${2:-$(emulator_boot_timeout)}"
  local elapsed=0 serial="" state=""
  flow8__log "Waiting for adb device (timeout ${timeout}s)..."
  while (( elapsed < timeout )); do
    if pgrep -f "qemu-system.*-avd ${avd}( |$)" >/dev/null 2>&1; then
      serial="$(flow8__adb "" devices 2>/dev/null | awk '/^emulator-/{print $1; exit}')"
      if [[ -n "$serial" ]]; then
        state="$(flow8__adb "$serial" get-state 2>/dev/null | tr -d '\r' || true)"
        if [[ "$state" == "device" ]]; then
          echo "$serial"
          return 0
        fi
      fi
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    if (( elapsed % 60 == 0 )); then
      flow8__log "  still waiting (${elapsed}s, adb state=${state:-none})..."
    fi
  done
  return 1
}

emulator_wait_for_boot() {
  local serial="$1" timeout="${2:-600}"
  local elapsed=0
  flow8__log "Waiting for boot completion on $serial..."
  while (( elapsed < timeout )); do
    if flow8__adb "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -q '^1$'; then
      if flow8__adb "$serial" shell pm path android >/dev/null 2>&1; then
        sleep 3
        return 0
      fi
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
  flow8__die "Emulator $serial did not finish booting within ${timeout}s"
}

emulator_wait_for_flow8() {
  local serial="$1" max_attempts="${2:-72}"
  local attempt
  for attempt in $(seq 1 "$max_attempts"); do
    if emulator_flow8_visible "$serial"; then
      flow8__log "Flow 8 visible in guest USB manager."
      return 0
    fi
    if [[ "$attempt" -eq 1 ]] || (( attempt % 6 == 0 )); then
      flow8__log "Waiting for Flow 8 in guest ($attempt/$max_attempts)..."
    fi
    sleep 5
  done
  return 1
}

emulator_kill() {
  local serial="$1" elapsed=0
  flow8__log "Stopping emulator $serial..."
  flow8__adb "$serial" emu kill 2>/dev/null || true
  while (( elapsed < 90 )); do
    if ! flow8__adb "" devices 2>/dev/null | awk -v s="$serial" '$1==s && $2=="device"{found=1} END{exit !found}'; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  flow8__warn "Emulator $serial may still be shutting down."
}

emulator_prepare_host_usb() {
  flow8_detect_host || flow8__die "Flow 8 (${FLOW8_VID_DEC}:${FLOW8_PID_DEC}) not found on host — connect USB."
  flow8__log "Host Flow 8: bus=${FLOW8_HOST_BUS} device=${FLOW8_HOST_ADDR} (${FLOW8_BUS_DEV})"
  sudo modprobe -r snd-usb-audio 2>/dev/null || true
  sudo modprobe -r snd-usbmidi-lib 2>/dev/null || true
  if [[ -e "$FLOW8_BUS_DEV" ]]; then
    sudo chmod 666 "$FLOW8_BUS_DEV"
  fi
}

emulator_start_background() {
  local avd="$1" log_file="$2"
  local emulator="$SDK/emulator/emulator"
  flow8_detect_host || return 1

  local accel_args=()
  if [[ ! -e /dev/kvm ]]; then
    flow8__warn "No /dev/kvm — cold boot may take 3–5 minutes."
    accel_args=(-accel off)
  fi

  flow8__log "Starting AVD '$avd' with Flow 8 passthrough (hostbus=${FLOW8_HOST_BUS} hostaddr=${FLOW8_HOST_ADDR})..."
  mkdir -p "$(dirname "$log_file")"
  nohup "$emulator" -avd "$avd" \
    -no-window -no-audio -gpu swiftshader_indirect \
    -no-snapshot-load -no-snapshot-save \
    -writable-system \
    "${accel_args[@]}" \
    -usb-passthrough "vendorid=${FLOW8_VID_HEX},productid=${FLOW8_PID_HEX},hostbus=${FLOW8_HOST_BUS},hostaddr=${FLOW8_HOST_ADDR}" \
    >>"$log_file" 2>&1 &
  echo $!
}
