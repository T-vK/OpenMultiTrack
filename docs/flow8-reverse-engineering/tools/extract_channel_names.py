#!/usr/bin/env python3
"""Extract Behringer FLOW 8 channel names from a captured SysEx state dump.

Accepts either:
  * a raw .syx binary (as written by `amidi -r`), or
  * a hex+ASCII text dump (the "OFFSET: hh hh .. | ascii" format).

Usage:
    python3 extract_channel_names.py dump.syx [more.syx ...]

No third-party dependencies. Offsets/algorithm match the reference parser; see
../02-sysex-dump-format.md and ../04-channel-name-extraction.md.
"""
import sys

SYSEX_START = 0xF0
SYSEX_END = 0xF7
BEHRINGER_ID = bytes([0x00, 0x20, 0x32])
FLOW8_MODEL = 0x21

NAMES_START = 0x0554
NAMES_STRIDE = 0x1E
NAME_SCAN_LEN = 14
CHANNEL_COUNT = 7


def load_bytes(path):
    """Return the raw byte payload, auto-detecting binary vs. hex-dump text."""
    with open(path, "rb") as f:
        raw = f.read()
    if raw[:1] == bytes([SYSEX_START]):
        return raw  # raw .syx binary
    # Try to parse as a hex+ASCII text dump.
    try:
        text = raw.decode("utf-8", "replace")
    except Exception:
        return raw
    out = bytearray()
    for line in text.splitlines():
        hex_part = line.split("|", 1)[0]
        if ":" in hex_part:
            hex_part = hex_part.split(":", 1)[1]
        for tok in hex_part.split():
            if len(tok) == 2:
                try:
                    out.append(int(tok, 16))
                except ValueError:
                    pass
    return bytes(out) if out else raw


def extract_sysex(data):
    """Slice the first complete F0..F7 message; offsets are relative to F0."""
    try:
        start = data.index(SYSEX_START)
    except ValueError:
        return data  # no framing; assume offsets already absolute
    end = data.find(SYSEX_END, start)
    return data[start:end + 1] if end != -1 else data[start:]


def validate(data):
    if len(data) < 100:
        return f"dump too small ({len(data)} bytes)"
    if data[0] != SYSEX_START:
        return f"missing SysEx start (got 0x{data[0]:02X})"
    if data[-1] != SYSEX_END:
        return f"missing SysEx end (got 0x{data[-1]:02X})"
    if data[1:4] != BEHRINGER_ID:
        return "wrong manufacturer ID (expected 00 20 32)"
    if data[4] != FLOW8_MODEL:
        return f"wrong model byte (got 0x{data[4]:02X}, expected 0x21)"
    return None


def restore_byte(data, pos):
    """Reconstruct an 8-bit value from the 7-byte rotating-MSB packing."""
    group_pos = (pos + 2) % 7
    if group_pos == 0:
        return None  # MSB carrier byte
    if pos < group_pos:
        return data[pos]
    msb_off = pos - group_pos
    if msb_off >= len(data):
        return data[pos]
    b = data[pos]
    if data[msb_off] & (1 << (group_pos - 1)):
        b |= 0x80
    return b


def decode_name(data, start):
    end = min(start + NAME_SCAN_LEN, len(data))
    restored = [b for i in range(start, end)
                if (b := restore_byte(data, i)) is not None]
    s = next((k for k, b in enumerate(restored) if b >= 0x20), len(restored))
    rest = restored[s:]
    e = next((k for k, b in enumerate(rest) if b == 0x00), len(rest))
    return bytes(rest[:e]).decode("utf-8", "replace")


def process(path):
    print(f"=== {path} ===")
    data = extract_sysex(load_bytes(path))
    print(f"  SysEx length: {len(data)} bytes")
    err = validate(data)
    if err:
        print(f"  WARNING: {err} (attempting decode anyway)")
    need = NAMES_START + CHANNEL_COUNT * NAMES_STRIDE
    if len(data) < need:
        print(f"  ERROR: dump too short for name region (need >= {need}).")
        return
    print(f"  {'Ch':>3}  {'Offset':>7}  Name")
    print("  " + "-" * 40)
    for i in range(CHANNEL_COUNT):
        off = NAMES_START + i * NAMES_STRIDE
        label = f"Ch{i + 1}" if i < 6 else "Ch7/USB-BT"
        print(f"  {label:>3}  0x{off:05X}  \"{decode_name(data, off)}\"")
    print()


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    for path in sys.argv[1:]:
        try:
            process(path)
        except OSError as e:
            print(f"=== {path} ===\n  Error: {e}\n")


if __name__ == "__main__":
    main()
