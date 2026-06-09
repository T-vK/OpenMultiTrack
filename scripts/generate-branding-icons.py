#!/usr/bin/env python3
"""Generate launcher, fastlane, and F-Droid repo icons from the app artwork."""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
BRANDING = ROOT / "branding"
APP_RES = ROOT / "app" / "src" / "main" / "res"
FASTLANE_ICON = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "icon.png"
FDROID_REPO_ICON = ROOT / "fdroid" / "repo-icons" / "icon.png"

BACKGROUND = (13, 27, 42, 255)  # #0D1B2A
WAVE = (79, 195, 247, 255)  # #4FC3F7
WAVE_LINE = (129, 212, 250, 255)  # #81D4FA
RECORD = (239, 83, 80, 255)  # #EF5350

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def draw_icon(size: int) -> Image.Image:
    image = Image.new("RGBA", (size, size), BACKGROUND)
    draw = ImageDraw.Draw(image)

    scale = size / 108.0
    pad = 0.12 * size

    # Waveform polygon (viewport 22,68 .. 86,68 with peak near top)
    points = [
        (22, 68),
        (34, 44),
        (46, 58),
        (58, 36),
        (70, 52),
        (82, 32),
        (86, 68),
    ]
    scaled = [(pad + (x / 108.0) * (size - 2 * pad), pad + (y / 108.0) * (size - 2 * pad)) for x, y in points]
    draw.polygon(scaled, fill=WAVE)

    baseline_y = pad + (72 / 108.0) * (size - 2 * pad)
    draw.line(
        (pad + (20 / 108.0) * (size - 2 * pad), baseline_y, pad + (88 / 108.0) * (size - 2 * pad), baseline_y),
        fill=WAVE_LINE,
        width=max(1, round(2 * scale)),
    )

    center_x = pad + (54 / 108.0) * (size - 2 * pad)
    center_y = pad + (24 / 108.0) * (size - 2 * pad)
    radius = 10 * scale
    draw.ellipse(
        (center_x - radius, center_y - radius, center_x + radius, center_y + radius),
        fill=RECORD,
    )
    return image


def save_png(path: Path, size: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    draw_icon(size).save(path, format="PNG", optimize=True)


def main() -> None:
    master = draw_icon(512)
    BRANDING.mkdir(parents=True, exist_ok=True)
    master.save(BRANDING / "icon-512.png", format="PNG", optimize=True)

    save_png(FASTLANE_ICON, 512)
    save_png(FDROID_REPO_ICON, 192)

    for folder, size in MIPMAP_SIZES.items():
        out_dir = APP_RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        save_png(out_dir / "ic_launcher.png", size)
        save_png(out_dir / "ic_launcher_round.png", size)

    print("Generated branding icons:")
    print(f"  {BRANDING / 'icon-512.png'}")
    print(f"  {FASTLANE_ICON}")
    print(f"  {FDROID_REPO_ICON}")
    for folder in MIPMAP_SIZES:
        print(f"  {APP_RES / folder}/ic_launcher.png")


if __name__ == "__main__":
    main()
