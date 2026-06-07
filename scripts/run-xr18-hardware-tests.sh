#!/usr/bin/env bash
# XR18 multichannel record/playback validation on a physical Android device (USB OTG).
#
# Does NOT send OSC commands or modify XR18 snapshots — USB audio only.
#
# Usage:
#   ./scripts/run-xr18-hardware-tests.sh [adb-serial]
#   ./scripts/run-xr18-hardware-tests.sh --connect 192.168.3.62:34969
#
# Options:
#   --connect HOST:PORT   adb connect before running
#   --skip-build          Use existing APKs
#   --skip-native         Skip host UAC2 native unit tests
#   -h, --help

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
source "$ROOT/scripts/lib/common.sh"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK/platform-tools/adb"

SERIAL=""
CONNECT=""
SKIP_BUILD=false
SKIP_NATIVE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --connect)
      [[ $# -ge 2 ]] || { echo "ERROR: --connect requires HOST:PORT" >&2; exit 1; }
      CONNECT="$2"
      shift 2
      ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    --skip-native) SKIP_NATIVE=true; shift ;;
    -h|--help)
      sed -n '2,15p' "$0"
      exit 0
      ;;
    -*)
      echo "ERROR: unknown option: $1" >&2
      exit 1
      ;;
    *)
      [[ -z "$SERIAL" ]] || { echo "ERROR: unexpected argument: $1" >&2; exit 1; }
      SERIAL="$1"
      shift
      ;;
  esac
done

log()  { echo "[xr18] $*"; }
die()  { echo "[xr18] ERROR: $*" >&2; exit 1; }

adb_cmd() {
  if [[ -n "$SERIAL" ]]; then
    "$ADB" -s "$SERIAL" "$@"
  else
    "$ADB" "$@"
  fi
}

if [[ -n "$CONNECT" ]]; then
  log "Connecting adb to $CONNECT..."
  "$ADB" connect "$CONNECT"
  sleep 2
  SERIAL="$CONNECT"
fi

if [[ -z "$SERIAL" ]]; then
  SERIAL="$(adb_cmd devices 2>/dev/null | awk '/\tdevice$/{print $1; exit}')"
fi
[[ -n "$SERIAL" ]] || die "No adb device found. Pass serial or use --connect HOST:PORT"
log "Using adb device: $SERIAL"

adb_cmd get-state 2>/dev/null | grep -q '^device$' || die "Device $SERIAL not ready"

if ! adb_cmd shell dumpsys usb 2>/dev/null | grep -qi 'XR18\|X18/XR18'; then
  die "XR18 not visible on $SERIAL — connect mixer via USB OTG."
fi

log "XR18 detected on $SERIAL"

log "Waking device for USB permission dialog (if needed)..."
adb_cmd shell input keyevent KEYCODE_WAKEUP 2>/dev/null || true
adb_cmd shell svc power stayon usb 2>/dev/null || true
adb_cmd shell wm dismiss-keyguard 2>/dev/null || true
sleep 2

if [[ "$SKIP_NATIVE" != true ]]; then
  chmod +x "$ROOT/scripts/run-uac2-native-tests.sh"
  "$ROOT/scripts/run-uac2-native-tests.sh"
fi

if [[ "$SKIP_BUILD" != true ]]; then
  log "Building APKs..."
  ensure_java_for_gradle
  (cd "$ROOT" && ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon)
else
  [[ -f "$ROOT/app/build/outputs/apk/debug/app-debug.apk" ]] || die "Debug APK missing"
fi

log "Installing on $SERIAL..."
adb_cmd install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb_cmd install -r "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

adb_cmd shell pm grant org.openmultitrack android.permission.RECORD_AUDIO 2>/dev/null || true
adb_cmd shell pm grant org.openmultitrack.test android.permission.RECORD_AUDIO 2>/dev/null || true

TEST_CLASS="org.openmultitrack.app.Xr18HardwareInstrumentedTest"
log "Running $TEST_CLASS (USB audio only — no OSC / snapshots)..."
log "If prompted on the tablet, tap Allow for USB access to the XR18."
set +e
output="$(adb_cmd shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  org.openmultitrack.test/androidx.test.runner.AndroidJUnitRunner 2>&1)"
test_exit=$?
set -e

printf '%s\n' "$output"

if [[ "$test_exit" -ne 0 ]] \
  || grep -qE 'FAILURES!!!|Tests run:.*Failures: [1-9]' <<<"$output" \
  || grep -q 'INSTRUMENTATION_FAILED' <<<"$output"; then
  die "XR18 hardware tests failed (exit $test_exit)"
fi

log "XR18 hardware tests passed on $SERIAL."
