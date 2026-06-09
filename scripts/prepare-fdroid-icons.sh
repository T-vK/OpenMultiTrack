#!/usr/bin/env bash
# Stage app and repository icons for fdroid update.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
version_name="${1:-}"

repo_icon="${root}/fdroid/repo-icons/icon.png"
app_icon="${root}/fastlane/metadata/android/en-US/images/icon.png"
metadata_icon_dir="${root}/fdroid/metadata/org.openmultitrack/en-US/images"
repo_dir="${root}/fdroid/repo"
pkg_id="org.openmultitrack"
pkg_locale_dir="${repo_dir}/${pkg_id}/en-US"

if [[ ! -f "$repo_icon" ]]; then
  echo "Missing repo icon: $repo_icon (run scripts/generate-branding-icons.py)" >&2
  exit 1
fi
if [[ ! -f "$app_icon" ]]; then
  echo "Missing app icon: $app_icon (run scripts/generate-branding-icons.py)" >&2
  exit 1
fi

mkdir -p "${repo_dir}/icons" "${metadata_icon_dir}" "${pkg_locale_dir}"
cp "$repo_icon" "${repo_dir}/icons/icon.png"
cp "$app_icon" "${metadata_icon_dir}/icon.png"
cp "$app_icon" "${pkg_locale_dir}/icon.png"

if [[ -n "$version_name" ]]; then
  version_code="$("${root}/scripts/semver-to-version-code.sh" "$version_name")"
  cp "$app_icon" "${repo_dir}/icons/${pkg_id}.${version_code}.png"
fi

if [[ -d "${root}/fdroid/archive" ]]; then
  mkdir -p "${root}/fdroid/archive/icons"
  cp "$repo_icon" "${root}/fdroid/archive/icons/icon.png"
fi

echo "Staged F-Droid icons:"
echo "  repo/icons/icon.png"
echo "  repo/${pkg_id}/en-US/icon.png"
echo "  metadata/org.openmultitrack/en-US/images/icon.png"
if [[ -n "$version_name" ]]; then
  echo "  repo/icons/${pkg_id}.${version_code}.png"
fi
