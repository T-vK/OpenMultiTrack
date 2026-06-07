#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
pass="${FDROID_KEYSTORE_PASS:-openmultitrack-ci}"
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
cat > "${root}/fdroid/config.yml" <<EOF
repo_url: https://T-vK.github.io/OpenMultiTrack/fdroid/repo
repo_name: OpenMultiTrack
repo_description: |
  OpenMultiTrack debug builds — FOSS multitrack recorder for Behringer USB mixers.
archive_older: 5
keystore: keystore.p12
repo_keyalias: fdroidrepo
keystorepass: ${pass}
keypass: ${pass}
EOF
if [[ -n "$sdk" && -d "$sdk" ]]; then
  echo "sdk_path: ${sdk}" >> "${root}/fdroid/config.yml"
fi
