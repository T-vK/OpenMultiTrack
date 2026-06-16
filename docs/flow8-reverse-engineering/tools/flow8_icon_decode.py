"""Decode FLOW 8 BLE icon presets to Mixing Station icon ids.

Flow Mix stores icons as (input_type, preset_index) pairs:
  - ParamQuery 0x80: 12 groups × 4 bytes; byte0 is 0x03 (typed), byte1 is preset index.
  - MixerState: per-name input type in the BLE compact record header.

Drawable assets use input_icon_{type*100+preset:03d} (e.g. 004, 202, 304, 507).
See doc 06.
"""

from __future__ import annotations

from mixing_station_icons import (
    ACOUSTIC_GUITAR,
    ELECTRIC_BASS,
    HANDHELD_MIC,
    ICON_MAX,
    ICON_MIN,
)

# JNI InputType* constants (libcom_musicgroup_xairbt.so).
INPUT_TYPE_DYNAMIC_MIC = 0
INPUT_TYPE_CONDENSOR_MIC = 1
INPUT_TYPE_GUITAR_OR_BASS = 2
INPUT_TYPE_LINE_INSTRUMENT = 3
# Additional categories used by later FLOW firmware / icon picker pages.
INPUT_TYPE_GUITAR_PAGE = 4
INPUT_TYPE_PLAYBACK = 5

ICON_MARKER_TYPED = 0x03
ICON_MARKER_PLAIN = 0x00
NAME_LEN_MIN = 2
NAME_LEN_MAX = 18
RECORD_MAGIC = 0x6A

# Per-type preset tables — mirrors Flow8IconPresets.kt / flow8_icon_mapping.json.
PRESET_ICON_TABLES: list[list[int]] = [
    [1, 0, 61, 0, 0, 0, 0, 0, 0, 0, 0, 72, 73, 70, 74],
    [47, 48, 49, 52, 0, 51, 50, 53, 43, 42, 41],
    [0, 0, 0, 10, 4, 5, 9, 11, 0, 0, 6, 7, 8, 13, 14, 12, 15, 16],
    [22, 0, 0, 0, 23, 20, 21, 0, 39, 0, 0, 38, 37, 36, 35, 29, 0, 0],
    [0, 0, 31, 32, 33, 0, 34, 30],
    [25, 26, 24, 0, 66, 0, 0, 0, 67, 63, 64, 65],
]

# Hardware-validated overrides (firmware v11749, 2026-06-08 capture).
PRESET_TO_MS_ICON: dict[tuple[int, int], int] = {
    (INPUT_TYPE_DYNAMIC_MIC, 4): HANDHELD_MIC,
    (INPUT_TYPE_DYNAMIC_MIC, 7): HANDHELD_MIC,
    (INPUT_TYPE_LINE_INSTRUMENT, 4): ACOUSTIC_GUITAR,
    (INPUT_TYPE_GUITAR_PAGE, 2): 31,
}

PLAIN_PRESET_TO_MS_ICON: dict[int, int] = {
    0x02: ELECTRIC_BASS,
    0x04: VIOLIN,
}

# Flow Mix UI labels (what the official app shows in the icon picker).
FLOW_UI_LABELS: dict[tuple[int, int], str] = {
    (INPUT_TYPE_DYNAMIC_MIC, 4): "Wired Mic",
    (INPUT_TYPE_DYNAMIC_MIC, 7): "Wired Mic",
    (INPUT_TYPE_GUITAR_OR_BASS, 2): "Crash",
    (INPUT_TYPE_LINE_INSTRUMENT, 4): "Acoustic Guitar",
    (INPUT_TYPE_GUITAR_PAGE, 2): "Synthesizer 1",
    (INPUT_TYPE_PLAYBACK, 7): "Speaker (Wall-Mounted)",
}


def read_length_prefixed(buf: bytes, offset: int) -> str | None:
    if offset >= len(buf):
        return None
    length = buf[offset]
    if not (NAME_LEN_MIN <= length <= NAME_LEN_MAX) or offset + 1 + length > len(buf):
        return None
    raw = buf[offset + 1 : offset + 1 + length]
    if not all(0x20 <= b <= 0x7E for b in raw):
        return None
    return raw.decode("ascii")


def scan_name_offsets(buf: bytes, max_names: int = 6) -> list[int]:
    offsets: list[int] = []
    i = 0
    while i < len(buf) and len(offsets) < max_names:
        name = read_length_prefixed(buf, i)
        if name is None:
            i += 1
            continue
        offsets.append(i)
        i += 1 + buf[i]
    return offsets


def decode_input_type(buf: bytes, name_offset: int) -> int:
    """Read input type from the BLE compact name record header."""
    # Ch1–3 use […][0x6a][len][name]; later strips use [type][…][len][name].
    if name_offset >= 1 and buf[name_offset - 1] == RECORD_MAGIC:
        return INPUT_TYPE_DYNAMIC_MIC
    if name_offset >= 3:
        value = buf[name_offset - 3]
        if value <= INPUT_TYPE_PLAYBACK:
            return value
    return INPUT_TYPE_DYNAMIC_MIC


def resolve_preset_icon(input_type: int, preset: int) -> int | None:
    key = (input_type, preset)
    if key in PRESET_TO_MS_ICON:
        return PRESET_TO_MS_ICON[key]
    if 0 <= input_type < len(PRESET_ICON_TABLES):
        table = PRESET_ICON_TABLES[input_type]
        if 0 <= preset < len(table):
            icon = table[preset]
            if ICON_MIN <= icon <= ICON_MAX:
                return icon
    return None


def flow_ui_label(input_type: int, preset: int, ms_icon: int | None) -> str | None:
    return FLOW_UI_LABELS.get((input_type, preset))


def parse_icon_config(
    payload: bytes,
    mixer_state: bytes | None = None,
    max_strips: int = 6,
) -> list[int | None]:
    name_offsets = scan_name_offsets(mixer_state) if mixer_state else []
    icons: list[int | None] = []
    groups = len(payload) // 4
    for index in range(min(max_strips, groups)):
        base = index * 4
        marker = payload[base]
        preset = payload[base + 1] if base + 1 < len(payload) else 0
        icons.append(decode_icon_group(index, marker, preset, mixer_state, name_offsets))
    while len(icons) < max_strips:
        icons.append(None)
    return icons


def decode_icon_group(
    strip_index: int,
    marker: int,
    preset: int,
    mixer_state: bytes | None,
    name_offsets: list[int],
) -> int | None:
    if marker == ICON_MARKER_TYPED:
        input_type = INPUT_TYPE_DYNAMIC_MIC
        if mixer_state and strip_index < len(name_offsets):
            input_type = decode_input_type(mixer_state, name_offsets[strip_index])
        return resolve_preset_icon(input_type, preset)

    if marker == ICON_MARKER_PLAIN:
        if preset in PLAIN_PRESET_TO_MS_ICON:
            return PLAIN_PRESET_TO_MS_ICON[preset]
        return preset if ICON_MIN <= preset <= ICON_MAX else None

    if ICON_MIN <= marker <= ICON_MAX:
        return marker
    if ICON_MIN <= preset <= ICON_MAX:
        return preset
    return None


def decode_icon_meta(
    payload: bytes,
    mixer_state: bytes | None = None,
    max_strips: int = 6,
) -> list[dict]:
    """Per-strip icon decode with input type, preset, MS id, and Flow UI label."""
    name_offsets = scan_name_offsets(mixer_state) if mixer_state else []
    meta: list[dict] = []
    groups = len(payload) // 4
    for index in range(min(max_strips, groups)):
        base = index * 4
        marker = payload[base]
        preset = payload[base + 1] if base + 1 < len(payload) else 0
        input_type = INPUT_TYPE_DYNAMIC_MIC
        if mixer_state and index < len(name_offsets):
            input_type = decode_input_type(mixer_state, name_offsets[index])
        ms_icon = decode_icon_group(index, marker, preset, mixer_state, name_offsets)
        meta.append(
            {
                "input_type": input_type,
                "preset": preset,
                "marker": marker,
                "icon_id": ms_icon,
                "flow_label": flow_ui_label(input_type, preset, ms_icon),
            }
        )
    while len(meta) < max_strips:
        meta.append(
            {
                "input_type": None,
                "preset": None,
                "marker": None,
                "icon_id": None,
                "flow_label": None,
            }
        )
    return meta
