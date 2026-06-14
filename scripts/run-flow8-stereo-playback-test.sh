#!/usr/bin/env bash
# Isolated FLOW 8 stereo playback — 440 Hz U01 + 554 Hz U02 for 8 seconds.
#
# No UI, recording, or soundcheck. Listen on the mixer USB returns while the test runs.
#
# Usage:
#   ./scripts/run-flow8-stereo-playback-test.sh --serial 192.168.3.62:45551
#   ./scripts/run-flow8-stereo-playback-test.sh   # first adb device
#
# Logs while running:
#   adb -s SERIAL logcat -s Flow8StereoPlayback:I Router:I Audio:I

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
source "$ROOT/scripts/lib/common.sh"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK/platform-tools/adb"

SERIAL=""
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --skip-build) SKIP_BUILD=true; shift ;;
    -h|--help)
      sed -n '2,14p' "$0"
      exit 0
      ;;
    *)
      [[ -z "$SERIAL" ]] && SERIAL="$1" && shift && continue
      echo "ERROR: unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

adb_dev() {
  if [[ -n "$SERIAL" ]]; then
    "$ADB" -s "$SERIAL" "$@"
  else
    "$ADB" "$@"
  fi
}

log() { echo "[flow8-stereo] $*"; }

if [[ -z "$SERIAL" ]]; then
  SERIAL="$(adb_dev devices 2>/dev/null | awk '/\tdevice$/{print $1; exit}')" || true
fi
[[ -n "$SERIAL" ]] || { echo "ERROR: no adb device" >&2; exit 1; }
log "device: $SERIAL"

if [[ "$SKIP_BUILD" != true ]]; then
  log "building APKs…"
  ensure_java_for_gradle
  (cd "$ROOT" && ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest)
fi

log "installing…"
adb_dev install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb_dev install -r "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

# Physical tablets: install + instrument only — never run grant-usb-permission.sh
# (it killall system_server on emulators and drops wireless adb on tablets).

adb_dev get-state >/dev/null 2>&1 || { echo "ERROR: adb device $SERIAL not connected" >&2; exit 1; }

CLASS="org.openmultitrack.app.Flow8StereoPlaybackInstrumentedTest"
log "running $CLASS (listen on FLOW 8 U01/U02 for ~8s)"
log "logcat: adb -s $SERIAL logcat -s Flow8StereoPlayback:I Router:I Audio:I"

adb_dev shell am instrument -w \
  -e class "$CLASS" \
  org.openmultitrack.test/androidx.test.runner.AndroidJUnitRunner
