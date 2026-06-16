"""Label lookup for Mixing Station WING channel icons (``wing_ch_TTNN``)."""

from __future__ import annotations

import re
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent

_WING_KEY = re.compile(r"^wing_ch_(\d{4})$")


def parse_wing_key(name: str) -> tuple[int, int] | None:
    match = _WING_KEY.match(name)
    if not match:
        return None
    code = match.group(1)
    input_type = int(code[:2])
    preset = int(code[2:])
    if input_type == 50:
        # WING tail block aligns with FLOW type 6 presets 1–6 in our mapping.
        return 6, preset
    return input_type, preset


def flow_drawable(input_type: int, preset: int) -> str:
    return f"input_icon_{input_type * 100 + preset:03d}"


def wing_label(name: str, assignments: dict | None = None) -> str:
    parsed = parse_wing_key(name)
    if parsed is None:
        return name.removeprefix("wing_ch_")
    input_type, preset = parsed
    drawable = flow_drawable(input_type, preset)

    if assignments is None:
        from flow8_mapping import load_state

        assignments = load_state().assignments
    entry = assignments.get(drawable)
    if entry is not None and entry.label.strip():
        return entry.label.strip()

    from flow8_icon_catalog import drawable_label

    return drawable_label(drawable, input_type, preset)


def ordered_wing_icon_names(assets_dir: Path) -> list[str]:
    if not assets_dir.is_dir():
        return []
    return sorted(path.stem for path in assets_dir.glob("wing_ch_*.png"))


def ordered_wing_entries(assets_dir: Path) -> list[tuple[str, str, Path]]:
    from flow8_mapping import load_state

    assignments = load_state().assignments
    rows: list[tuple[str, str, Path]] = []
    for name in ordered_wing_icon_names(assets_dir):
        rows.append((name, wing_label(name, assignments), assets_dir / f"{name}.png"))
    return rows
