#!/usr/bin/env bash
# Run UAC2 instrumented tests.
#
# Fixture-only tests run on any emulator/device.
# Flow 8 hardware tests require passthrough + USB permission (see run-emulator-with-flow8.sh).
#
# Usage:
#   ./scripts/run-uac2-instrumented-tests.sh [adb-serial]
#   ./scripts/run-uac2-instrumented-tests.sh emulator-5554 hardware

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="${SDK}/platform-tools/adb"

SERIAL="${1:-}"
MODE="${2:-all}"

ADB_FLAGS=()
if [[ -n "$SERIAL" ]]; then
  ADB_FLAGS=(-s "$SERIAL")
fi

cd "$ROOT"
chmod +x scripts/run-uac2-native-tests.sh
./scripts/run-uac2-native-tests.sh

echo "Building debug APK + androidTest..."
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :audio-engine:assembleDebugAndroidTest --no-daemon

if ! "${ADB[@]}" "${ADB_FLAGS[@]}" get-state >/dev/null 2>&1; then
  echo "No adb device — skipping instrumented tests." >&2
  echo "Start an emulator (optionally with ./scripts/run-emulator-with-flow8.sh) and re-run." >&2
  exit 0
fi

FIXTURE_TESTS="org.openmultitrack.audio.Uac2FixtureInstrumentedTest,org.openmultitrack.app.Xr18VirtualSoundcheckInstrumentedTest"
HARDWARE_TESTS="org.openmultitrack.audio.Flow8HardwareInstrumentedTest,org.openmultitrack.app.UsbAudioRecordingInstrumentedTest,org.openmultitrack.app.Flow8VirtualSoundcheckInstrumentedTest"

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
