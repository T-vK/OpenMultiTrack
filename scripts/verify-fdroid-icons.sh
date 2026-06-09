#!/usr/bin/env bash
# Fail CI if the published F-Droid repo is missing repository or app icons.
set -euo pipefail
index="${1:?path to index-v1.json}"
root="$(cd "$(dirname "$0")/.." && pwd)"
repo_dir="${root}/fdroid/repo"
pkg_id="org.openmultitrack"

test -f "${repo_dir}/icons/icon.png" || {
  echo "Missing repository icon: ${repo_dir}/icons/icon.png" >&2
  exit 1
}
test -f "${repo_dir}/${pkg_id}/en-US/icon.png" || {
  echo "Missing app icon: ${repo_dir}/${pkg_id}/en-US/icon.png" >&2
  exit 1
}

python3 - "$index" "$pkg_id" <<'PY'
import json
import sys

index_path, package_name = sys.argv[1:3]
with open(index_path, encoding="utf-8") as handle:
    data = json.load(handle)

repo_icon = data.get("repo", {}).get("icon")
if not repo_icon:
    raise SystemExit("repo missing icon field in index-v1.json")

apps = [app for app in data.get("apps", []) if app.get("packageName") == package_name]
if not apps:
    raise SystemExit(f"app {package_name} missing from index-v1.json")

app = apps[0]
icon = app.get("icon")
localized_icon = app.get("localized", {}).get("en-US", {}).get("icon")
if not icon and not localized_icon:
    raise SystemExit(
        f"{package_name}: index-v1.json has no app icon "
        "(expected top-level icon or localized.en-US.icon)"
    )

print(f"OK repo icon={repo_icon}")
print(f"OK {package_name} icon={icon or localized_icon}")
PY
