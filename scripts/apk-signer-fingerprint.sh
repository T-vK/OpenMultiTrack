#!/usr/bin/env bash
# Print the SHA-256 certificate fingerprint used by apksigner / F-Droid AllowedAPKSigningKeys.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
keystore="${root}/keystore/debug.keystore"
alias_name="openmultitrack-debug"
storepass="openmultitrack"

keytool -exportcert -alias "$alias_name" -keystore "$keystore" -storepass "$storepass" \
  | openssl dgst -sha256 \
  | awk '{print $2}'
