#!/usr/bin/env bash
# Start an Android emulator with the host-connected Behringer Flow 8 USB device attached.
#
# Prerequisites (Linux):
#   - Flow 8 connected (lsusb: 1397:050c)
#   - User in plugdev group; udev allows the device
#   - ANDROID_HOME / SDK emulator installed
#
# Usage:
#   ./scripts/run-emulator-with-flow8.sh [AVD_NAME]
#
# After boot, grant USB access once:
#   adb shell am start -n org.openmultitrack/.MainActivity
#   (accept the USB permission dialog when prompted)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
EMULATOR="${SDK}/emulator/emulator"
ADB="${SDK}/platform-tools/adb"

FLOW8_VID="0x1397"
FLOW8_PID="0x050c"

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

ACCEL_ARGS=()
if [[ ! -e /dev/kvm ]]; then
  echo "No /dev/kvm — using -accel off (cold boot is slow)."
  ACCEL_ARGS=(-accel off)
fi

echo "Starting AVD: $AVD with Flow 8 USB passthrough ($FLOW8_VID:$FLOW8_PID)"
echo "If passthrough fails, the host may still own the device (lsusb). Stop DAW/PulseAudio using Flow 8, then retry."
# -usb-passthrough is supported on Linux emulator builds.
exec "$EMULATOR" -avd "$AVD" -no-window -no-audio -gpu swiftshader_indirect \
  "${ACCEL_ARGS[@]}" \
  -usb-passthrough "vendorid=${FLOW8_VID},productid=${FLOW8_PID}"
