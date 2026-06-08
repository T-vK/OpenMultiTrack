#!/usr/bin/env python3
"""Decode FLOW 8 channel names and icon IDs from a state dump.

The mixer always exposes six names (Ch1–4, Ch5+6, Ch7+8). USB 9–10 are Main L/R.

Usage:
    python3 extract_flow8_channels.py dump.bin
    python3 extract_flow8_channels.py dump.bin --icon-config icon_config.bin

See ../04-channel-name-extraction.md and ../06-channel-icons-and-stereo-link.md.
"""
from __future__ import annotations

import argparse
import sys

from extract_channel_names import (
    CHANNEL_COUNT,
    MAIN_L_NAME,
    MAIN_R_NAME,
    NAME_LABELS,
    NAMES_START,
    NAMES_STRIDE,
    decode_name,
    extract_sysex,
    load_bytes,
    validate,
)

ICON_MIN = 1
ICON_MAX = 74
SYSEX_NAME_REGION_MIN_SIZE = 0x0600
SYSEX_START = 0xF0

SYSEX_NAME_OFFSETS = [NAMES_START + i * NAMES_STRIDE for i in range(CHANNEL_COUNT)]


def parse_icon_config(payload: bytes) -> list[int | None]:
    icons: list[int | None] = []
    for i in range(min(CHANNEL_COUNT, len(payload) // 4)):
        base = i * 4
        primary = payload[base]
        fallback = payload[base + 1] if base + 1 < len(payload) else 0
        if ICON_MIN <= primary <= ICON_MAX:
            icons.append(primary)
        elif ICON_MIN <= fallback <= ICON_MAX:
            icons.append(fallback)
        else:
            icons.append(None)
    while len(icons) < CHANNEL_COUNT:
        icons.append(None)
    return icons


def read_length_prefixed(buf: bytes, offset: int) -> str | None:
    if offset >= len(buf):
        return None
    length = buf[offset]
    if not (2 <= length <= 18) or offset + 1 + length > len(buf):
        return None
    raw = buf[offset + 1 : offset + 1 + length]
    if not all(0x20 <= b <= 0x7E for b in raw):
        return None
    return raw.decode("ascii")


def scan_names(buf: bytes) -> list[str]:
    names: list[str] = []
    i = 0
    while i < len(buf) and len(names) < CHANNEL_COUNT:
        name = read_length_prefixed(buf, i)
        if name is None:
            i += 1
            continue
        names.append(name)
        i += 1 + buf[i]
    return names


def uses_sysex_name_region(buf: bytes) -> bool:
    if len(buf) < SYSEX_NAME_REGION_MIN_SIZE:
        return False
    if buf[:1] == bytes([SYSEX_START]):
        return True
    return read_length_prefixed(buf, NAMES_START) is not None


def decode_names(buf: bytes) -> list[str]:
    if uses_sysex_name_region(buf):
        fixed = [read_length_prefixed(buf, off) or "" for off in SYSEX_NAME_OFFSETS]
        if sum(1 for n in fixed if n) >= CHANNEL_COUNT:
            return fixed[:CHANNEL_COUNT]
    return scan_names(buf)


def decode_entries(buf: bytes, icon_config: bytes | None) -> list[dict]:
    icons = parse_icon_config(icon_config) if icon_config else [None] * CHANNEL_COUNT
    names = decode_names(buf)
    entries = []
    for i in range(CHANNEL_COUNT):
        entries.append(
            {
                "index": i,
                "label": NAME_LABELS[i],
                "name": names[i] if i < len(names) else "",
                "icon_id": icons[i] if i < len(icons) else None,
            }
        )
    return entries


def usb_mapping(entries: list[dict]) -> list[tuple[int, str, str]]:
    rows: list[tuple[int, str, str]] = []
    for i in range(4):
        name = entries[i]["name"] or "(empty)"
        rows.append((i + 1, NAME_LABELS[i], name))
    name56 = entries[4]["name"] or "(empty)"
    rows.append((5, "Ch5+6", name56))
    rows.append((6, "Ch5+6", name56))
    name78 = entries[5]["name"] or "(empty)"
    rows.append((7, "Ch7+8", name78))
    rows.append((8, "Ch7+8", name78))
    rows.append((9, "Main L", MAIN_L_NAME))
    rows.append((10, "Main R", MAIN_R_NAME))
    return rows


def process(path: str, icon_config: bytes | None) -> int:
    print(f"=== {path} ===")
    data = extract_sysex(load_bytes(path))
    fmt = "USB SysEx" if uses_sysex_name_region(data) else "BLE compact"
    print(f"  Buffer length: {len(data)} bytes ({fmt})")
    err = validate(data)
    if err and fmt == "USB SysEx":
        print(f"  WARNING: {err} (attempting decode anyway)")
    if icon_config:
        print(f"  Icon config: {len(icon_config)} bytes")

    entries = decode_entries(data, icon_config)
    if not any(e["name"] for e in entries):
        print("  ERROR: no names decoded.")
        return 1

    print()
    print(f"  {'#':<3}  {'Strip':<8}  {'Icon':>4}  Name")
    print("  " + "-" * 48)
    for e in entries:
        icon = str(e["icon_id"]) if e["icon_id"] is not None else "—"
        name = e["name"] or "(empty)"
        print(f"  {e['index'] + 1:<3}  {e['label']:<8}  {icon:>4}  \"{name}\"")

    print()
    print("  USB capture channels:")
    print(f"  {'USB':>4}  {'Source':<8}  Name")
    print("  " + "-" * 36)
    for usb, source, name in usb_mapping(entries):
        print(f"  {usb:>4}  {source:<8}  \"{name}\"")
    print()
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dump", help="MixerState or SysEx dump file")
    parser.add_argument("--icon-config", metavar="FILE", help="48-byte 0x80 payload")
    args = parser.parse_args()
    icon_cfg = None
    if args.icon_config:
        with open(args.icon_config, "rb") as fh:
            icon_cfg = fh.read()
    sys.exit(process(args.dump, icon_cfg))


if __name__ == "__main__":
    main()
