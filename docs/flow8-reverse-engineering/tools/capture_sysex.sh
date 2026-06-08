#!/usr/bin/env bash
# Capture a Behringer FLOW 8 SysEx state dump from USB MIDI using ALSA `amidi`.
#
# Usage:
#   ./capture_sysex.sh [output_file] [seconds]
#
# Defaults: output_file=dump.syx, seconds=6
#
# While this is recording, trigger the dump either from the official FLOW Mixer
# app (open the mixer view) or by sending BLE packet 4B 01 4C to the device's
# GATT characteristic (see ../03-bluetooth-le-protocol.md and
# ../04-channel-name-extraction.md). The captured file begins with 0xF0 and ends
# with 0xF7 and can be decoded with extract_channel_names.py.
set -euo pipefail

OUT="${1:-dump.syx}"
SECS="${2:-6}"

echo "== FLOW 8 SysEx capture =="

# Locate the FLOW 8 raw-MIDI port.
PORT="$(amidi -l 2>/dev/null | awk '/FLOW 8/ {print $2; exit}')"
if [ -z "${PORT}" ]; then
  echo "ERROR: No 'FLOW 8' MIDI port found in 'amidi -l'." >&2
  echo "       Make sure the mixer is connected via USB and enumerated:" >&2
  echo "         lsusb | grep -i 1397:050c" >&2
  echo "         amidi -l" >&2
  exit 1
fi
echo "Using MIDI port: ${PORT}"

echo "Recording incoming SysEx for ${SECS}s to '${OUT}' ..."
echo ">>> TRIGGER THE DUMP NOW (app mixer view, or BLE 4B 01 4C) <<<"

# -r records raw incoming bytes (incl. F0..F7); -t sets a timeout in seconds.
amidi -p "${PORT}" -r "${OUT}" -t "${SECS}"

if [ ! -s "${OUT}" ]; then
  echo "WARNING: '${OUT}' is empty — no SysEx captured." >&2
  echo "         Re-run and ensure the dump is triggered while recording." >&2
  exit 2
fi

BYTES="$(wc -c < "${OUT}")"
FIRST="$(head -c1 "${OUT}" | od -An -tx1 | tr -d ' ')"
echo "Captured ${BYTES} bytes (first byte: 0x${FIRST})."
if [ "${FIRST}" != "f0" ]; then
  echo "NOTE: file does not start with 0xF0; it may contain non-SysEx MIDI too." >&2
fi
echo "Decode with: python3 $(dirname "$0")/extract_channel_names.py '${OUT}'"
