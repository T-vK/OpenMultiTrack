#!/usr/bin/env bash
# Prepare emulator + passthrough Flow 8 for OpenMultiTrack hardware tests.
#
# - Publishes live USB config descriptor (guest sysfs → app-readable cache)
# - Optional: disable USB permission dialogs via overlay (best-effort)
# - Grants RECORD_AUDIO to app + test packages
#
# Usage:
#   ./scripts/grant-usb-permission.sh [adb-serial]
#   ./scripts/grant-usb-permission.sh --sync-uids-only [adb-serial]   # after APK install

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="${SDK}/platform-tools/adb"

SERIAL=""
SYNC_UIDS_ONLY=false
for arg in "$@"; do
  case "$arg" in
    --sync-uids-only) SYNC_UIDS_ONLY=true ;;
    -h|--help)
      sed -n '2,10p' "$0"
      exit 0
      ;;
    *)
      if [[ -z "$SERIAL" ]]; then
        SERIAL="$arg"
      fi
      ;;
  esac
done

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

grant_usb_uid_in_xml() {
  local uid="$1"
  [[ -z "$uid" ]] && return 0
  "${ADB[@]}" "${ADB_FLAGS[@]}" shell "grep -q 'uid=\\\"$uid\\\"' /data/system/users/0/usb_permissions.xml 2>/dev/null || \
    sed -i 's|</permissions>|  <permission uid=\"$uid\" granted=\"true\"><usb-device vendor-id=\"${FLOW8_VID}\" product-id=\"${FLOW8_PID}\" class=\"239\" subclass=\"2\" protocol=\"1\" /></permission>\\n</permissions>|' /data/system/users/0/usb_permissions.xml" 2>/dev/null || true
}

sync_usb_package_uids() {
  "${ADB[@]}" "${ADB_FLAGS[@]}" root >/dev/null 2>&1 || true
  sleep 1
  local app_uid test_uid uid_marker current_uids
  # pm list FILTER is substring match — anchor package names so .test is not included.
  app_uid="$("${ADB[@]}" "${ADB_FLAGS[@]}" shell pm list packages -U 2>/dev/null \
    | grep "^package:${PACKAGE} uid:" | sed -n 's/.*uid://p' | head -1 | tr -d '\r')"
  test_uid="$("${ADB[@]}" "${ADB_FLAGS[@]}" shell pm list packages -U 2>/dev/null \
    | grep "^package:${TEST_PACKAGE} uid:" | sed -n 's/.*uid://p' | head -1 | tr -d '\r')"
  grant_usb_uid_in_xml "$app_uid"
  grant_usb_uid_in_xml "$test_uid"
  current_uids="${app_uid}:${test_uid}"
  uid_marker="/data/local/tmp/omt_usb_granted_uids"
  local applied_uids
  applied_uids="$("${ADB[@]}" "${ADB_FLAGS[@]}" shell "cat '$uid_marker' 2>/dev/null || true" | tr -d '\r')"
  if [[ -n "$current_uids" && "$current_uids" != "$applied_uids" ]]; then
    echo "Refreshing USB permissions for app uid=$app_uid test uid=$test_uid..."
    "${ADB[@]}" "${ADB_FLAGS[@]}" shell killall system_server 2>/dev/null || true
    wait_for_boot
    "${ADB[@]}" "${ADB_FLAGS[@]}" shell "echo '$current_uids' > '$uid_marker'" 2>/dev/null || true
  fi
}

wait_for_flow8() {
  local max_attempts="${1:-60}"
  for attempt in $(seq 1 "$max_attempts"); do
    if "${ADB[@]}" "${ADB_FLAGS[@]}" shell dumpsys usb 2>/dev/null | grep -q "product_name=FLOW 8"; then
      return 0
    fi
    if [[ "$attempt" -eq 1 ]] || (( attempt % 6 == 0 )); then
      echo "Waiting for Flow 8 in emulator USB host manager ($attempt/$max_attempts)..."
    fi
    sleep 5
  done
  return 1
}

if [[ "$SYNC_UIDS_ONLY" == true ]]; then
  "${ADB[@]}" "${ADB_FLAGS[@]}" root >/dev/null 2>&1 || true
  sleep 1
  sync_usb_package_uids
  FLOW8_NODE="$("${ADB[@]}" "${ADB_FLAGS[@]}" shell dumpsys usb 2>/dev/null \
    | grep -oE '/dev/bus/usb/[0-9]+/[0-9]+' | head -1 | tr -d '\r')" || true
  FLOW8_NODE="${FLOW8_NODE:-/dev/bus/usb/001/002}"
  "${ADB[@]}" "${ADB_FLAGS[@]}" shell "setenforce 0 2>/dev/null || true"
  "${ADB[@]}" "${ADB_FLAGS[@]}" shell "chmod 666 '$FLOW8_NODE' 2>/dev/null || true"
  echo "USB package UIDs synced."
  exit 0
fi

if ! wait_for_flow8; then
  echo "Flow 8 not visible in emulator USB host manager." >&2
  if ! lsusb -d 1397:050c >/dev/null 2>&1; then
    echo "  Host: Flow 8 not found (lsusb). Connect the mixer via USB." >&2
  else
    echo "  Host: Flow 8 is connected — restart the emulator with passthrough:" >&2
    echo "    ./scripts/run-emulator-with-flow8.sh" >&2
  fi
  echo "  Check guest: adb ${ADB_FLAGS[*]} shell dumpsys usb | grep -i flow" >&2
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

# Emulator passthrough: UsbManager.openDevice() often throws SecurityException even when
# usb_permissions.xml shows granted. Allow direct usbfs open via device node fallback.
FLOW8_NODE="$("${ADB[@]}" "${ADB_FLAGS[@]}" shell dumpsys usb 2>/dev/null \
  | grep -oE '/dev/bus/usb/[0-9]+/[0-9]+' | head -1 | tr -d '\r')" || true
FLOW8_NODE="${FLOW8_NODE:-/dev/bus/usb/001/002}"
"${ADB[@]}" "${ADB_FLAGS[@]}" shell "setenforce 0 2>/dev/null || true"
"${ADB[@]}" "${ADB_FLAGS[@]}" shell "chmod 666 '$FLOW8_NODE' 2>/dev/null || true"

"${ADB[@]}" "${ADB_FLAGS[@]}" shell cmd overlay fabricate --target android --name "$OVERLAY_NAME" \
  android:bool/config_disableUsbPermissionDialogs 0x01010001 true >/dev/null 2>&1 || \
"${ADB[@]}" "${ADB_FLAGS[@]}" shell cmd overlay fabricate --target android --name "$OVERLAY_NAME" \
  android:bool/config_disableUsbPermissionDialogs 0x12 0x1 >/dev/null 2>&1 || true
"${ADB[@]}" "${ADB_FLAGS[@]}" shell cmd overlay enable --user 0 "com.android.shell:${OVERLAY_NAME}" >/dev/null 2>&1 || true
# Restart system_server once per emulator boot so the USB permission overlay takes effect.
MARKER="/data/local/tmp/omt_usb_overlay_boot_id"
BOOT_ID="$("${ADB[@]}" "${ADB_FLAGS[@]}" shell settings get global boot_count 2>/dev/null | tr -d '\r')"
MARKER_BOOT="$("${ADB[@]}" "${ADB_FLAGS[@]}" shell "cat '$MARKER' 2>/dev/null || true" | tr -d '\r')"
if [[ -n "$BOOT_ID" && "$BOOT_ID" != "null" && "$BOOT_ID" != "$MARKER_BOOT" ]]; then
  echo "Restarting system_server once to apply USB permission overlay (boot_count=$BOOT_ID)..."
  "${ADB[@]}" "${ADB_FLAGS[@]}" shell killall system_server 2>/dev/null || true
  wait_for_boot
  "${ADB[@]}" "${ADB_FLAGS[@]}" shell "echo '$BOOT_ID' > '$MARKER'" 2>/dev/null || true
  "${ADB[@]}" "${ADB_FLAGS[@]}" shell "setenforce 0 2>/dev/null || true"
  "${ADB[@]}" "${ADB_FLAGS[@]}" shell "chmod 666 '$FLOW8_NODE' 2>/dev/null || true"
fi

"${ADB[@]}" "${ADB_FLAGS[@]}" shell pm grant "$PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
"${ADB[@]}" "${ADB_FLAGS[@]}" shell pm grant "$TEST_PACKAGE" android.permission.RECORD_AUDIO 2>/dev/null || true
"${ADB[@]}" "${ADB_FLAGS[@]}" shell settings put global hidden_api_policy 1 2>/dev/null || true

sync_usb_package_uids
"${ADB[@]}" "${ADB_FLAGS[@]}" shell "setenforce 0 2>/dev/null || true"
"${ADB[@]}" "${ADB_FLAGS[@]}" shell "chmod 666 '$FLOW8_NODE' 2>/dev/null || true"

echo "Emulator Flow 8 ready (live descriptor cache at $CACHE_FILE)."
