#!/usr/bin/env python3
"""Build a labeled contact sheet of MS + FLOW scribble icons for vision labeling / img2img.

Default layout: 10 columns, 128 px cells, label band under each icon row.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

TOOLS_DIR = Path(__file__).resolve().parent
DOCS_DIR = TOOLS_DIR.parent.parent
ASSETS = DOCS_DIR / "mixer-icons" / "assets"
DEFAULT_OUT = DOCS_DIR / "mixer-icons" / "generated" / "icon_contact_sheet.png"
MANIFEST_OUT = DOCS_DIR / "mixer-icons" / "generated" / "icon_contact_sheet_manifest.json"

sys.path.insert(0, str(TOOLS_DIR))
from flow8_icon_catalog import PRESET_COUNTS, catalog_rows, drawable_key
from mixing_station_display_labels import display_label
from mixing_station_icons import ICON_LABELS

CELL = 128
COLS = 10
LABEL_HEIGHT = 28
BG = (32, 32, 36, 255)
LABEL_COLOR = (220, 220, 220, 255)


def load_font(size: int = 14) -> ImageFont.ImageFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/TTF/DejaVuSans.ttf",
    ):
        if Path(path).is_file():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


def paste_icon(canvas: Image.Image, icon_path: Path, col: int, row: int, cell: int) -> None:
    if not icon_path.is_file():
        return
    with Image.open(icon_path) as icon:
        icon = icon.convert("RGBA")
        icon.thumbnail((cell, cell), Image.Resampling.LANCZOS)
        x = col * cell + (cell - icon.width) // 2
        y = row * (cell + LABEL_HEIGHT) + (cell - icon.height) // 2
        canvas.paste(icon, (x, y), icon)


def draw_label(
    draw: ImageDraw.ImageDraw,
    text: str,
    col: int,
    row: int,
    cell: int,
    label_h: int,
    font: ImageFont.ImageFont,
) -> None:
    x0 = col * cell
    y0 = row * (cell + label_h) + cell
    text = text if len(text) <= 18 else text[:17] + "…"
    draw.text((x0 + 4, y0 + 4), text, fill=LABEL_COLOR, font=font)


def sheet_entries(include_ms: bool, include_flow: bool) -> list[dict]:
    entries: list[dict] = []
    if include_ms:
        for icon_id in range(1, 75):
            entries.append(
                {
                    "source": "mixing-station",
                    "icon_id": icon_id,
                    "drawable": None,
                    "label": display_label(icon_id) or ICON_LABELS[icon_id],
                    "path": ASSETS / "mixing-station" / f"{icon_id}.png",
                }
            )
    if include_flow:
        for row in catalog_rows():
            entries.append(
                {
                    "source": "flow8",
                    "icon_id": row["ms_id"],
                    "drawable": row["drawable"],
                    "input_type": row["input_type"],
                    "preset": row["preset"],
                    "label": row["label"],
                    "path": ASSETS / "flow8" / f"{row['drawable']}.png",
                }
            )
    return entries


def build_sheet(
    entries: list[dict],
    cols: int,
    cell: int,
    label_h: int,
) -> tuple[Image.Image, list[dict]]:
    rows = (len(entries) + cols - 1) // cols
    width = cols * cell
    height = rows * (cell + label_h)
    canvas = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(canvas)
    font = load_font(13)
    manifest: list[dict] = []

    for index, entry in enumerate(entries):
        row, col = divmod(index, cols)
        paste_icon(canvas, Path(entry["path"]), col, row, cell)
        draw_label(draw, entry["label"], col, row, cell, label_h, font)
        manifest.append(
            {
                "index": index,
                "row": row,
                "col": col,
                "x": col * cell,
                "y": row * (cell + label_h),
                "cell": cell,
                "label_height": label_h,
                **entry,
                "path": str(entry["path"]),
            }
        )
    return canvas, manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--manifest", type=Path, default=MANIFEST_OUT)
    parser.add_argument("--cols", type=int, default=COLS)
    parser.add_argument("--cell", type=int, default=CELL)
    parser.add_argument("--label-height", type=int, default=LABEL_HEIGHT)
    parser.add_argument("--ms-only", action="store_true")
    parser.add_argument("--flow-only", action="store_true")
    args = parser.parse_args()

    include_ms = not args.flow_only
    include_flow = not args.ms_only
    entries = sheet_entries(include_ms, include_flow)
    sheet, manifest = build_sheet(entries, args.cols, args.cell, args.label_height)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.output)
    args.manifest.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Wrote {args.output} ({sheet.width}×{sheet.height}, {len(entries)} icons)")
    print(f"Wrote {args.manifest}")


if __name__ == "__main__":
    main()
