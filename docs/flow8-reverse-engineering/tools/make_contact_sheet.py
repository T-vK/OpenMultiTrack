#!/usr/bin/env python3
"""Minimal 1:1 sprite sheet for Mixing Station + FLOW 8 scribble icons.

Each **supported Mixing Station mixer line** gets a reserved block of picker slots
(74 for most desks, 130 for WING). Cells use MS channel icon artwork when the APK
bundles it (Behringer X32-family + WING); otherwise cells stay empty, with the
MS mixer branding image in the first slot when available (``mt_x32``, ``ah_sq``, …).

Order: MS mixer lines (manufacturer list), then FLOW 8 picker (100 slots).

Extract assets first::

    python3 extract_ms_wing_icons.py
    python3 extract_ms_brand_icons.py
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
WING_ASSETS = ASSETS / "mixing-station-wing"
BRAND_ASSETS = ASSETS / "mixing-station-brands"
GENERATED = DOCS_DIR / "mixer-icons" / "generated"
DEFAULT_OUT = GENERATED / "icon_sprite_sheet.png"
DEFAULT_MANIFEST = GENERATED / "icon_sprite_sheet_manifest.json"

sys.path.insert(0, str(TOOLS_DIR))
from ms_mixer_icon_sets import MIXER_SECTIONS, SheetSlot, all_mixer_slots, ms_slot_count

ICON_SIZE = 128
BG = (24, 24, 28, 255)
LABEL_COLOR = (230, 230, 230, 255)
MISSING_COLOR = (80, 80, 88, 255)


@dataclass
class SpriteEntry:
    source: str
    label: str
    path: Path | None
    mixer_key: str | None = None
    mixer_name: str | None = None
    slot_index: int | None = None
    icon_id: int | None = None
    drawable: str | None = None
    wing_key: str | None = None
    brand_atlas: str | None = None
    use_brand: bool = False


def load_font(size: int) -> ImageFont.ImageFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/TTF/DejaVuSans.ttf",
    ):
        if Path(path).is_file():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


def slot_to_entry(slot: SheetSlot) -> SpriteEntry:
    return SpriteEntry(
        source=f"mixer/{slot.mixer_key}",
        mixer_key=slot.mixer_key,
        mixer_name=slot.mixer_name,
        slot_index=slot.slot_index,
        label=slot.label,
        path=slot.path,
        icon_id=slot.icon_id,
        wing_key=slot.wing_key,
        brand_atlas=slot.brand_atlas,
        use_brand=slot.use_brand,
    )


def ordered_entries(
    include_ms: bool,
    include_flow: bool,
    *,
    brand_fallback: bool = True,
) -> list[SpriteEntry]:
    entries: list[SpriteEntry] = []
    if include_ms:
        for slot in all_mixer_slots(WING_ASSETS, BRAND_ASSETS, brand_fallback=brand_fallback):
            entries.append(slot_to_entry(slot))
    if include_flow:
        from flow8_icon_catalog import catalog_rows as flow_rows

        for row in flow_rows():
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
    best: tuple[int, int, int] | None = None
    best_score = math.inf

    for cols in range(max(6, int(math.sqrt(count)) - 10), int(math.sqrt(count)) + 30):
        rows = math.ceil(count / cols)
        label_h = round(icon_size * cols / rows - icon_size)
        if label_h < 12 or label_h > 52:
            continue
        width = cols * icon_size
        height = rows * (icon_size + label_h)
        if width != height:
            continue
        empty = cols * rows - count
        if empty < best_score:
            best_score = empty
            best = (cols, rows, label_h)

    if best is not None:
        return best

    cols = max(6, round(math.sqrt(count)))
    rows = math.ceil(count / cols)
    label_h = max(14, round(icon_size * cols / rows - icon_size))
    side = max(cols, rows) * icon_size
    rows = math.ceil(side / icon_size)
    cols = math.ceil(side / icon_size)
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
    canvas = Image.new("RGBA", (side, side), BG)

    draw = ImageDraw.Draw(canvas)
    font_size = max(10, min(13, label_h - 8))
    font = load_font(font_size)
    manifest: list[dict] = []

    for index, entry in enumerate(entries):
        row, col = divmod(index, cols)
        x = col * icon_size
        y = row * row_h

        present = False
        if entry.path is not None and entry.path.is_file():
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
                "mixer_key": entry.mixer_key,
                "mixer_name": entry.mixer_name,
                "slot_index": entry.slot_index,
                "icon_id": entry.icon_id,
                "drawable": entry.drawable,
                "wing_key": entry.wing_key,
                "brand_atlas": entry.brand_atlas,
                "use_brand": entry.use_brand,
                "path": str(entry.path) if entry.path else None,
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
    parser.add_argument(
        "--no-brand-fallback",
        action="store_true",
        help="Leave brand-mixer first slots empty instead of mixer branding images",
    )
    args = parser.parse_args()

    include_ms = not args.flow_only
    include_flow = not args.ms_only
    entries = ordered_entries(
        include_ms,
        include_flow,
        brand_fallback=not args.no_brand_fallback,
    )

    if include_ms and not WING_ASSETS.is_dir():
        print("WING icons missing. Run: python3 extract_ms_wing_icons.py", file=sys.stderr)
    if include_ms and not args.no_brand_fallback and not BRAND_ASSETS.is_dir():
        print("Brand icons missing. Run: python3 extract_ms_brand_icons.py", file=sys.stderr)

    sheet, manifest, cols, rows, label_h = build_sprite_sheet(entries, args.icon_size)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.output)
    mixer_names = [s.name for s in MIXER_SECTIONS]
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
                "ms_mixer_lines": len(MIXER_SECTIONS),
                "ms_slot_count": ms_slot_count(),
                "mixer_sections": [
                    {
                        "key": s.key,
                        "name": s.name,
                        "brand_atlas": s.brand_atlas,
                        "slot_count": s.slot_count,
                        "fill": s.fill,
                    }
                    for s in MIXER_SECTIONS
                ],
                "order": (
                    f"MS mixer lines ({len(mixer_names)}): "
                    + ", ".join(mixer_names)
                    + "; then FLOW 8 picker (100)"
                ),
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
