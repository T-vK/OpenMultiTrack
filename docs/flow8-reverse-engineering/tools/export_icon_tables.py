#!/usr/bin/env python3
"""Print markdown icon reference tables for doc 06 (stdout)."""

from __future__ import annotations

import sys

from flow8_icon_decode import (
    FLOW_UI_LABELS,
    INPUT_TYPE_CONDENSOR_MIC,
    INPUT_TYPE_DYNAMIC_MIC,
    INPUT_TYPE_GUITAR_OR_BASS,
    INPUT_TYPE_GUITAR_PAGE,
    INPUT_TYPE_LINE_INSTRUMENT,
    INPUT_TYPE_PLAYBACK,
    PRESET_TO_MS_ICON,
)
from mixing_station_icons import ICON_EMOJI, ICON_LABELS


INPUT_TYPE_NAMES = {
    INPUT_TYPE_DYNAMIC_MIC: "Dynamic mic",
    INPUT_TYPE_CONDENSOR_MIC: "Condenser mic",
    INPUT_TYPE_GUITAR_OR_BASS: "Guitar / bass",
    INPUT_TYPE_LINE_INSTRUMENT: "Line instrument",
    INPUT_TYPE_GUITAR_PAGE: "Guitar page (extended)",
    INPUT_TYPE_PLAYBACK: "Playback / source",
}

# Drawable counts observed in Flowmix_v1.9.apk (`input_icon_{type}{preset:03d}` dex strings).
PRESET_COUNTS = {
    INPUT_TYPE_DYNAMIC_MIC: 16,
    INPUT_TYPE_CONDENSOR_MIC: 11,
    INPUT_TYPE_GUITAR_OR_BASS: 18,
    INPUT_TYPE_LINE_INSTRUMENT: 18,
    INPUT_TYPE_GUITAR_PAGE: 8,
    INPUT_TYPE_PLAYBACK: 12,
}


def flow_drawable(input_type: int, preset: int) -> str:
    return f"input_icon_{input_type * 100 + preset:03d}"


def ms_table() -> None:
    print("## Mixing Station scribble icon IDs (1–74)\n")
    print("Resolved icon values on the wire and in `getChannelIconId` use this")
    print("X32 / X-Air / Mixing Station numbering. Flow Mix drawables map to the same ids.\n")
    print("| MS ID | Label | Emoji |")
    print("| ----- | ----- | ----- |")
    for icon_id in range(1, 75):
        label = ICON_LABELS[icon_id] or "(blank)"
        emoji = ICON_EMOJI[icon_id] or ""
        print(f"| {icon_id} | `{label}` | {emoji} |")
    print()


def flow_input_types() -> None:
    print("## FLOW input types\n")
    print("JNI constants in `libcom_musicgroup_xairbt.so` (`InputType*` symbols).")
    print("The icon picker uses drawable names `input_icon_{type}{preset:03d}`.\n")
    print("| Type | JNI / native | Flow Mix category | Preset slots (APK) |")
    print("| ---- | ------------ | ----------------- | ------------------ |")
    for type_id, name in INPUT_TYPE_NAMES.items():
        slots = PRESET_COUNTS.get(type_id, "?")
        first = flow_drawable(type_id, 0)
        last = flow_drawable(type_id, slots - 1)
        print(f"| {type_id} | `InputType` = {type_id} | {name} | `{first}` … `{last}` ({slots}) |")
    print()


def validated_preset_map() -> None:
    print("## Hardware-validated `(input_type, preset)` → icon mapping\n")
    print("Firmware **v11749**, capture 2026-06-08. Other combinations must be looked up")
    print("via `getInputChannelPresetIconIdAtIndex` in the native library.\n")
    print("| Input type | Preset | Flow drawable | Flow UI label | MS ID | MS label |")
    print("| ---------- | ------ | ------------- | ------------- | ----- | -------- |")
    rows = sorted(PRESET_TO_MS_ICON.keys())
    seen = set()
    for key in rows:
        if key in seen:
            continue
        seen.add(key)
        input_type, preset = key
        ms_id = PRESET_TO_MS_ICON[key]
        flow_label = FLOW_UI_LABELS.get(key, "")
        drawable = flow_drawable(input_type, preset)
        ms_label = ICON_LABELS[ms_id]
        type_name = INPUT_TYPE_NAMES.get(input_type, str(input_type))
        print(
            f"| {input_type} ({type_name}) | {preset} | `{drawable}` | {flow_label} | {ms_id} | `{ms_label}` |"
        )
    print()


def main() -> None:
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    if which in ("all", "ms"):
        ms_table()
    if which in ("all", "types"):
        flow_input_types()
    if which in ("all", "presets"):
        validated_preset_map()


if __name__ == "__main__":
    main()
