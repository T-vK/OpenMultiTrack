#!/usr/bin/env bash
# Fail CI if index-v1.json advertises a versionCode with no matching APK entry.
set -euo pipefail
index="${1:?path to index-v1.json}"
python3 - "$index" <<'PY'
import json
import sys

path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    data = json.load(f)

for app in data.get("apps", []):
    package = app["packageName"]
    suggested = str(app.get("suggestedVersionCode", ""))
    versions = {str(pkg["versionCode"]) for pkg in data.get("packages", {}).get(package, [])}
    if suggested not in versions:
        raise SystemExit(
            f"{package}: suggestedVersionCode={suggested} has no matching package "
            f"(available: {sorted(versions)})"
        )
    print(f"OK {package}: suggestedVersionCode={suggested}")
PY
