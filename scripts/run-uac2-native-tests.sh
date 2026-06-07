#!/usr/bin/env bash
# Host-side UAC2 descriptor parser tests (no Android device required).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${ROOT}/audio-engine/build/host-uac2-test"

cmake -S "${ROOT}/audio-engine/src/test/cpp" -B "${BUILD_DIR}" -DCMAKE_BUILD_TYPE=Debug
cmake --build "${BUILD_DIR}" -j"$(nproc 2>/dev/null || echo 4)"
"${BUILD_DIR}/uac2_descriptor_test" "${ROOT}/audio-engine/src/test/resources/uac2/flow8_recording_mode.bin"
