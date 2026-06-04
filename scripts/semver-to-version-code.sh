#!/usr/bin/env bash
# Maps semver MAJOR.MINOR.PATCH to monotonic Android versionCode.
set -euo pipefail
version="${1:?semver required}"
major=$(echo "$version" | cut -d. -f1 | sed 's/[^0-9]//g')
minor=$(echo "$version" | cut -d. -f2 | sed 's/[^0-9]//g')
patch=$(echo "$version" | cut -d. -f3 | sed 's/[^0-9]//g')
major=${major:-0}
minor=${minor:-0}
patch=${patch:-0}
# Max safe range for Play/F-Droid style monotonic codes
echo $((major * 10000 + minor * 100 + patch))
