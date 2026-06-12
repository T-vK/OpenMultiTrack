#!/usr/bin/env bash
# FLOW 8 virtual soundcheck playback e2e — USB host tablet with Behringer FLOW 8 attached.
#
# Usage:
#   ./scripts/run-flow8-soundcheck-e2e.sh [adb-serial]
#   ./scripts/run-flow8-soundcheck-e2e.sh --serial 192.168.3.62:45551
#
# Logs (while test runs):
#   adb -s SERIAL logcat -s Flow8PlaybackE2e:I OmtE2e:I MixerSession:I Player:I

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
      sed -n '2,12p' "$0"
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

log() { echo "[flow8-e2e] $*"; }

if [[ -z "$SERIAL" ]]; then
  SERIAL="$(adb_dev devices -l 2>/dev/null | awk '/gta4xlwifi/ && /\tdevice/{print $1; exit}')" || true
  [[ -n "$SERIAL" ]] || SERIAL="$(adb_dev devices 2>/dev/null | awk '/\tdevice$/{print $1; exit}')" || true
fi
[[ -n "$SERIAL" ]] || { echo "ERROR: no adb device" >&2; exit 1; }
log "device: $SERIAL"

if [[ "$SKIP_BUILD" != true ]]; then
  log "building APKs…"
  (cd "$ROOT" && ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest)
fi

log "installing…"
adb_dev install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb_dev install -r "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

CLASS="org.openmultitrack.app.e2e.Flow8SoundcheckPlaybackE2eTest"
log "running $CLASS"
log "tail logs: adb -s $SERIAL logcat -s Flow8PlaybackE2e:I OmtE2e:I MixerSession:I Player:I"

adb_dev shell am instrument -w \
  -e class "$CLASS" \
  org.openmultitrack.test/androidx.test.runner.AndroidJUnitRunner
