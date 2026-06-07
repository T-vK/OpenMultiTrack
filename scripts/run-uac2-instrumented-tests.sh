#!/usr/bin/env bash
# Run UAC2 instrumented tests.
#
# Fixture-only tests run on any emulator/device.
# Flow 8 hardware tests require passthrough + USB permission (see run-emulator-with-flow8.sh).
#
# Usage:
#   ./scripts/run-uac2-instrumented-tests.sh [adb-serial] [fixtures|hardware|all]
#   ./scripts/run-uac2-instrumented-tests.sh hardware emulator-5554   # order does not matter
#
# Examples:
#   ./scripts/run-uac2-instrumented-tests.sh emulator-5554 hardware
#   ./scripts/run-uac2-instrumented-tests.sh hardware emulator-5554

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
source "$ROOT/scripts/lib/common.sh"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="${SDK}/platform-tools/adb"

for arg in "$@"; do
  if [[ "$arg" == "-h" || "$arg" == "--help" ]]; then
    sed -n '2,14p' "$0"
    exit 0
  fi
done

omt_parse_serial_and_mode "$@"
SERIAL="$OMT_ADB_SERIAL"
MODE="$OMT_TEST_MODE"

ADB_FLAGS=()
if [[ -n "$SERIAL" ]]; then
  ADB_FLAGS=(-s "$SERIAL")
fi

cd "$ROOT"
chmod +x scripts/run-uac2-native-tests.sh
./scripts/run-uac2-native-tests.sh

echo "Building debug APK + androidTest..."
ensure_java_for_gradle
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon

if ! "${ADB[@]}" "${ADB_FLAGS[@]}" get-state >/dev/null 2>&1; then
  echo "No adb device — skipping instrumented tests." >&2
  if [[ -n "$SERIAL" ]]; then
    echo "Device '$SERIAL' not found. Check: adb devices" >&2
  fi
  echo "Start an emulator (optionally with ./scripts/run-emulator-with-flow8.sh) and re-run." >&2
  exit 0
fi

if [[ "$MODE" == "hardware" || "$MODE" == "all" ]]; then
  chmod +x scripts/setup-emulator-flow8.sh
  if ! ./scripts/setup-emulator-flow8.sh "$SERIAL"; then
    echo "" >&2
    echo "Hardware setup failed. Ensure:" >&2
    echo "  1. Flow 8 is connected on the host (lsusb | grep 1397:050c)" >&2
    echo "  2. Emulator was started with USB passthrough:" >&2
    echo "       ./scripts/run-emulator-with-flow8.sh" >&2
    echo "  3. Then run: ./scripts/setup-emulator-flow8.sh ${SERIAL:-emulator-5554}" >&2
    exit 1
  fi
fi

FIXTURE_TESTS="org.openmultitrack.app.Xr18VirtualSoundcheckInstrumentedTest"
HARDWARE_TESTS="org.openmultitrack.app.UsbAudioRecordingInstrumentedTest,org.openmultitrack.app.Flow8VirtualSoundcheckInstrumentedTest"

run_tests() {
  local class_list="$1"
  "${ADB[@]}" "${ADB_FLAGS[@]}" install -r app/build/outputs/apk/debug/app-debug.apk
  "${ADB[@]}" "${ADB_FLAGS[@]}" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  "${ADB[@]}" "${ADB_FLAGS[@]}" shell am instrument -w -r \
    -e class "$class_list" \
    org.openmultitrack.test/androidx.test.runner.AndroidJUnitRunner
}

case "$MODE" in
  fixtures)
    run_tests "$FIXTURE_TESTS"
    ;;
  hardware)
    run_tests "$HARDWARE_TESTS"
    ;;
  all)
    run_tests "${FIXTURE_TESTS},${HARDWARE_TESTS}"
    ;;
  *)
    echo "Unknown mode: $MODE (fixtures|hardware|all)" >&2
    exit 1
    ;;
esac
