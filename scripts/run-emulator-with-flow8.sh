#!/usr/bin/env bash
# Start an Android emulator with the host-connected Behringer Flow 8 USB device attached.
#
# Prerequisites (Linux):
#   - Flow 8 connected (lsusb: 1397:050c)
#   - Run this script (uses sudo for USB node permissions + optional audio module unload)
#
# Usage:
#   ./scripts/run-emulator-with-flow8.sh [AVD_NAME]

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
EMULATOR="${SDK}/emulator/emulator"
ADB="${SDK}/platform-tools/adb"

FLOW8_VID="0x1397"
FLOW8_PID="0x050c"
FLOW8_HOST_BUS="1"
FLOW8_HOST_ADDR="6"
FLOW8_BUS_DEV="/dev/bus/usb/${FLOW8_HOST_BUS}/${FLOW8_HOST_ADDR}"

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

if ! lsusb -d 1397:050c >/dev/null 2>&1; then
  echo "Flow 8 (1397:050c) not visible on host — connect it before starting the emulator." >&2
  exit 1
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
echo "  ./scripts/grant-emulator-usb-host.sh"
echo "  ./scripts/grant-usb-permission.sh"
echo "  ./scripts/run-uac2-instrumented-tests.sh emulator-5554 hardware"

exec "$EMULATOR" -avd "$AVD" \
  -no-window -no-audio -gpu swiftshader_indirect \
  -no-snapshot-load -no-snapshot-save \
  -writable-system \
  "${ACCEL_ARGS[@]}" \
  -usb-passthrough vendorid=${FLOW8_VID},productid=${FLOW8_PID},hostbus=${FLOW8_HOST_BUS},hostaddr=${FLOW8_HOST_ADDR}
