#!/usr/bin/env bash
# Prepare emulator + passthrough Flow 8 for OpenMultiTrack hardware tests.
#
# - Publishes live USB config descriptor (guest sysfs → app-readable cache)
# - Optional: disable USB permission dialogs via overlay (best-effort)
# - Grants RECORD_AUDIO to app + test packages
#
# Usage:
#   ./scripts/grant-usb-permission.sh [adb-serial]

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="${SDK}/platform-tools/adb"

SERIAL="${1:-}"
ADB_FLAGS=()
if [[ -n "$SERIAL" ]]; then
  ADB_FLAGS=(-s "$SERIAL")
fi

PACKAGE=org.openmultitrack
TEST_PACKAGE=org.openmultitrack.test
FLOW8_VID_HEX="1397"
FLOW8_PID_HEX="050c"
FLOW8_VID=5015
FLOW8_PID=1292
CACHE_FILE="/data/local/tmp/omt_usb_${FLOW8_VID}_${FLOW8_PID}.bin"
OVERLAY_NAME="DisableUsbPerm"

wait_for_boot() {
  for _ in $(seq 1 120); do
    if "${ADB[@]}" "${ADB_FLAGS[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -q '^1$'; then
      if "${ADB[@]}" "${ADB_FLAGS[@]}" shell pm path android >/dev/null 2>&1; then
        sleep 3
        return 0
      fi
    fi
    sleep 3
  done
  echo "Emulator did not finish booting." >&2
  exit 1
}

find_sysfs_descriptors() {
  "${ADB[@]}" "${ADB_FLAGS[@]}" shell "
    for d in /sys/bus/usb/devices/*; do
      [ -f \"\$d/idVendor\" ] || continue
      v=\$(cat \"\$d/idVendor\" 2>/dev/null)
      p=\$(cat \"\$d/idProduct\" 2>/dev/null)
      if [ \"\$v\" = \"${FLOW8_VID_HEX}\" ] && [ \"\$p\" = \"${FLOW8_PID_HEX}\" ] && [ -f \"\$d/descriptors\" ]; then
        echo \"\$d/descriptors\"
        exit 0
      fi
    done
    exit 1
  " 2>/dev/null | tr -d '\r'
}

wait_for_boot

if ! "${ADB[@]}" "${ADB_FLAGS[@]}" shell dumpsys usb 2>/dev/null | grep -q "product_name=FLOW 8"; then
  echo "Flow 8 not visible in emulator USB host manager — check passthrough." >&2
  exit 1
fi

if [[ -f "$ROOT/app/build/outputs/apk/debug/app-debug.apk" ]]; then
  "${ADB[@]}" "${ADB_FLAGS[@]}" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null 2>&1 || true
fi

"${ADB[@]}" "${ADB_FLAGS[@]}" root >/dev/null 2>&1 || true
sleep 2

SYSFS_DESC="$(find_sysfs_descriptors)" || {
  echo "Could not find Flow 8 sysfs descriptors path." >&2
  exit 1
}
echo "Publishing live descriptor from $SYSFS_DESC → $CACHE_FILE"
"${ADB[@]}" "${ADB_FLAGS[@]}" shell "cat '$SYSFS_DESC' > '$CACHE_FILE' && chmod 644 '$CACHE_FILE'"

"${ADB[@]}" "${ADB_FLAGS[@]}" shell cmd overlay fabricate --target android --name "$OVERLAY_NAME" \
  android:bool/config_disableUsbPermissionDialogs 0x12 0x1 >/dev/null 2>&1 || true
"${ADB[@]}" "${ADB_FLAGS[@]}" shell cmd overlay enable --user 0 "com.android.shell:${OVERLAY_NAME}" >/dev/null 2>&1 || true

"${ADB[@]}" "${ADB_FLAGS[@]}" shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
"${ADB[@]}" "${ADB_FLAGS[@]}" shell pm grant "$TEST_PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
"${ADB[@]}" "${ADB_FLAGS[@]}" shell settings put global hidden_api_policy 1 2>/dev/null || true

echo "Emulator Flow 8 ready (live descriptor cache at $CACHE_FILE)."
