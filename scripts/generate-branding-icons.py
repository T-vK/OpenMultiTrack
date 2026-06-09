#!/usr/bin/env python3
"""Generate launcher, fastlane, and F-Droid repo icons from iconv2.png."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "iconv2.png"
BRANDING = ROOT / "branding"
APP_RES = ROOT / "app" / "src" / "main" / "res"
APP_LOGO_DRAWABLE = APP_RES / "drawable-nodpi" / "ic_app_logo.png"
FASTLANE_ICON = ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "icon.png"
FDROID_REPO_ICON = ROOT / "fdroid" / "repo-icons" / "icon.png"

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def load_master() -> Image.Image:
    if not SOURCE.is_file():
        raise SystemExit(f"Missing source icon: {SOURCE}")
    image = Image.open(SOURCE).convert("RGBA")
    return image


def save_resized(path: Path, image: Image.Image, size: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    resized = image.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(path, format="PNG", optimize=True)


def main() -> None:
    master = load_master()
    BRANDING.mkdir(parents=True, exist_ok=True)
    save_resized(BRANDING / "icon-512.png", master, 512)

    save_resized(FASTLANE_ICON, master, 512)
    save_resized(FDROID_REPO_ICON, master, 192)
    save_resized(APP_LOGO_DRAWABLE, master, 128)

    for folder, size in MIPMAP_SIZES.items():
        out_dir = APP_RES / folder
        save_resized(out_dir / "ic_launcher.png", master, size)
        save_resized(out_dir / "ic_launcher_round.png", master, size)
        save_resized(out_dir / "ic_launcher_foreground.png", master, size)

    print("Generated branding icons from iconv2.png:")
    print(f"  {BRANDING / 'icon-512.png'}")
    print(f"  {FASTLANE_ICON}")
    print(f"  {FDROID_REPO_ICON}")
    print(f"  {APP_LOGO_DRAWABLE}")
    for folder in MIPMAP_SIZES:
        print(f"  {APP_RES / folder}/ic_launcher.png")


if __name__ == "__main__":
    main()
