#!/usr/bin/env bash
# Grant persistent USB permission for Flow 8 to org.openmultitrack (rooted emulator).
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
FLOW8_VID=5015
FLOW8_PID=1292
FLOW8_CLASS=239
FLOW8_SUBCLASS=2
FLOW8_PROTOCOL=1
PERMS_FILE=/data/system/users/0/usb_permissions.xml

wait_for_boot() {
  for _ in $(seq 1 120); do
    if "${ADB[@]}" "${ADB_FLAGS[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -q '^1$'; then
      sleep 10
      return 0
    fi
    sleep 3
  done
  echo "Emulator did not finish booting." >&2
  exit 1
}

wait_for_boot

if [[ -f "$ROOT/app/build/outputs/apk/debug/app-debug.apk" ]]; then
  "${ADB[@]}" "${ADB_FLAGS[@]}" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null 2>&1 || true
fi

app_uid="$("${ADB[@]}" "${ADB_FLAGS[@]}" shell pm list packages -U "$PACKAGE" 2>/dev/null | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -1)"
test_uid="$("${ADB[@]}" "${ADB_FLAGS[@]}" shell pm list packages -U "$TEST_PACKAGE" 2>/dev/null | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -1)"
if [[ -z "$app_uid" ]]; then
  echo "Could not resolve uid for $PACKAGE" >&2
  exit 1
fi

if ! "${ADB[@]}" "${ADB_FLAGS[@]}" shell dumpsys usb 2>/dev/null | grep -q "product_name=FLOW 8"; then
  echo "Flow 8 not visible in emulator USB host manager — check passthrough." >&2
  exit 1
fi

"${ADB[@]}" "${ADB_FLAGS[@]}" root >/dev/null 2>&1 || true
sleep 2

tmp="$(mktemp)"
{
  echo "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>"
  echo "<permissions>"
  echo "  <permission uid=\"${app_uid}\" granted=\"true\">"
  echo "    <usb-device vendor-id=\"${FLOW8_VID}\" product-id=\"${FLOW8_PID}\" class=\"${FLOW8_CLASS}\" subclass=\"${FLOW8_SUBCLASS}\" protocol=\"${FLOW8_PROTOCOL}\" />"
  echo "  </permission>"
  if [[ -n "$test_uid" ]]; then
    echo "  <permission uid=\"${test_uid}\" granted=\"true\">"
    echo "    <usb-device vendor-id=\"${FLOW8_VID}\" product-id=\"${FLOW8_PID}\" class=\"${FLOW8_CLASS}\" subclass=\"${FLOW8_SUBCLASS}\" protocol=\"${FLOW8_PROTOCOL}\" />"
    echo "  </permission>"
  fi
  echo "</permissions>"
} > "$tmp"

echo "Writing $PERMS_FILE for uid=${app_uid} (test uid=${test_uid:-none})"
"${ADB[@]}" "${ADB_FLAGS[@]}" push "$tmp" "$PERMS_FILE" >/dev/null
rm -f "$tmp"

"${ADB[@]}" "${ADB_FLAGS[@]}" reboot
wait_for_boot

"${ADB[@]}" "${ADB_FLAGS[@]}" shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
"${ADB[@]}" "${ADB_FLAGS[@]}" shell pm grant "$TEST_PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
"${ADB[@]}" "${ADB_FLAGS[@]}" shell settings put global hidden_api_policy 1 2>/dev/null || true

echo "USB permission granted for $PACKAGE."
