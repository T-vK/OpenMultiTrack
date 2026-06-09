#!/usr/bin/env bash
# Stage app and repository icons for fdroid update.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"

repo_icon="${root}/fdroid/repo-icons/icon.png"
app_icon="${root}/fastlane/metadata/android/en-US/images/icon.png"
metadata_icon_dir="${root}/fdroid/metadata/org.openmultitrack/en-US/images"

if [[ ! -f "$repo_icon" ]]; then
  echo "Missing repo icon: $repo_icon (run scripts/generate-branding-icons.py)" >&2
  exit 1
fi
if [[ ! -f "$app_icon" ]]; then
  echo "Missing app icon: $app_icon (run scripts/generate-branding-icons.py)" >&2
  exit 1
fi

mkdir -p "${root}/fdroid/repo/icons" "${metadata_icon_dir}"
cp "$repo_icon" "${root}/fdroid/repo/icons/icon.png"
cp "$app_icon" "${metadata_icon_dir}/icon.png"

if [[ -d "${root}/fdroid/archive" ]]; then
  mkdir -p "${root}/fdroid/archive/icons"
  cp "$repo_icon" "${root}/fdroid/archive/icons/icon.png"
fi

echo "Staged F-Droid icons:"
echo "  repo/icons/icon.png"
echo "  metadata/org.openmultitrack/en-US/images/icon.png"
