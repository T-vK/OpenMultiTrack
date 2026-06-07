#!/usr/bin/env bash
# Sync CurrentVersion / CurrentVersionCode in repo metadata before fdroid update.
set -euo pipefail
version_name="${1:?VERSION_NAME required}"
root="$(cd "$(dirname "$0")/.." && pwd)"
metadata="${root}/fdroid/metadata/org.openmultitrack.yml"
version_code="$("${root}/scripts/semver-to-version-code.sh" "$version_name")"

if [[ ! -f "$metadata" ]]; then
  echo "Missing metadata file: $metadata" >&2
  exit 1
fi

sed -i "s/^CurrentVersion:.*/CurrentVersion: '${version_name}'/" "$metadata"
sed -i "s/^CurrentVersionCode:.*/CurrentVersionCode: ${version_code}/" "$metadata"

echo "Updated F-Droid metadata: CurrentVersion=${version_name} CurrentVersionCode=${version_code}"
