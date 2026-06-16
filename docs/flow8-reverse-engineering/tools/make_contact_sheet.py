#!/usr/bin/env python3
"""Build labeled contact sheets of Mixing Station + FLOW 8 scribble icons.

Mixing Station uses one shared id space (1–74) across the Behringer/Midas desks
it supports (X32, M32, X-Air/XR, WING, …). FLOW 8 adds 100 picker drawables
with extended artwork; many map to the same MS id but the pictures differ.

Default output (under ``docs/mixer-icons/generated/``):

- ``icon_contact_sheet_all.png`` — MS block + FLOW 8 blocks (by input type)
- ``icon_contact_sheet_ms.png`` — canonical MS / X32 family set only
- ``icon_contact_sheet_flow8.png`` — all 100 FLOW picker slots
- ``icon_contact_sheet_manifest.json`` — cell coordinates + metadata
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

TOOLS_DIR = Path(__file__).resolve().parent
DOCS_DIR = TOOLS_DIR.parent.parent
ASSETS = DOCS_DIR / "mixer-icons" / "assets"
GENERATED = DOCS_DIR / "mixer-icons" / "generated"

sys.path.insert(0, str(TOOLS_DIR))
from flow8_icon_catalog import INPUT_TYPE_NAMES, PRESET_COUNTS, catalog_rows
from mixing_station_display_labels import display_label
from mixing_station_icons import ICON_LABELS

CELL = 128
COLS = 14
LABEL_HEIGHT = 30
SECTION_PAD = 20
HEADER_HEIGHT = 44
SUBHEADER_HEIGHT = 32
BG = (28, 28, 32, 255)
SECTION_BG = (38, 38, 44, 255)
HEADER_COLOR = (245, 245, 245, 255)
SUBHEADER_COLOR = (200, 200, 210, 255)
LABEL_COLOR = (220, 220, 220, 255)
MISSING_COLOR = (90, 90, 96, 255)

MS_MIXER_FAMILIES = (
    "Mixing Station scribble ids 1–74 · shared by X32 · M32 · X-Air / XR · WING · "
    "FLOW 8 (resolved icon on wire)"
)


@dataclass
class SheetEntry:
    source: str
    label: str
    path: Path
    icon_id: int | None = None
    drawable: str | None = None
    input_type: int | None = None
    preset: int | None = None
    subtitle: str | None = None

    def caption(self) -> str:
        if self.subtitle:
            return self.subtitle
        if self.source == "mixing-station" and self.icon_id is not None:
            return f"{self.icon_id:02d} · {self.label}"
        if self.drawable:
            short = self.drawable.removeprefix("input_icon_")
            return f"{short} · {self.label}"
        return self.label


@dataclass
class SheetSection:
    title: str
    subtitle: str
    entries: list[SheetEntry] = field(default_factory=list)


def load_font(size: int = 13) -> ImageFont.ImageFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/TTF/DejaVuSans.ttf",
    ):
        if Path(path).is_file():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


def load_font_bold(size: int = 15) -> ImageFont.ImageFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        if Path(path).is_file():
            try:
                return ImageFont.truetype(path, size=size)
            except OSError:
                continue
    return load_font(size)


def ms_sections() -> list[SheetSection]:
    entries: list[SheetEntry] = []
    for icon_id in range(1, 75):
        name = display_label(icon_id) or ICON_LABELS[icon_id]
        entries.append(
            SheetEntry(
                source="mixing-station",
                icon_id=icon_id,
                label=name,
                path=ASSETS / "mixing-station" / f"{icon_id}.png",
            )
        )
    return [
        SheetSection(
            title="Mixing Station / X32 family",
            subtitle=MS_MIXER_FAMILIES,
            entries=entries,
        )
    ]


def flow_sections() -> list[SheetSection]:
    rows = catalog_rows()
    by_type: dict[int, list[SheetEntry]] = {t: [] for t in PRESET_COUNTS}
    for row in rows:
        input_type = int(row["input_type"])
        preset = int(row["preset"])
        drawable = str(row["drawable"])
        label = str(row["label"])
        ms_id = row.get("ms_id")
        subtitle = None
        if ms_id is not None:
            subtitle = f"{drawable.removeprefix('input_icon_')} · MS {int(ms_id):02d} · {label}"
        else:
            subtitle = f"{drawable.removeprefix('input_icon_')} · {label}"
        by_type[input_type].append(
            SheetEntry(
                source="flow8",
                icon_id=int(ms_id) if ms_id is not None else None,
                drawable=drawable,
                input_type=input_type,
                preset=preset,
                label=label,
                subtitle=subtitle,
                path=ASSETS / "flow8" / f"{drawable}.png",
            )
        )

    sections: list[SheetSection] = []
    for input_type in sorted(PRESET_COUNTS):
        count = PRESET_COUNTS[input_type]
        type_name = INPUT_TYPE_NAMES.get(input_type, str(input_type))
        entries = by_type.get(input_type, [])
        sections.append(
            SheetSection(
                title=f"FLOW 8 · type {input_type} · {type_name}",
                subtitle=f"{count} picker slots · drawables input_icon_{input_type}00–{input_type}{count - 1:02d}",
                entries=entries,
            )
        )
    return sections


def auto_cols(count: int, preferred: int) -> int:
    if count <= 0:
        return preferred
    if count <= 8:
        return min(preferred, max(4, count))
    if count <= 15:
        return min(preferred, count)
    return preferred


def section_height(entries: int, cols: int, cell: int, label_h: int) -> int:
    if entries <= 0:
        return SUBHEADER_HEIGHT + SECTION_PAD
    rows = (entries + cols - 1) // cols
    return SUBHEADER_HEIGHT + rows * (cell + label_h) + SECTION_PAD


def sheet_height(sections: list[SheetSection], cols: int, cell: int, label_h: int, banner: str | None) -> int:
    height = SECTION_PAD
    if banner:
        height += HEADER_HEIGHT + SECTION_PAD
    for section in sections:
        if not section.entries:
            continue
        use_cols = auto_cols(len(section.entries), cols)
        height += section_height(len(section.entries), use_cols, cell, label_h)
    return height + SECTION_PAD


def sheet_width(sections: list[SheetSection], cols: int, cell: int) -> int:
    max_cols = cols
    for section in sections:
        if section.entries:
            max_cols = max(max_cols, auto_cols(len(section.entries), cols))
    return max_cols * cell


def truncate(text: str, max_len: int = 22) -> str:
    text = text.strip()
    if len(text) <= max_len:
        return text
    return text[: max_len - 1] + "…"


def paste_icon(
    canvas: Image.Image,
    draw: ImageDraw.ImageDraw,
    icon_path: Path,
    x: int,
    y: int,
    cell: int,
) -> bool:
    if not icon_path.is_file():
        draw.rectangle((x + 8, y + 8, x + cell - 8, y + cell - 8), outline=MISSING_COLOR, width=2)
        draw.text((x + 12, y + cell // 2 - 6), "missing", fill=MISSING_COLOR, font=load_font(11))
        return False
    with Image.open(icon_path) as icon:
        icon = icon.convert("RGBA")
        icon.thumbnail((cell, cell), Image.Resampling.LANCZOS)
        px = x + (cell - icon.width) // 2
        py = y + (cell - icon.height) // 2
        canvas.paste(icon, (px, py), icon)
    return True


def draw_section(
    canvas: Image.Image,
    draw: ImageDraw.ImageDraw,
    section: SheetSection,
    y: int,
    cols: int,
    cell: int,
    label_h: int,
    font: ImageFont.ImageFont,
    header_font: ImageFont.ImageFont,
    manifest: list[dict],
    section_index: int,
) -> int:
    if not section.entries:
        return y

    width = canvas.width
    use_cols = auto_cols(len(section.entries), cols)
    draw.rectangle((0, y, width, y + SUBHEADER_HEIGHT), fill=SECTION_BG)
    draw.text((12, y + 6), section.title, fill=HEADER_COLOR, font=header_font)
    draw.text((12, y + 22), truncate(section.subtitle, 110), fill=SUBHEADER_COLOR, font=font)
    y += SUBHEADER_HEIGHT

    for index, entry in enumerate(section.entries):
        row, col = divmod(index, use_cols)
        x = col * cell
        icon_y = y + row * (cell + label_h)
        present = paste_icon(canvas, draw, entry.path, x, icon_y, cell)
        caption = truncate(entry.caption())
        draw.text((x + 4, icon_y + cell + 5), caption, fill=LABEL_COLOR, font=font)
        manifest.append(
            {
                "section_index": section_index,
                "section_title": section.title,
                "index_in_section": index,
                "row": row,
                "col": col,
                "x": x,
                "y": icon_y,
                "cell": cell,
                "label_height": label_h,
                "present": present,
                "source": entry.source,
                "label": entry.label,
                "caption": caption,
                "icon_id": entry.icon_id,
                "drawable": entry.drawable,
                "input_type": entry.input_type,
                "preset": entry.preset,
                "path": str(entry.path),
            }
        )

    rows = (len(section.entries) + use_cols - 1) // use_cols
    return y + rows * (cell + label_h) + SECTION_PAD


def render_sheet(
    sections: list[SheetSection],
    cols: int,
    cell: int,
    label_h: int,
    banner: str | None,
) -> tuple[Image.Image, list[dict]]:
    width = sheet_width(sections, cols, cell)
    height = sheet_height(sections, cols, cell, label_h, banner)
    canvas = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(canvas)
    font = load_font(12)
    header_font = load_font_bold(15)
    manifest: list[dict] = []
    y = SECTION_PAD

    if banner:
        draw.rectangle((0, y, width, y + HEADER_HEIGHT), fill=SECTION_BG)
        draw.text((12, y + 12), banner, fill=HEADER_COLOR, font=header_font)
        y += HEADER_HEIGHT + SECTION_PAD

    for section_index, section in enumerate(sections):
        y = draw_section(
            canvas,
            draw,
            section,
            y,
            cols,
            cell,
            label_h,
            font,
            header_font,
            manifest,
            section_index,
        )

    return canvas, manifest


def write_outputs(
    stem: str,
    sections: list[SheetSection],
    cols: int,
    cell: int,
    label_h: int,
    banner: str | None,
    out_dir: Path,
) -> tuple[Path, int]:
    out_dir.mkdir(parents=True, exist_ok=True)
    png_path = out_dir / f"{stem}.png"
    sheet, manifest = render_sheet(sections, cols, cell, label_h, banner)
    sheet.save(png_path)
    manifest_path = out_dir / f"{stem}_manifest.json"
    manifest_path.write_text(
        json.dumps(
            {
                "file": str(png_path),
                "width": sheet.width,
                "height": sheet.height,
                "cols": cols,
                "cell": cell,
                "label_height": label_h,
                "banner": banner,
                "sections": [
                    {"title": s.title, "subtitle": s.subtitle, "count": len(s.entries)}
                    for s in sections
                ],
                "entries": manifest,
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    count = sum(len(s.entries) for s in sections)
    print(f"Wrote {png_path} ({sheet.width}×{sheet.height}, {count} icons)")
    print(f"Wrote {manifest_path}")
    return png_path, count


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=GENERATED)
    parser.add_argument("--cols", type=int, default=COLS, help="max columns per section (default 14)")
    parser.add_argument("--cell", type=int, default=CELL)
    parser.add_argument("--label-height", type=int, default=LABEL_HEIGHT)
    parser.add_argument("--ms-only", action="store_true")
    parser.add_argument("--flow-only", action="store_true")
    parser.add_argument("--combined-only", action="store_true", help="skip split ms/flow sheets")
    args = parser.parse_args()

    ms = ms_sections()
    flow = flow_sections()
    include_ms = not args.flow_only
    include_flow = not args.ms_only

    total = 0
    if include_ms and include_flow and not args.combined_only:
        total += write_outputs(
            "icon_contact_sheet_ms",
            ms,
            args.cols,
            args.cell,
            args.label_height,
            MS_MIXER_FAMILIES,
            args.output_dir,
        )[1]
        total += write_outputs(
            "icon_contact_sheet_flow8",
            flow,
            args.cols,
            args.cell,
            args.label_height,
            "FLOW 8 picker drawables (100 slots) · grouped by JNI input type",
            args.output_dir,
        )[1]

    combined_sections: list[SheetSection] = []
    banner = None
    if include_ms and include_flow:
        banner = (
            f"OpenMultiTrack icon reference · {sum(len(s.entries) for s in ms)} MS + "
            f"{sum(len(s.entries) for s in flow)} FLOW 8 = "
            f"{sum(len(s.entries) for s in ms) + sum(len(s.entries) for s in flow)} cells"
        )
        combined_sections.extend(ms)
        combined_sections.extend(flow)
    elif include_ms:
        combined_sections.extend(ms)
        banner = MS_MIXER_FAMILIES
    else:
        combined_sections.extend(flow)
        banner = "FLOW 8 picker drawables (100 slots)"

    _, combined_count = write_outputs(
        "icon_contact_sheet_all",
        combined_sections,
        args.cols,
        args.cell,
        args.label_height,
        banner,
        args.output_dir,
    )
    total = max(total, combined_count)
    print(f"Done — {total} icon cells in split sheets, {combined_count} in combined sheet")


if __name__ == "__main__":
    main()
