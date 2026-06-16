"""Shared libGDX atlas extraction for Mixing Station APK assets."""

from __future__ import annotations

import os
import re
import zipfile
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

PACKED_PREFIX = "assets/lib/packed/"


@dataclass
class AtlasRegion:
    name: str
    page: str
    x: int
    y: int
    width: int
    height: int
    rotate: bool


def find_apk(explicit: Path | None) -> Path:
    if explicit is not None:
        if explicit.is_file():
            return explicit
        raise SystemExit(f"APK not found: {explicit}")
    env = os.environ.get("MIXING_STATION_APK")
    if env and Path(env).is_file():
        return Path(env)
    for pattern in (
        Path.home() / "Downloads",
        Path("/home/tamino/Downloads"),
    ):
        if not pattern.is_dir():
            continue
        matches = sorted(pattern.glob("Mixing*Station*.apk"))
        if matches:
            return matches[0]
    raise SystemExit(
        "Mixing Station APK not found. Set MIXING_STATION_APK or pass --apk PATH."
    )


def load_atlas(apk: Path) -> dict[str, AtlasRegion]:
    with zipfile.ZipFile(apk) as zf:
        text = zf.read(PACKED_PREFIX + "packs.atlas").decode("utf-8")
    return parse_atlas(text)


def parse_atlas(text: str) -> dict[str, AtlasRegion]:
    blocks = re.split(r"\n(?=\S+\.png\n)", text.strip())
    regions: dict[str, AtlasRegion] = {}
    for block in blocks:
        lines = [line.rstrip() for line in block.splitlines() if line.strip()]
        if not lines:
            continue
        page = lines[0].replace(".png", "")
        name: str | None = None
        data: dict[str, str] = {}
        for line in lines[1:]:
            if not line.startswith(" ") and ":" not in line:
                if name is not None:
                    regions[name] = _region_from_data(name, page, data)
                name = line.strip()
                data = {}
                continue
            if name is None or ":" not in line:
                continue
            key, value = line.split(":", 1)
            data[key.strip()] = value.strip()
        if name is not None:
            regions[name] = _region_from_data(name, page, data)
    return regions


def _region_from_data(name: str, page: str, data: dict[str, str]) -> AtlasRegion:
    xy = [int(v.strip()) for v in data["xy"].split(",")]
    size = [int(v.strip()) for v in data["size"].split(",")]
    return AtlasRegion(
        name=name,
        page=page,
        x=xy[0],
        y=xy[1],
        width=size[0],
        height=size[1],
        rotate=data.get("rotate", "false").lower() == "true",
    )


def extract_regions(apk: Path, names: list[str], output_dir: Path) -> int:
    output_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(apk) as zf:
        atlas_text = zf.read(PACKED_PREFIX + "packs.atlas").decode("utf-8")
        regions = parse_atlas(atlas_text)
        pages: dict[str, Image.Image] = {}
        written = 0
        for name in names:
            region = regions.get(name)
            if region is None:
                continue
            page_path = PACKED_PREFIX + f"{region.page}.png"
            if region.page not in pages:
                with zf.open(page_path) as handle:
                    pages[region.page] = Image.open(handle).convert("RGBA")
            sheet = pages[region.page]
            crop = sheet.crop(
                (
                    region.x,
                    region.y,
                    region.x + region.width,
                    region.y + region.height,
                )
            )
            if region.rotate:
                crop = crop.transpose(Image.Transpose.ROTATE_90)
            crop.save(output_dir / f"{name}.png")
            written += 1
    return written
