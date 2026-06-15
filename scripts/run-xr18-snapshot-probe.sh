#!/usr/bin/env bash
# Probe XR18 snapshot names over OSC. See scripts/xr18-snapshot-names.py
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec python3 "$ROOT/scripts/xr18-snapshot-names.py" "$@"
