#!/usr/bin/env bash
# Assembles a static F-Droid-compatible repo under site/fdroid/repo for GitHub Pages.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
apk="${1:?path to APK}"
site_repo="${root}/site/fdroid/repo"
mkdir -p "$site_repo"
cp -f "$apk" "$site_repo/"

if ! command -v fdroid >/dev/null 2>&1; then
  echo "fdroid CLI not found; install fdroidserver (pip) in CI." >&2
  exit 1
fi

cd "${root}/fdroid"
export FDROID_UPDATE_AUTOKEY=1
fdroid update --create-metadata --pretty
fdroid update --pretty

rsync -a --delete "${root}/fdroid/repo/" "$site_repo/"
