#!/usr/bin/env python3
"""Generate launcher, fastlane, and F-Droid repo icons from iconv4.png."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "iconv4.png"
BRANDING = ROOT / "branding"
APP_RES = ROOT / "app" / "src" / "main" / "res"
APP_LOGO_DRAWABLE = APP_RES / "drawable-nodpi" / "ic_app_logo.png"
FASTLANE_ICON = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "icon.png"
FDROID_REPO_ICON = ROOT / "fdroid" / "repo-icons" / "icon.png"

# Dark circle behind the artwork — matches the icon frame interior.
CIRCLE_BG = (28, 28, 34, 255)

# Legacy launcher / in-app: artwork diameter as a fraction of the output size.
CIRCULAR_ART_SCALE = 0.58

# Adaptive-icon safe zone is 66dp inside a 108dp foreground layer.
ADAPTIVE_SAFE_ZONE = 66 / 108

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Adaptive foreground layers are 108dp (not the legacy 48dp launcher size).
ADAPTIVE_FOREGROUND_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def load_master() -> Image.Image:
    if not SOURCE.is_file():
        raise SystemExit(f"Missing source icon: {SOURCE}")
    return Image.open(SOURCE).convert("RGBA")


def paste_centered(canvas: Image.Image, art: Image.Image) -> None:
    x = (canvas.width - art.width) // 2
    y = (canvas.height - art.height) // 2
    canvas.paste(art, (x, y), art)


def compose_circular_icon(source: Image.Image, size: int, art_scale: float = CIRCULAR_ART_SCALE) -> Image.Image:
    """Full circular icon: colored disc + smaller centered artwork."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    draw.ellipse((0, 0, size - 1, size - 1), fill=CIRCLE_BG)

    art_size = max(1, int(size * art_scale))
    art = source.resize((art_size, art_size), Image.Resampling.LANCZOS)
    paste_centered(canvas, art)
    return canvas


def compose_adaptive_foreground(source: Image.Image, size: int) -> Image.Image:
    """Transparent 108dp layer with artwork scaled to the adaptive safe zone."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    art_size = max(1, int(size * ADAPTIVE_SAFE_ZONE))
    art = source.resize((art_size, art_size), Image.Resampling.LANCZOS)
    paste_centered(canvas, art)
    return canvas


def save_png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def main() -> None:
    master = load_master()

    BRANDING.mkdir(parents=True, exist_ok=True)
    save_png(BRANDING / "icon-512.png", compose_circular_icon(master, 512))
    save_png(FASTLANE_ICON, compose_circular_icon(master, 512))
    save_png(FDROID_REPO_ICON, compose_circular_icon(master, 192))
    save_png(APP_LOGO_DRAWABLE, compose_circular_icon(master, 128))

    for folder, size in MIPMAP_SIZES.items():
        out_dir = APP_RES / folder
        save_png(out_dir / "ic_launcher.png", compose_circular_icon(master, size))
        save_png(out_dir / "ic_launcher_round.png", compose_circular_icon(master, size))

    for folder, size in ADAPTIVE_FOREGROUND_SIZES.items():
        out_dir = APP_RES / folder
        save_png(out_dir / "ic_launcher_foreground.png", compose_adaptive_foreground(master, size))

    print("Generated circular branding icons from iconv4.png:")
    print(f"  {BRANDING / 'icon-512.png'}")
    print(f"  {FASTLANE_ICON}")
    print(f"  {FDROID_REPO_ICON}")
    print(f"  {APP_LOGO_DRAWABLE}")
    for folder in MIPMAP_SIZES:
        print(f"  {APP_RES / folder}/ic_launcher.png")


if __name__ == "__main__":
    main()
