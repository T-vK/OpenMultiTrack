"""FLOW 8 picker drawable catalog — MS id mapping from manual assignment.

Flow Mix APK icons are named ``input_icon_{key:03d}`` where
``key = input_type * 100 + preset_index``.

Run ``assign_flow8_icons.py`` to build ``flow8_icon_mapping.json``, then
``export_icon_tables.py all`` to refresh the documentation tables.
"""

from __future__ import annotations

import json
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
MAPPING_JSON = TOOLS_DIR / "flow8_icon_mapping.json"
TABLES_JSON = TOOLS_DIR / "flow8_icon_tables.json"

PRESET_COUNTS: dict[int, int] = {
    0: 15,
    1: 11,
    2: 18,
    3: 18,
    4: 8,
    5: 12,
}

INPUT_TYPE_NAMES: dict[int, str] = {
    0: "Dynamic mic",
    1: "Condenser mic",
    2: "Guitar / bass",
    3: "Line instrument",
    4: "Guitar page (extended)",
    5: "Playback / source",
}

# Hardware-validated Flow UI strings (firmware v11749).
FLOW_UI_LABELS: dict[tuple[int, int], str] = {
    (0, 4): "Wired Mic",
    (0, 7): "Wired Mic",
    (2, 2): "Acoustic Guitar",
    (3, 4): "Violine",
    (4, 2): "Acoustic Guitar",
    (5, 7): "Record player",
}

VALIDATED_MS_IDS: dict[tuple[int, int], int] = {
    (0, 4): 50,
    (0, 7): 50,
    (2, 2): 23,
    (3, 4): 39,
    (4, 2): 23,
    (5, 7): 60,
}


def drawable_key(input_type: int, preset: int) -> str:
    return f"input_icon_{input_type * 100 + preset:03d}"


def load_manual_mapping() -> dict[str, int]:
    if not MAPPING_JSON.is_file():
        return {}
    data = json.loads(MAPPING_JSON.read_text(encoding="utf-8"))
    raw = data.get("assignments", data)
    out: dict[str, int] = {}
    for drawable, value in raw.items():
        if isinstance(value, dict):
            out[drawable] = int(value["ms_id"])
        else:
            out[drawable] = int(value)
    return out


def load_extracted_ms_tables() -> dict[tuple[int, int], int]:
    if not TABLES_JSON.is_file():
        return {}
    data = json.loads(TABLES_JSON.read_text(encoding="utf-8"))
    out: dict[tuple[int, int], int] = {}
    for row in data.get("presets", []):
        key = (int(row["input_type"]), int(row["preset"]))
        out[key] = int(row["ms_id"])
    return out


def resolve_ms_id(input_type: int, preset: int) -> int | None:
    drawable = drawable_key(input_type, preset)
    manual = load_manual_mapping()
    if drawable in manual:
        return manual[drawable]
    key = (input_type, preset)
    if key in VALIDATED_MS_IDS:
        return VALIDATED_MS_IDS[key]
    extracted = load_extracted_ms_tables()
    if key in extracted:
        return extracted[key]
    from flow8_icon_decode import resolve_preset_icon

    return resolve_preset_icon(input_type, preset)


def drawable_label(drawable: str, input_type: int | None = None, preset: int | None = None) -> str:
    if input_type is not None and preset is not None:
        flow_ui = FLOW_UI_LABELS.get((input_type, preset))
        if flow_ui:
            return flow_ui
    from mixing_station_display_labels import display_label

    ms_id = None
    if input_type is not None and preset is not None:
        ms_id = resolve_ms_id(input_type, preset)
    else:
        manual = load_manual_mapping()
        ms_id = manual.get(drawable)
    if ms_id is not None:
        name = display_label(ms_id)
        if name:
            return name
    return drawable.replace("input_icon_", "icon ")


def catalog_rows() -> list[dict]:
    rows: list[dict] = []
    for input_type, count in PRESET_COUNTS.items():
        for preset in range(count):
            drawable = drawable_key(input_type, preset)
            rows.append(
                {
                    "input_type": input_type,
                    "type_name": INPUT_TYPE_NAMES[input_type],
                    "preset": preset,
                    "drawable": drawable,
                    "label": drawable_label(drawable, input_type, preset),
                    "ms_id": resolve_ms_id(input_type, preset),
                    "validated_ms": (input_type, preset) in VALIDATED_MS_IDS,
                    "validated_flow_ui": (input_type, preset) in FLOW_UI_LABELS,
                    "manually_assigned": drawable in load_manual_mapping(),
                }
            )
    return rows
