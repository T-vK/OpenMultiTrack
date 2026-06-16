"""FLOW 8 picker drawable catalog — MS id mapping from manual assignment.

Flow Mix APK icons are named ``input_icon_{key:03d}`` where
``key = input_type * 100 + preset_index``.

Run ``serve_flow8_mapper.py`` (HTML UI) or legacy ``assign_flow8_icons.py`` to build
``flow8_icon_mapping.json``, then ``export_icon_tables.py all`` to refresh docs.
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
    6: 18,
}

INPUT_TYPE_NAMES: dict[int, str] = {
    0: "Dynamic mic",
    1: "Condenser mic",
    2: "Guitar / bass",
    3: "Line instrument",
    4: "Guitar page (extended)",
    5: "Playback / source",
    6: "Music / routing",
}

# Hardware-validated Flow UI strings (firmware v11749, corrected via icon mapper).
FLOW_UI_LABELS: dict[tuple[int, int], str] = {
    (0, 4): "Wired Mic",
    (0, 7): "Wired Mic",
    (2, 2): "Crash",
    (3, 4): "Acoustic Guitar",
    (4, 2): "Synthesizer 1",
    (5, 7): "Speaker (Wall-Mounted)",
}

VALIDATED_MS_IDS: dict[tuple[int, int], int] = {
    (0, 4): 50,
    (0, 7): 50,
    (3, 4): 23,
    (4, 2): 31,
}


def drawable_key(input_type: int, preset: int) -> str:
    return f"input_icon_{input_type * 100 + preset:03d}"


def load_manual_assignments() -> dict[str, dict]:
    from flow8_mapping import load_state

    state = load_state()
    return {drawable: entry.to_json() for drawable, entry in state.assignments.items()}


def load_manual_mapping() -> dict[str, int]:
    from flow8_mapping import load_state

    state = load_state()
    out: dict[str, int] = {}
    for drawable, entry in state.assignments.items():
        if entry.ms_id is not None:
            out[drawable] = entry.ms_id
    return out


def load_manual_labels() -> dict[str, str]:
    from flow8_mapping import load_state

    state = load_state()
    return {drawable: entry.label for drawable, entry in state.assignments.items()}


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
    from flow8_mapping import load_state

    state = load_state()
    if drawable in state.assignments:
        return state.assignments[drawable].ms_id
    key = (input_type, preset)
    if key in VALIDATED_MS_IDS:
        return VALIDATED_MS_IDS[key]
    extracted = load_extracted_ms_tables()
    if key in extracted:
        return extracted[key]
    from flow8_icon_decode import resolve_preset_icon

    return resolve_preset_icon(input_type, preset)


def drawable_label(drawable: str, input_type: int | None = None, preset: int | None = None) -> str:
    manual = load_manual_labels()
    if drawable in manual:
        return manual[drawable]
    if input_type is not None and preset is not None:
        flow_ui = FLOW_UI_LABELS.get((input_type, preset))
        if flow_ui:
            return flow_ui
    from mixing_station_display_labels import display_label

    ms_id = None
    if input_type is not None and preset is not None:
        ms_id = resolve_ms_id(input_type, preset)
    else:
        ms_id = load_manual_mapping().get(drawable)
    if ms_id is not None:
        name = display_label(ms_id)
        if name:
            return name
    return drawable.replace("input_icon_", "icon ")


def catalog_rows() -> list[dict]:
    from flow8_mapping import load_state

    state = load_state()
    rows: list[dict] = []
    for input_type, count in PRESET_COUNTS.items():
        for preset in range(count):
            drawable = drawable_key(input_type, preset)
            entry = state.assignments.get(drawable)
            rows.append(
                {
                    "input_type": input_type,
                    "type_name": INPUT_TYPE_NAMES[input_type],
                    "preset": preset,
                    "drawable": drawable,
                    "label": drawable_label(drawable, input_type, preset),
                    "ms_id": resolve_ms_id(input_type, preset),
                    "flow_slug": entry.flow_slug if entry else None,
                    "validated_ms": (input_type, preset) in VALIDATED_MS_IDS,
                    "validated_flow_ui": (input_type, preset) in FLOW_UI_LABELS,
                    "manually_assigned": drawable in state.assignments,
                }
            )
    return rows
