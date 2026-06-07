#!/usr/bin/env bash
# One-shot emulator prep: USB host feature (if needed) + Flow 8 permission/descriptor cache.
#
# Usage:
#   ./scripts/setup-emulator-flow8.sh [adb-serial]

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERIAL="${1:-}"

chmod +x "$ROOT/scripts/grant-emulator-usb-host.sh"
chmod +x "$ROOT/scripts/grant-usb-permission.sh"

if [[ "${1:-}" == "-h" ]] || [[ "${1:-}" == "--help" ]]; then
  sed -n '2,6p' "$0"
  echo ""
  echo "Requires emulator started with Flow 8 USB passthrough:"
  echo "  ./scripts/run-emulator-with-flow8.sh"
  exit 0
fi

"$ROOT/scripts/grant-emulator-usb-host.sh" "$SERIAL" || true
"$ROOT/scripts/grant-usb-permission.sh" "$SERIAL"
