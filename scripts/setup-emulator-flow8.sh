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

"$ROOT/scripts/grant-emulator-usb-host.sh" "$SERIAL" || true
"$ROOT/scripts/grant-usb-permission.sh" "$SERIAL"
