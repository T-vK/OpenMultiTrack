#!/usr/bin/env python3
"""Generate icon reference tables for doc 06 and docs/mixer-icons.md."""

from __future__ import annotations

import argparse
import sys
from collections import defaultdict
from pathlib import Path

from flow8_icon_catalog import (
    FLOW_UI_LABELS,
    INPUT_TYPE_NAMES,
    PRESET_COUNTS,
    VALIDATED_MS_IDS,
    catalog_rows,
    drawable_key,
    resolve_ms_id,
)
from mixing_station_display_labels import display_label
from mixing_station_icons import ICON_LABELS

TOOLS_DIR = Path(__file__).resolve().parent
FLOW8_DIR = TOOLS_DIR.parent
DOCS_DIR = FLOW8_DIR.parent
OUTPUT = DOCS_DIR / "mixer-icons.md"
DOC06 = FLOW8_DIR / "06-channel-icons-and-stereo-link.md"

INPUT_TYPE_NAMES = INPUT_TYPE_NAMES  # re-export from catalog

PRESET_COUNTS_LOCAL = PRESET_COUNTS


def flow_drawable(input_type: int, preset: int) -> str:
    return drawable_key(input_type, preset)


def md_img(assets_prefix: str, subpath: str, alt: str) -> str:
    return f"![{alt}]({assets_prefix}/{subpath})"


def ms_icon_img(icon_id: int, assets_prefix: str) -> str:
    label = display_label(icon_id) or ICON_LABELS[icon_id]
    return md_img(assets_prefix, f"mixing-station/{icon_id}.png", label)


def flow_icon_img(drawable: str, alt: str, assets_prefix: str) -> str:
    return md_img(assets_prefix, f"flow8/{drawable}.png", alt)


DOC06_ASSETS = "../mixer-icons/assets"
STANDALONE_ASSETS = "mixer-icons/assets"


def flow_preset_rows() -> list[dict]:
    rows: list[dict] = []
    for row in catalog_rows():
        ms_id = row["ms_id"]
        rows.append(
            {
                "input_type": row["input_type"],
                "type_name": row["type_name"],
                "preset": row["preset"],
                "drawable": row["drawable"],
                "label": row["label"],
                "ms_id": ms_id,
                "ms_slug": ICON_LABELS[ms_id] if ms_id else row.get("flow_slug") or "",
                "validated_label": row["validated_flow_ui"],
            }
        )
    return rows


def appendix_a_ms(assets_prefix: str) -> list[str]:
    lines = [
        "## Appendix A: Mixing Station scribble icon IDs (1–74)",
        "",
        "Resolved icon values on the wire and in `getChannelIconId` use this",
        "X32 / X-Air / Mixing Station numbering. Icons below are the original X32 BMP",
        "artwork (Patrick-Gilles Maillot / [behringer-icons](https://github.com/mamarguerat/behringer-icons)),",
        "converted to PNG — the same pictures Mixing Station shows for scribble ids.",
        "",
        "| Label | Slug | ID | Icon |",
        "| ----- | ---- | -- | ---- |",
    ]
    for icon_id in range(1, 75):
        slug = ICON_LABELS[icon_id]
        name = display_label(icon_id) or slug
        lines.append(f"| {name} | `{slug}` | {icon_id} | {ms_icon_img(icon_id, assets_prefix)} |")
    lines.append("")
    return lines


def appendix_b_flow(assets_prefix: str) -> list[str]:
    lines = [
        "## Appendix B: FLOW 8 picker icons",
        "",
        "Drawable assets from `Flowmix_v1.9.apk` (`res/drawable-*/input_icon_NNN`).",
        "Labels marked *(validated)* were read from the Flow Mix UI on hardware (firmware v11749);",
        "others come from `flow8_icon_mapping.json` (run `serve_flow8_mapper.py` to edit).",
        "MS ID is set when the label matches Mixing Station ids 1–74; FLOW-only labels",
        "(DCA, clefs, …) have no MS id. Type 6 drawables `input_icon_600`…`617` are the",
        "last 18 picker slots.",
        "",
        "| Label | Input type | Preset | Drawable | MS ID | MS slug | Icon |",
        "| ----- | ---------- | ------ | -------- | ----- | ------- | ---- |",
    ]
    for row in flow_preset_rows():
        label = row["label"]
        if row["validated_label"]:
            label += " *(validated)*"
        ms_id = row["ms_id"]
        if ms_id is not None:
            ms_slug = f"`{row['ms_slug']}`" if row.get("ms_slug") else f"`{ICON_LABELS[ms_id]}`"
        elif row.get("flow_slug"):
            ms_slug = f"`flow:{row['flow_slug']}`"
        else:
            ms_slug = "—"
        ms_id_cell = str(ms_id) if ms_id is not None else "—"
        icon = flow_icon_img(row["drawable"], row["label"], assets_prefix)
        lines.append(
            f"| {label} | {row['input_type']} ({row['type_name']}) | {row['preset']} "
            f"| `{row['drawable']}` | {ms_id_cell} | {ms_slug} | {icon} |"
        )
    lines.append("")
    return lines


def appendix_c_validated() -> list[str]:
    lines = [
        "## Appendix C: Hardware-validated preset → icon mapping",
        "",
        "Firmware **v11749**, capture 2026-06-08. Other `(input_type, preset)` pairs must",
        "be resolved via `getInputChannelPresetIconIdAtIndex` in the native library.",
        "",
        "| Input type | Preset | Flow drawable | Flow UI label | MS ID | MS label |",
        "| ---------- | ------ | ------------- | ------------- | ----- | -------- |",
    ]
    for key in sorted(VALIDATED_MS_IDS.keys()):
        input_type, preset = key
        ms_id = VALIDATED_MS_IDS[key]
        flow_label = FLOW_UI_LABELS.get(key, "")
        drawable = flow_drawable(input_type, preset)
        ms_slug = ICON_LABELS[ms_id]
        type_name = INPUT_TYPE_NAMES.get(input_type, str(input_type))
        lines.append(
            f"| {input_type} ({type_name}) | {preset} | `{drawable}` | {flow_label} "
            f"| {ms_id} | `{ms_slug}` |"
        )
    lines.extend(
        [
            "",
            "Drawable key formula: `type × 100 + preset`, zero-padded to three digits",
            "(`input_icon_{key:03d}`).",
            "",
            "*Maintained in `tools/flow8_icon_catalog.py` (`VALIDATED_MS_IDS`, `FLOW_UI_LABELS`).*",
            "",
        ]
    )
    return lines


def appendix_d_combined(assets_prefix: str) -> list[str]:
    lines = [
        "## Appendix D: Combined reference (by Mixing Station id)",
        "",
        "Cross-reference of Mixing Station ids with every FLOW picker slot that resolves to",
        "the same id. **Icon (MS)** uses the X32 BMP artwork; **Icon (FLOW)** is from the",
        "Flow Mix APK drawable named in the FLOW drawable column.",
        "",
        "| Label (MS) | MS slug | MS ID | Icon (MS) | FLOW drawable(s) | Icon (FLOW) |",
        "| ---------- | ------- | ----- | --------- | ---------------- | ----------- |",
    ]
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
            flow_imgs = " ".join(
                flow_icon_img(r["drawable"], r["label"], assets_prefix) for r in flow_rows[:3]
            )
            if len(flow_rows) > 3:
                flow_imgs += f" … +{len(flow_rows) - 3}"
        else:
            drawables = "—"
            flow_imgs = "—"
        lines.append(
            f"| {name} | `{slug}` | {icon_id} | {ms_icon_img(icon_id, assets_prefix)} "
            f"| {drawables} | {flow_imgs} |"
        )
    lines.append("")
    return lines


def icon_appendices(assets_prefix: str) -> str:
    parts: list[str] = []
    parts.extend(appendix_a_ms(assets_prefix))
    parts.extend(appendix_b_flow(assets_prefix))
    parts.extend(appendix_c_validated())
    parts.extend(appendix_d_combined(assets_prefix))
    return "\n".join(parts)


def patch_doc06() -> None:
    text = DOC06.read_text(encoding="utf-8")
    start = text.find("## Appendix A:")
    end = text.find("\n---\n\n## Related documents")
    if start < 0 or end < 0:
        raise SystemExit("Could not find appendix block in doc 06")
    new_block = icon_appendices(DOC06_ASSETS)
    updated = text[:start] + new_block + text[end + 1 :]
    DOC06.write_text(updated, encoding="utf-8")
    print(f"Patched {DOC06}")


def write_standalone_doc(path: Path) -> None:
    lines = [
        "# Mixer scribble icon reference",
        "",
        "Channel scribble icons on Behringer X32 / X-Air consoles, **Mixing Station**, and",
        "**FLOW 8** share a common numeric id space (**1–74**).",
        "",
        "The full tables (with embedded icons) also live in",
        "[flow8-reverse-engineering/06-channel-icons-and-stereo-link.md](flow8-reverse-engineering/06-channel-icons-and-stereo-link.md)",
        "appendices A–D.",
        "",
        "## Regenerating",
        "",
        "```bash",
        "cd docs/flow8-reverse-engineering/tools",
        "python3 extract_icon_assets.py",
        "python3 serve_flow8_mapper.py   # browser UI for FLOW → label mapping",
        "python3 export_icon_tables.py all",
        "```",
        "",
        "---",
        "",
    ]
    lines.append(icon_appendices(STANDALONE_ASSETS))
    lines.extend(
        [
            "---",
            "",
            "## Related",
            "",
            "- `mixer-behringer/.../MixingStationIcons.kt`",
            "- `mixer-behringer/.../Flow8IconPresets.kt`",
            "",
        ]
    )
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {path}")


def ms_table_stdout(assets_prefix: str) -> None:
    print("## Mixing Station scribble icon IDs (1–74)\n")
    for line in appendix_a_ms(assets_prefix)[4:]:
        print(line)


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
    for line in appendix_c_validated():
        print(line)


def main() -> None:
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    if which in ("all", "doc"):
        write_standalone_doc(OUTPUT)
    if which in ("all", "doc06"):
        patch_doc06()
    if which in ("all", "ms"):
        ms_table_stdout(STANDALONE_ASSETS)
    if which in ("all", "types"):
        flow_input_types()
    if which in ("all", "presets"):
        validated_preset_map()


if __name__ == "__main__":
    main()
