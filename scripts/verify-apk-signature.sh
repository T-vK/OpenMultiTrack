#!/usr/bin/env bash
# Verify an APK is signed with the repo's pinned debug certificate.
set -euo pipefail
apk="${1:?APK path required}"
root="$(cd "$(dirname "$0")/.." && pwd)"
expected="$(tr -d '[:space:]' < "${root}/keystore/EXPECTED_SIGNER.txt")"

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
apksigner="${sdk}/build-tools/35.0.0/apksigner"
if [[ ! -x "$apksigner" ]]; then
  apksigner="$(command -v apksigner || true)"
fi
if [[ -z "$apksigner" || ! -x "$apksigner" ]]; then
  echo "apksigner not found; set ANDROID_HOME" >&2
  exit 1
fi

actual="$("$apksigner" verify --print-certs "$apk" | awk '/Signer #1 certificate SHA-256 digest:/{print $6}')"
if [[ -z "$actual" ]]; then
  echo "Could not read APK certificate from $apk" >&2
  exit 1
fi

if [[ "$actual" != "$expected" ]]; then
  echo "APK signer mismatch for $apk" >&2
  echo "  expected: $expected" >&2
  echo "  actual:   $actual" >&2
  exit 1
fi

echo "OK signer=$actual"
