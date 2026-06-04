#!/usr/bin/env bash
set -euo pipefail
version_name="${1:?VERSION_NAME required}"
root="$(cd "$(dirname "$0")/.." && pwd)"
version_code="$("${root}/scripts/semver-to-version-code.sh" "$version_name")"
cat > "${root}/gradle/version.properties" <<EOF
VERSION_NAME=${version_name}
VERSION_CODE=${version_code}
EOF
echo "Wrote VERSION_NAME=${version_name} VERSION_CODE=${version_code}"
