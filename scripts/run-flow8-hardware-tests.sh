#!/usr/bin/env bash
# Fully automated Flow 8 hardware validation on the Android emulator.
#
# Idempotent orchestrator: reuses a correctly configured running emulator, restarts
# only when passthrough is stale or Flow 8 is missing in the guest, then prepares
# permissions and runs UAC2 record/playback instrumented tests.
#
# Prerequisites (Linux):
#   - Android SDK (ANDROID_HOME or ~/Android/Sdk)
#   - Behringer Flow 8 connected (lsusb: 1397:050c)
#   - sudo for host USB node permissions (passwordless sudo recommended for CI)
#   - Writable-system AVD (e.g. OpenMultiTrack_Flow8)
#
# Usage:
#   ./scripts/run-flow8-hardware-tests.sh [options] [AVD_NAME]
#
# Options:
#   --avd NAME         AVD to use (default: first listed)
#   --serial SERIAL    Pin adb serial (skip auto-detect; still validates Flow 8)
#   --skip-build       Skip Gradle assemble (use existing APKs)
#   --skip-tests       Prepare emulator only; do not run instrumented tests
#   --skip-native      Skip host UAC2 native unit tests
#   -h, --help         Show this help
#
# Examples:
#   ./scripts/run-flow8-hardware-tests.sh
#   ./scripts/run-flow8-hardware-tests.sh --avd OpenMultiTrack_Flow8
#   ./scripts/run-flow8-hardware-tests.sh --skip-build

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/common.sh
source "$ROOT/scripts/lib/common.sh"
# shellcheck source=lib/flow8-emulator.sh
source "$ROOT/scripts/lib/flow8-emulator.sh"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"

OMT_AVD=""
OMT_SERIAL=""
OMT_SKIP_BUILD=false
OMT_SKIP_TESTS=false
OMT_SKIP_NATIVE=false

usage() {
  sed -n '2,28p' "$0"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --avd)
        [[ $# -ge 2 ]] || flow8__die "--avd requires a value"
        OMT_AVD="$2"
        shift 2
        ;;
      --serial)
        [[ $# -ge 2 ]] || flow8__die "--serial requires a value"
        OMT_SERIAL="$2"
        shift 2
        ;;
      --skip-build) OMT_SKIP_BUILD=true; shift ;;
      --skip-tests) OMT_SKIP_TESTS=true; shift ;;
      --skip-native) OMT_SKIP_NATIVE=true; shift ;;
      -h|--help) usage; exit 0 ;;
      -*)
        flow8__die "Unknown option: $1 (try --help)"
        ;;
      *)
        if [[ -z "$OMT_AVD" ]]; then
          OMT_AVD="$1"
        else
          flow8__die "Unexpected argument: $1"
        fi
        shift
        ;;
    esac
  done
}

require_tools() {
  [[ -x "$EMULATOR" ]] || flow8__die "Emulator not found at $EMULATOR"
  [[ -x "$ADB" ]] || flow8__die "adb not found at $ADB"
  command -v lsusb >/dev/null 2>&1 || flow8__die "lsusb not found"
}

ensure_emulator() {
  local avd serial log_file reuse=false

  avd="$(flow8_resolve_avd "$OMT_AVD")" || flow8__die "No AVD found. Create one in Android Studio (API 30+, x86_64)."
  flow8__log "AVD: $avd"

  emulator_prepare_host_usb

  if [[ -n "$OMT_SERIAL" ]]; then
    serial="$OMT_SERIAL"
    flow8__adb "$serial" get-state >/dev/null 2>&1 || flow8__die "Device '$serial' not reachable via adb"
    if emulator_passthrough_ok "$avd" "$serial"; then
      reuse=true
    elif emulator_flow8_visible "$serial"; then
      flow8__warn "Pinned serial $serial has Flow 8 but passthrough addr may be stale — continuing."
      reuse=true
    else
      flow8__warn "Pinned serial $serial missing Flow 8 — restarting emulator."
      emulator_kill "$serial"
      serial=""
    fi
  else
    serial="$(emulator_running_serial "$avd" || true)"
    if [[ -n "$serial" ]]; then
      if emulator_passthrough_addrs "$avd" \
        && [[ "$EMU_PT_BUS" == "$FLOW8_HOST_BUS" && "$EMU_PT_ADDR" == "$FLOW8_HOST_ADDR" ]]; then
        flow8__log "Reusing $serial (USB passthrough address matches host)."
        reuse=true
      else
        flow8__log "Running emulator $serial has stale USB passthrough — restarting."
        emulator_kill "$serial"
        serial=""
      fi
    fi
  fi

  if [[ "$reuse" == true ]]; then
    flow8__log "Reusing emulator $serial."
  else
    log_file="$ROOT/.cache/omt-emulator-${avd}.log"
    emulator_start_background "$avd" "$log_file" >/dev/null
    flow8__log "Emulator log: $log_file"
    serial="$(emulator_wait_for_device "$avd")" || flow8__die "Emulator did not appear on adb (timeout $(emulator_boot_timeout)s)."
    flow8__log "Emulator online: $serial"
  fi

  emulator_wait_for_boot "$serial" 600
  OMT_SERIAL="$serial"
}

prepare_guest() {
  flow8__log "Preparing guest USB host + Flow 8 permissions on $OMT_SERIAL..."

  chmod +x "$ROOT/scripts/grant-emulator-usb-host.sh"
  chmod +x "$ROOT/scripts/grant-usb-permission.sh"

  if ! "$ROOT/scripts/grant-emulator-usb-host.sh" "$OMT_SERIAL"; then
    flow8__warn "USB host feature setup reported an issue (writable-system may already be configured)."
  fi
  emulator_wait_for_boot "$OMT_SERIAL" 600

  if ! "$ROOT/scripts/grant-usb-permission.sh" "$OMT_SERIAL"; then
    flow8__die "Flow 8 permission setup failed."
  fi
  emulator_wait_for_boot "$OMT_SERIAL" 300

  if ! emulator_wait_for_flow8 "$OMT_SERIAL" 24; then
    flow8__die "Flow 8 not visible in guest after setup. Check host USB and emulator log."
  fi
}

build_apks() {
  if [[ "$OMT_SKIP_BUILD" == true ]]; then
    flow8__log "Skipping build (--skip-build)."
    [[ -f "$ROOT/app/build/outputs/apk/debug/app-debug.apk" ]] || \
      flow8__die "Debug APK missing — run without --skip-build."
    return 0
  fi

  flow8__log "Building debug APK + androidTest..."
  ensure_java_for_gradle
  (cd "$ROOT" && ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon)
}

run_native_tests() {
  if [[ "$OMT_SKIP_NATIVE" == true ]]; then
    flow8__log "Skipping native UAC2 tests (--skip-native)."
    return 0
  fi
  chmod +x "$ROOT/scripts/run-uac2-native-tests.sh"
  "$ROOT/scripts/run-uac2-native-tests.sh"
}

install_apks_and_sync_permissions() {
  flow8__log "Installing APKs on $OMT_SERIAL..."
  flow8__adb "$OMT_SERIAL" install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  flow8__adb "$OMT_SERIAL" install -r "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

  flow8__log "Syncing USB permission UIDs after install..."
  "$ROOT/scripts/grant-usb-permission.sh" --sync-uids-only "$OMT_SERIAL"
  emulator_wait_for_boot "$OMT_SERIAL" 300
}

verify_test_ready() {
  if ! emulator_flow8_visible "$OMT_SERIAL"; then
    flow8__die "Flow 8 disappeared from guest before tests."
  fi

  local app_uid
  app_uid="$(flow8__adb "$OMT_SERIAL" shell pm list packages -U 2>/dev/null \
    | grep '^package:org.openmultitrack uid:' | sed -n 's/.*uid://p' | head -1 | tr -d '\r')"
  if [[ -z "$app_uid" ]]; then
    flow8__die "org.openmultitrack not installed."
  fi
  if ! flow8__adb "$OMT_SERIAL" shell dumpsys usb 2>/dev/null | grep -A2 "uid=$app_uid" | grep -q 'is_granted=true'; then
    flow8__warn "USB permission for uid=$app_uid not confirmed in dumpsys — tests may still pass via overlay."
  else
    flow8__log "USB permission confirmed for app uid=$app_uid."
  fi
}

run_hardware_tests() {
  local test_classes output test_exit=0
  test_classes="org.openmultitrack.app.UsbAudioRecordingInstrumentedTest,org.openmultitrack.app.Flow8VirtualSoundcheckInstrumentedTest"

  flow8__log "Running hardware instrumented tests on $OMT_SERIAL..."
  set +e
  output="$(flow8__adb "$OMT_SERIAL" shell am instrument -w -r \
    -e class "$test_classes" \
    org.openmultitrack.test/androidx.test.runner.AndroidJUnitRunner 2>&1)"
  test_exit=$?
  set -e

  printf '%s\n' "$output"

  if [[ "$test_exit" -ne 0 ]] \
    || grep -qE 'FAILURES!!!|Tests run:.*Failures: [1-9]' <<<"$output" \
    || grep -q 'INSTRUMENTATION_FAILED' <<<"$output"; then
    flow8__die "Hardware instrumented tests failed (exit $test_exit)." 1
  fi

  flow8__log "All hardware instrumented tests passed."
}

main() {
  parse_args "$@"
  require_tools

  flow8__log "=== Phase 1/5: Emulator ==="
  ensure_emulator

  flow8__log "=== Phase 2/5: Guest USB setup ==="
  prepare_guest

  flow8__log "=== Phase 3/5: Build ==="
  run_native_tests
  build_apks

  flow8__log "=== Phase 4/5: Install + permissions ==="
  install_apks_and_sync_permissions
  verify_test_ready

  if [[ "$OMT_SKIP_TESTS" == true ]]; then
    flow8__log "=== Done (--skip-tests): emulator $OMT_SERIAL is ready ==="
    exit 0
  fi

  flow8__log "=== Phase 5/5: Hardware tests ==="
  run_hardware_tests
  flow8__log "=== Success: Flow 8 hardware validation complete on $OMT_SERIAL ==="
}

main "$@"
