#!/usr/bin/env python3
"""Generate docs/mixer-icons.md with embedded icon tables."""

from __future__ import annotations

import argparse
import sys
from collections import defaultdict
from pathlib import Path

from flow8_icon_decode import (
    FLOW_UI_LABELS,
    INPUT_TYPE_CONDENSOR_MIC,
    INPUT_TYPE_DYNAMIC_MIC,
    INPUT_TYPE_GUITAR_OR_BASS,
    INPUT_TYPE_GUITAR_PAGE,
    INPUT_TYPE_LINE_INSTRUMENT,
    INPUT_TYPE_PLAYBACK,
    PRESET_ICON_TABLES,
    resolve_preset_icon,
)
from mixing_station_display_labels import DISPLAY_LABELS, display_label
from mixing_station_icons import ICON_LABELS

TOOLS_DIR = Path(__file__).resolve().parent
DOCS_DIR = TOOLS_DIR.parent.parent
REPO_ROOT = DOCS_DIR.parent
ASSETS_DIR = DOCS_DIR / "mixer-icons" / "assets"
OUTPUT = DOCS_DIR / "mixer-icons.md"

INPUT_TYPE_NAMES = {
    INPUT_TYPE_DYNAMIC_MIC: "Dynamic mic",
    INPUT_TYPE_CONDENSOR_MIC: "Condenser mic",
    INPUT_TYPE_GUITAR_OR_BASS: "Guitar / bass",
    INPUT_TYPE_LINE_INSTRUMENT: "Line instrument",
    INPUT_TYPE_GUITAR_PAGE: "Guitar page (extended)",
    INPUT_TYPE_PLAYBACK: "Playback / source",
}

PRESET_COUNTS = {
    INPUT_TYPE_DYNAMIC_MIC: 15,
    INPUT_TYPE_CONDENSOR_MIC: 11,
    INPUT_TYPE_GUITAR_OR_BASS: 18,
    INPUT_TYPE_LINE_INSTRUMENT: 18,
    INPUT_TYPE_GUITAR_PAGE: 8,
    INPUT_TYPE_PLAYBACK: 12,
}

IMG = 32  # markdown table icon width (px)


def flow_drawable(input_type: int, preset: int) -> str:
    return f"input_icon_{input_type * 100 + preset:03d}"


def ms_icon_img(icon_id: int) -> str:
    rel = f"mixer-icons/assets/mixing-station/{icon_id}.svg"
    label = display_label(icon_id) or ICON_LABELS[icon_id]
    return f'<img src="{rel}" alt="{label}" width="{IMG}" />'


def flow_icon_img(drawable: str, alt: str) -> str:
    rel = f"mixer-icons/assets/flow8/{drawable}.png"
    return f'<img src="{rel}" alt="{alt}" width="{IMG}" />'


def flow_preset_label(input_type: int, preset: int, ms_id: int | None) -> str:
    key = (input_type, preset)
    if key in FLOW_UI_LABELS:
        return FLOW_UI_LABELS[key]
    if ms_id is not None:
        return display_label(ms_id) or ICON_LABELS[ms_id]
    return "—"


def flow_preset_rows() -> list[dict]:
    rows: list[dict] = []
    for input_type, count in PRESET_COUNTS.items():
        for preset in range(count):
            ms_id = resolve_preset_icon(input_type, preset)
            drawable = flow_drawable(input_type, preset)
            label = flow_preset_label(input_type, preset, ms_id)
            validated = (input_type, preset) in FLOW_UI_LABELS
            rows.append(
                {
                    "input_type": input_type,
                    "type_name": INPUT_TYPE_NAMES[input_type],
                    "preset": preset,
                    "drawable": drawable,
                    "label": label,
                    "ms_id": ms_id,
                    "ms_slug": ICON_LABELS[ms_id] if ms_id else "",
                    "validated_label": validated,
                }
            )
    return rows


def write_doc(path: Path) -> None:
    lines: list[str] = [
        "# Mixer scribble icon reference",
        "",
        "Channel scribble icons on Behringer X32 / X-Air consoles, **Mixing Station**, and",
        "**FLOW 8** share a common numeric id space (**1–74**). OpenMultiTrack resolves FLOW 8",
        "BLE/USB state to those ids before rendering strip glyphs.",
        "",
        "> **Assets:** Mixing Station / X32 artwork is from the community",
        "> [behringer-icons](https://github.com/mamarguerat/behringer-icons) SVG pack (same",
        "> numbering as the desk). FLOW 8 picker PNGs are extracted from `Flowmix_v1.9.apk`",
        "> (`res/drawable-*/input_icon_NNN`). Regenerate with the commands below.",
        "",
        "## Regenerating",
        "",
        "```bash",
        "cd docs/flow8-reverse-engineering/tools",
        "python3 extract_icon_assets.py      # download SVGs + extract Flow PNGs",
        "python3 export_icon_tables.py doc   # rewrite this file",
        "```",
        "",
        "To refresh FLOW UI labels from a device with Flow Mix installed, extend and run",
        "`Flow8IconTableExtractor` (see `tools/Flow8IconTableExtractor.java`).",
        "",
        "---",
        "",
        "## Mixing Station / X32 icons (ids 1–74)",
        "",
        "Labels are the original names from the [behringer-icons](https://github.com/mamarguerat/behringer-icons)",
        "pack. Slug ids (`kick-back`, …) match OSC / `MixingStationIcons.kt`.",
        "",
        "| Display label | Slug | ID | Icon |",
        "| ------------- | ---- | -- | ---- |",
    ]

    for icon_id in range(1, 75):
        slug = ICON_LABELS[icon_id]
        name = display_label(icon_id) or slug
        lines.append(f"| {name} | `{slug}` | {icon_id} | {ms_icon_img(icon_id)} |")

    lines.extend(
        [
            "",
            "---",
            "",
            "## FLOW 8 picker icons",
            "",
            "FLOW 8 does not send Mixing Station ids directly. The official app stores an",
            "**input type** (0–5) and **preset index**; native code maps that pair to an MS id.",
            "Drawable assets are named `input_icon_{type×100+preset:03d}`.",
            "",
            "**Labels:** rows marked *(validated)* were read from the Flow Mix UI on hardware",
            "(firmware v11749). Other labels are inferred from the resolved MS id and the",
            "behringer-icons display name until a full native label dump is available.",
            "",
            "| Flow label | Input type | Preset | Drawable | MS ID | MS slug | Icon |",
            "| ---------- | ---------- | ------ | -------- | ----- | ------- | ---- |",
        ]
    )

    for row in flow_preset_rows():
        label = row["label"]
        if row["validated_label"]:
            label += " *(validated)*"
        ms_id = row["ms_id"]
        ms_slug = f"`{row['ms_slug']}`" if row["ms_slug"] else "—"
        ms_id_cell = str(ms_id) if ms_id is not None else "—"
        icon = flow_icon_img(row["drawable"], row["label"])
        lines.append(
            f"| {label} | {row['input_type']} ({row['type_name']}) | {row['preset']} "
            f"| `{row['drawable']}` | {ms_id_cell} | {ms_slug} | {icon} |"
        )

    lines.extend(
        [
            "",
            "---",
            "",
            "## Combined reference (by Mixing Station id)",
            "",
            "One MS id may appear in several FLOW picker slots (e.g. multiple mic presets →",
            "Handheld Mic). FLOW columns list every `(drawable → Flow label)` pair that",
            "resolves to the id.",
            "",
            "| MS display label | MS slug | MS ID | MS icon | FLOW drawables | FLOW icons |",
            "| ---------------- | ------- | ----- | ------- | -------------- | ---------- |",
        ]
    )

    by_ms: dict[int, list[dict]] = defaultdict(list)
    for row in flow_preset_rows():
        if row["ms_id"] is not None:
            by_ms[row["ms_id"]].append(row)

    for icon_id in range(1, 75):
        slug = ICON_LABELS[icon_id]
        name = display_label(icon_id) or slug
        flow_rows = by_ms.get(icon_id, [])
        if flow_rows:
            drawables = ", ".join(f"`{r['drawable']}`" for r in flow_rows)
            flow_labels = "; ".join(
                r["label"] + (" *" if r["validated_label"] else "") for r in flow_rows
            )
            flow_imgs = " ".join(flow_icon_img(r["drawable"], r["label"]) for r in flow_rows[:4])
            if len(flow_rows) > 4:
                flow_imgs += f" +{len(flow_rows) - 4}"
        else:
            drawables = "—"
            flow_labels = "—"
            flow_imgs = "—"
        lines.append(
            f"| {name} | `{slug}` | {icon_id} | {ms_icon_img(icon_id)} "
            f"| {drawables} | {flow_imgs} |"
        )

    lines.extend(
        [
            "",
            "---",
            "",
            "## Related",
            "",
            "- [flow8-reverse-engineering/06-channel-icons-and-stereo-link.md](flow8-reverse-engineering/06-channel-icons-and-stereo-link.md) — BLE/USB decode",
            "- `mixer-behringer/.../MixingStationIcons.kt` — strip glyph rendering",
            "- `mixer-behringer/.../Flow8IconPresets.kt` — `(input_type, preset)` tables",
            "",
        ]
    )

    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {path}")


def ms_table() -> None:
    print("## Mixing Station scribble icon IDs (1–74)\n")
    print("| MS ID | Slug | Display label | Icon |")
    print("| ----- | ---- | ------------- | ---- |")
    for icon_id in range(1, 75):
        slug = ICON_LABELS[icon_id]
        name = display_label(icon_id) or slug
        print(f"| {icon_id} | `{slug}` | {name} | {ms_icon_img(icon_id)} |")
    print()


def flow_input_types() -> None:
    print("## FLOW input types\n")
    print("| Type | Category | Preset slots (APK v1.9) |")
    print("| ---- | -------- | ----------------------- |")
    for type_id, name in INPUT_TYPE_NAMES.items():
        slots = PRESET_COUNTS.get(type_id, "?")
        first = flow_drawable(type_id, 0)
        last = flow_drawable(type_id, slots - 1)
        print(f"| {type_id} | {name} | `{first}` … `{last}` ({slots}) |")
    print()


def validated_preset_map() -> None:
    print("## Hardware-validated `(input_type, preset)` → icon mapping\n")
    from flow8_icon_decode import PRESET_TO_MS_ICON

    print("| Input type | Preset | Flow drawable | Flow UI label | MS ID | MS slug |")
    print("| ---------- | ------ | ------------- | ------------- | ----- | ------- |")
    for key in sorted(PRESET_TO_MS_ICON.keys()):
        input_type, preset = key
        ms_id = PRESET_TO_MS_ICON[key]
        flow_label = FLOW_UI_LABELS.get(key, "")
        drawable = flow_drawable(input_type, preset)
        ms_slug = ICON_LABELS[ms_id]
        type_name = INPUT_TYPE_NAMES.get(input_type, str(input_type))
        print(
            f"| {input_type} ({type_name}) | {preset} | `{drawable}` | {flow_label} "
            f"| {ms_id} | `{ms_slug}` |"
        )
    print()


def main() -> None:
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    if which in ("all", "doc"):
        write_doc(OUTPUT)
    if which in ("all", "ms"):
        ms_table()
    if which in ("all", "types"):
        flow_input_types()
    if which in ("all", "presets"):
        validated_preset_map()


if __name__ == "__main__":
    main()
