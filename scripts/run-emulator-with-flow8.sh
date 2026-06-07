#!/usr/bin/env bash
# Start an Android emulator with the host-connected Behringer Flow 8 USB device attached.
#
# Prerequisites (Linux):
#   - Flow 8 connected (lsusb: 1397:050c)
#   - Run this script (uses sudo for USB node permissions + optional audio module unload)
#
# Usage:
#   ./scripts/run-emulator-with-flow8.sh [AVD_NAME]
#
# For full automation (emulator + permissions + tests), prefer:
#   ./scripts/run-flow8-hardware-tests.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
EMULATOR="${SDK}/emulator/emulator"
ADB="${SDK}/platform-tools/adb"

FLOW8_VID="0x1397"
FLOW8_PID="0x050c"
FLOW8_VID_DEC="1397"
FLOW8_PID_DEC="050c"

# Resolve host bus + device address from lsusb (address changes when replugged).
detect_flow8_host_path() {
  local line bus dev
  line="$(lsusb -d "${FLOW8_VID_DEC}:${FLOW8_PID_DEC}" 2>/dev/null | head -1)" || return 1
  if [[ "$line" =~ Bus\ ([0-9]+)\ Device\ ([0-9]+) ]]; then
    bus=$((10#${BASH_REMATCH[1]}))
    dev=$((10#${BASH_REMATCH[2]}))
    FLOW8_HOST_BUS="$bus"
    FLOW8_HOST_ADDR="$dev"
    FLOW8_BUS_DEV="/dev/bus/usb/$(printf '%03d' "$bus")/$(printf '%03d' "$dev")"
    return 0
  fi
  return 1
}

if [[ ! -x "$EMULATOR" ]]; then
  echo "Emulator not found at $EMULATOR" >&2
  exit 1
fi

AVD="${1:-}"
if [[ -z "$AVD" ]]; then
  AVD="$("$EMULATOR" -list-avds | head -1)"
fi
if [[ -z "$AVD" ]]; then
  echo "No AVD found. Create one in Android Studio (API 30+, x86_64 recommended)." >&2
  exit 1
fi

if ! detect_flow8_host_path; then
  echo "Flow 8 (1397:050c) not visible on host — connect it before starting the emulator." >&2
  exit 1
fi

echo "Flow 8 on host: bus=${FLOW8_HOST_BUS} device=${FLOW8_HOST_ADDR} (${FLOW8_BUS_DEV})"

# Reuse an already-running emulator for this AVD (QEMU forbids two writable instances).
if pgrep -f "qemu-system.*-avd ${AVD}( |$)" >/dev/null 2>&1; then
  SERIAL="$("$ADB" devices 2>/dev/null | awk '/^emulator-/{print $1; exit}')"
  echo "Emulator for AVD '$AVD' is already running${SERIAL:+ ($SERIAL)}."
  echo "Reuse it — no need to start again:"
  echo "  ./scripts/setup-emulator-flow8.sh ${SERIAL:-emulator-5554}"
  echo "  ./scripts/run-uac2-instrumented-tests.sh ${SERIAL:-emulator-5554} hardware"
  echo ""
  echo "If Flow 8 is missing in the guest (wrong USB port or replugged), restart:"
  echo "  adb ${SERIAL:+-s $SERIAL }emu kill"
  echo "  ./scripts/run-emulator-with-flow8.sh $AVD"
  exit 0
fi

# Release device from host audio stack when possible.
sudo modprobe -r snd-usb-audio 2>/dev/null || true
sudo modprobe -r snd-usbmidi-lib 2>/dev/null || true

# QEMU needs read/write on the USB character device (often root:root).
if [[ -e "$FLOW8_BUS_DEV" ]]; then
  sudo chmod 666 "$FLOW8_BUS_DEV"
fi

ACCEL_ARGS=()
if [[ ! -e /dev/kvm ]]; then
  echo "No /dev/kvm — using -accel off (cold boot is slow, ~3–5 min)."
  ACCEL_ARGS=(-accel off)
fi

echo "Starting AVD: $AVD with Flow 8 USB passthrough (writable-system for USB host feature)."
echo "After boot:"
echo "  ./scripts/grant-emulator-usb-host.sh    # first time only"
echo "  ./scripts/grant-usb-permission.sh"
echo "  ./scripts/run-uac2-instrumented-tests.sh emulator-5554 hardware"
echo ""
echo "Or run ./scripts/setup-emulator-flow8.sh after the emulator is up."

exec "$EMULATOR" -avd "$AVD" \
  -no-window -no-audio -gpu swiftshader_indirect \
  -no-snapshot-load -no-snapshot-save \
  -writable-system \
  "${ACCEL_ARGS[@]}" \
  -usb-passthrough vendorid=${FLOW8_VID},productid=${FLOW8_PID},hostbus=${FLOW8_HOST_BUS},hostaddr=${FLOW8_HOST_ADDR}
