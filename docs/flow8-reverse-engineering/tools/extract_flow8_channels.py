#!/usr/bin/env python3
"""Decode FLOW 8 channel names and icon IDs from a state dump.

The mixer always exposes six names (Ch1–4, Ch5+6, Ch7+8). USB 9–10 are Main L/R.

Usage:
    python3 extract_flow8_channels.py dump.bin
    python3 extract_flow8_channels.py dump.bin --icon-config icon_config.bin

See ../04-channel-name-extraction.md and ../06-channel-icons-and-stereo-link.md
(icon tables in appendices A and C).
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
    extract_sysex,
    load_bytes,
    validate,
)
from flow8_icon_decode import decode_icon_meta, parse_icon_config
from mixing_station_icons import SPEAKER_LEFT, SPEAKER_RIGHT, format_icon

SYSEX_NAME_REGION_MIN_SIZE = 0x0600
SYSEX_START = 0xF0

SYSEX_NAME_OFFSETS = [NAMES_START + i * NAMES_STRIDE for i in range(CHANNEL_COUNT)]


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
    icon_meta = (
        decode_icon_meta(icon_config, buf, CHANNEL_COUNT)
        if icon_config
        else [None] * CHANNEL_COUNT
    )
    names = decode_names(buf)
    entries = []
    for i in range(CHANNEL_COUNT):
        meta = icon_meta[i] if i < len(icon_meta) else {}
        icon_id = meta.get("icon_id") if meta else None
        flow_label = meta.get("flow_label") if meta else None
        entries.append(
            {
                "index": i,
                "label": NAME_LABELS[i],
                "name": names[i] if i < len(names) else "",
                "icon_id": icon_id,
                "flow_label": flow_label,
                "input_type": meta.get("input_type") if meta else None,
                "preset": meta.get("preset") if meta else None,
                "icon_display": format_icon(icon_id, flow_label),
            }
        )
    return entries


def scribble_usb_rows(entries: list[dict]) -> list[dict]:
    """Ten USB capture channels with names and resolved icon ids."""
    rows: list[dict] = []

    def row(usb: int, source: str, name: str, entry: dict) -> dict:
        return {
            "usb": usb,
            "source": source,
            "name": name,
            "icon_id": entry["icon_id"],
            "icon_display": entry["icon_display"],
        }

    for i in range(4):
        name = entries[i]["name"] or "(empty)"
        rows.append(row(i + 1, NAME_LABELS[i], name, entries[i]))

    name56 = entries[4]["name"] or "(empty)"
    rows.append(row(5, "Ch5+6", name56, entries[4]))
    rows.append(row(6, "Ch5+6", name56, entries[4]))

    name78 = entries[5]["name"] or "(empty)"
    rows.append(row(7, "Ch7+8", name78, entries[5]))
    rows.append(row(8, "Ch7+8", name78, entries[5]))

    rows.append(
        row(
            9,
            "Main L",
            MAIN_L_NAME,
            {"icon_id": SPEAKER_LEFT, "icon_display": format_icon(SPEAKER_LEFT, "Main L")},
        )
    )
    rows.append(
        row(
            10,
            "Main R",
            MAIN_R_NAME,
            {"icon_id": SPEAKER_RIGHT, "icon_display": format_icon(SPEAKER_RIGHT, "Main R")},
        )
    )
    return rows


def print_scribble_report(entries: list[dict]) -> None:
    print()
    print("  Mixer strips (6 names from FLOW 8):")
    print(f"  {'#':<3}  {'Strip':<8}  {'Icon':<28}  Name")
    print("  " + "-" * 72)
    for e in entries:
        name = e["name"] or "(empty)"
        print(f"  {e['index'] + 1:<3}  {e['label']:<8}  {e['icon_display']:<28}  \"{name}\"")

    print()
    print("  USB scribble strip (10 capture channels):")
    print(f"  {'USB':>4}  {'Source':<8}  {'Icon':<28}  Name")
    print("  " + "-" * 72)
    for r in scribble_usb_rows(entries):
        print(f"  {r['usb']:>4}  {r['source']:<8}  {r['icon_display']:<28}  \"{r['name']}\"")
    print()


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

    print_scribble_report(entries)
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
