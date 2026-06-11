#!/usr/bin/env bash
# XR18 routing OSC e2e — tablet on same LAN as mixer, USB optional but recommended.
#
# Usage:
#   ./scripts/run-xr18-routing-e2e.sh [adb-serial]
#   ./scripts/run-xr18-routing-e2e.sh --serial R52NB055W6J --osc-host 192.168.3.63
#
# Logs (while test runs):
#   adb logcat -s Xr18RoutingE2e:I OmtE2e:I

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
source "$ROOT/scripts/lib/common.sh"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK/platform-tools/adb"

SERIAL=""
OSC_HOST=""
SKIP_BUILD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --osc-host) OSC_HOST="$2"; shift 2 ;;
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

log() { echo "[routing-e2e] $*"; }

if [[ -z "$SERIAL" ]]; then
  # Prefer the wireless tablet (XR18 host) over other adb devices.
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

EXTRA=()
[[ -n "$OSC_HOST" ]] && EXTRA+=(-e osc_host "$OSC_HOST")

run_test() {
  local class_name="$1"
  log "running $class_name (logcat: adb -s $SERIAL logcat -s Xr18RoutingE2e:I RoutingHooks:W Xr18Routing:W)"
  adb_dev shell am instrument -w "${EXTRA[@]}" \
    -e class "$class_name" \
    org.openmultitrack.test/androidx.test.runner.AndroidJUnitRunner
}

run_test org.openmultitrack.app.e2e.Xr18RoutingOscE2eTest
run_test org.openmultitrack.app.e2e.Xr18RoutingAppE2eTest
