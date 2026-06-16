#!/usr/bin/env python3
"""Minimal 1:1 sprite sheet for Mixing Station + FLOW 8 scribble icons.

Order: Mixing Station ids 1–74, then FLOW 8 picker slots (type 0–6, preset order).
Each cell is icon + label underneath. Labels only — no ids or drawable keys.

Default: ``docs/mixer-icons/generated/icon_sprite_sheet.png``
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

TOOLS_DIR = Path(__file__).resolve().parent
DOCS_DIR = TOOLS_DIR.parent.parent
ASSETS = DOCS_DIR / "mixer-icons" / "assets"
GENERATED = DOCS_DIR / "mixer-icons" / "generated"
DEFAULT_OUT = GENERATED / "icon_sprite_sheet.png"
DEFAULT_MANIFEST = GENERATED / "icon_sprite_sheet_manifest.json"

sys.path.insert(0, str(TOOLS_DIR))
from flow8_icon_catalog import catalog_rows
from mixing_station_display_labels import display_label
from mixing_station_icons import ICON_LABELS

ICON_SIZE = 128
BG = (24, 24, 28, 255)
LABEL_COLOR = (230, 230, 230, 255)
MISSING_COLOR = (80, 80, 88, 255)


@dataclass
class SpriteEntry:
    source: str
    label: str
    path: Path
    icon_id: int | None = None
    drawable: str | None = None


def load_font(size: int) -> ImageFont.ImageFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/TTF/DejaVuSans.ttf",
    ):
        if Path(path).is_file():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


def ordered_entries(include_ms: bool, include_flow: bool) -> list[SpriteEntry]:
    entries: list[SpriteEntry] = []
    if include_ms:
        for icon_id in range(1, 75):
            entries.append(
                SpriteEntry(
                    source="mixing-station",
                    icon_id=icon_id,
                    label=display_label(icon_id) or ICON_LABELS[icon_id],
                    path=ASSETS / "mixing-station" / f"{icon_id}.png",
                )
            )
    if include_flow:
        for row in catalog_rows():
            entries.append(
                SpriteEntry(
                    source="flow8",
                    drawable=str(row["drawable"]),
                    label=str(row["label"]),
                    path=ASSETS / "flow8" / f"{row['drawable']}.png",
                )
            )
    return entries


def choose_square_layout(count: int, icon_size: int) -> tuple[int, int, int]:
    """Return (cols, rows, label_height) for a tight 1:1 sheet."""
    best: tuple[int, int, int] | None = None
    best_score = math.inf

    for cols in range(max(6, int(math.sqrt(count)) - 4), int(math.sqrt(count)) + 12):
        rows = math.ceil(count / cols)
        label_h = round(icon_size * cols / rows - icon_size)
        if label_h < 14 or label_h > 52:
            continue
        width = cols * icon_size
        height = rows * (icon_size + label_h)
        if width != height:
            continue
        empty = cols * rows - count
        score = empty
        if score < best_score:
            best_score = score
            best = (cols, rows, label_h)

    if best is not None:
        return best

    # Fallback: closest aspect ratio, then pad canvas to square.
    cols = max(6, round(math.sqrt(count)))
    rows = math.ceil(count / cols)
    label_h = max(16, round(icon_size * cols / rows - icon_size))
    return cols, rows, label_h


def truncate_label(text: str, font: ImageFont.ImageFont, max_width: int) -> str:
    text = text.strip()
    if font.getlength(text) <= max_width:
        return text
    while len(text) > 1 and font.getlength(text + "…") > max_width:
        text = text[:-1]
    return text + "…"


def build_sprite_sheet(
    entries: list[SpriteEntry],
    icon_size: int,
) -> tuple[Image.Image, list[dict], int, int, int]:
    cols, rows, label_h = choose_square_layout(len(entries), icon_size)
    row_h = icon_size + label_h
    width = cols * icon_size
    height = rows * row_h
    side = max(width, height)
    if width != height:
        # Pad to 1:1 without changing cell size.
        canvas = Image.new("RGBA", (side, side), BG)
    else:
        canvas = Image.new("RGBA", (width, height), BG)

    draw = ImageDraw.Draw(canvas)
    font_size = max(11, min(14, label_h - 8))
    font = load_font(font_size)
    manifest: list[dict] = []

    for index, entry in enumerate(entries):
        row, col = divmod(index, cols)
        x = col * icon_size
        y = row * row_h

        if entry.path.is_file():
            with Image.open(entry.path) as icon:
                icon = icon.convert("RGBA")
                icon.thumbnail((icon_size, icon_size), Image.Resampling.LANCZOS)
                px = x + (icon_size - icon.width) // 2
                py = y + (icon_size - icon.height) // 2
                canvas.paste(icon, (px, py), icon)
            present = True
        else:
            draw.rectangle(
                (x + 6, y + 6, x + icon_size - 6, y + icon_size - 6),
                outline=MISSING_COLOR,
                width=1,
            )
            present = False

        caption = truncate_label(entry.label, font, icon_size - 4)
        tw = font.getlength(caption)
        tx = x + (icon_size - tw) / 2
        ty = y + icon_size + (label_h - font_size) / 2 - 1
        draw.text((tx, ty), caption, fill=LABEL_COLOR, font=font)

        manifest.append(
            {
                "index": index,
                "row": row,
                "col": col,
                "x": int(x),
                "y": int(y),
                "icon_size": icon_size,
                "label_height": label_h,
                "label": entry.label,
                "caption": caption,
                "source": entry.source,
                "icon_id": entry.icon_id,
                "drawable": entry.drawable,
                "path": str(entry.path),
                "present": present,
            }
        )

    return canvas, manifest, cols, rows, label_h


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--icon-size", type=int, default=ICON_SIZE)
    parser.add_argument("--ms-only", action="store_true")
    parser.add_argument("--flow-only", action="store_true")
    args = parser.parse_args()

    include_ms = not args.flow_only
    include_flow = not args.ms_only
    entries = ordered_entries(include_ms, include_flow)
    sheet, manifest, cols, rows, label_h = build_sprite_sheet(entries, args.icon_size)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.output)
    args.manifest.write_text(
        json.dumps(
            {
                "file": str(args.output),
                "width": sheet.width,
                "height": sheet.height,
                "aspect_ratio": "1:1",
                "cols": cols,
                "rows": rows,
                "icon_size": args.icon_size,
                "label_height": label_h,
                "count": len(entries),
                "order": "mixing-station 1–74, then flow8 picker slots",
                "entries": manifest,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(
        f"Wrote {args.output} ({sheet.width}×{sheet.height}, {len(entries)} icons, "
        f"{cols}×{rows} grid)"
    )
    print(f"Wrote {args.manifest}")


if __name__ == "__main__":
    main()
