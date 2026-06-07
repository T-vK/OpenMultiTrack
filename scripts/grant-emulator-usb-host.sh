#!/usr/bin/env bash
# Enable android.hardware.usb.host on a writable-system emulator (one-time per AVD data dir).
#
# Usage:
#   ./scripts/grant-emulator-usb-host.sh [adb-serial]

set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="${SDK}/platform-tools/adb"

SERIAL="${1:-}"
ADB_FLAGS=()
if [[ -n "$SERIAL" ]]; then
  ADB_FLAGS=(-s "$SERIAL")
fi

wait_for_boot() {
  for _ in $(seq 1 90); do
    if "${ADB[@]}" "${ADB_FLAGS[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -q '^1$'; then
      return 0
    fi
    sleep 2
  done
  echo "Emulator did not finish booting." >&2
  exit 1
}

wait_for_boot

"${ADB[@]}" "${ADB_FLAGS[@]}" root >/dev/null 2>&1 || true
sleep 2

HOST_XML='/system/etc/permissions/android.hardware.usb.host.xml'
CONTENT='<?xml version="1.0" encoding="utf-8"?><permissions><feature name="android.hardware.usb.host"/></permissions>'

if "${ADB[@]}" "${ADB_FLAGS[@]}" shell "test -f $HOST_XML" 2>/dev/null; then
  if "${ADB[@]}" "${ADB_FLAGS[@]}" shell 'pm list features' 2>/dev/null | grep -q 'android.hardware.usb.host'; then
    echo "USB host feature already enabled."
    exit 0
  fi
fi

remount_out="$("${ADB[@]}" "${ADB_FLAGS[@]}" remount 2>&1 || true)"
echo "$remount_out"

if echo "$remount_out" | grep -qi 'reboot your device'; then
  echo "Rebooting after verity disable..."
  "${ADB[@]}" "${ADB_FLAGS[@]}" reboot
  wait_for_boot
  "${ADB[@]}" "${ADB_FLAGS[@]}" root >/dev/null 2>&1 || true
  sleep 2
  if ! "${ADB[@]}" "${ADB_FLAGS[@]}" remount 2>&1 | grep -qiE 'Remount succeeded|Using overlayfs'; then
    echo "Could not remount /system — start the emulator with -writable-system." >&2
    exit 1
  fi
elif ! echo "$remount_out" | grep -qiE 'Remount succeeded|Using overlayfs|already'; then
  echo "Could not remount /system — start the emulator with -writable-system." >&2
  exit 1
fi

echo "Installing $HOST_XML"
"${ADB[@]}" "${ADB_FLAGS[@]}" shell "echo '$CONTENT' > $HOST_XML"

echo "Rebooting emulator to pick up USB host feature..."
"${ADB[@]}" "${ADB_FLAGS[@]}" reboot
wait_for_boot
echo "USB host feature enabled."
