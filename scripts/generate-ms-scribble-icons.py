#!/usr/bin/env python3
"""Generate Mixing Station scribble PNGs (ids 1–74) for the Android app.

Source BMPs: https://github.com/mamarguerat/behringer-icons (GPL-3.0)
Original artwork: Patrick-Gilles Maillot (X32 icon pack).

Usage:
    python3 scripts/generate-ms-scribble-icons.py [--bmp-dir PATH]

Output:
    app/src/main/res/drawable-nodpi/ms_scribble_XX.png
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BMP_REPO = "https://github.com/mamarguerat/behringer-icons.git"
OUT_DIR = ROOT / "app/src/main/res/drawable-nodpi"
ICON_MIN = 1
ICON_MAX = 74


def ensure_bmp_dir(path: Path) -> Path:
    bmp = path / "bmp"
    if bmp.is_dir() and any(bmp.glob("*.bmp")):
        return bmp
    if not path.exists():
        subprocess.check_call(["git", "clone", "--depth", "1", DEFAULT_BMP_REPO, str(path)])
    return path / "bmp"


def bmp_to_png(bmp_path: Path, png_path: Path) -> None:
    from PIL import Image

    image = Image.open(bmp_path).convert("RGBA")
    pixels = image.load()
    width, height = image.size
    for y in range(height):
        for x in range(width):
            r, g, b, a = pixels[x, y]
            if r > 240 and g > 240 and b > 240:
                pixels[x, y] = (r, g, b, 0)
            else:
                pixels[x, y] = (r, g, b, 255)
    png_path.parent.mkdir(parents=True, exist_ok=True)
    image.save(png_path, optimize=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--bmp-dir",
        type=Path,
        help="Path to behringer-icons checkout (contains bmp/ subfolder)",
    )
    args = parser.parse_args()

    try:
        from PIL import Image  # noqa: F401
    except ImportError:
        print("Pillow is required: pip install pillow", file=sys.stderr)
        return 1

    if args.bmp_dir:
        bmp_dir = ensure_bmp_dir(args.bmp_dir)
    else:
        with tempfile.TemporaryDirectory() as tmp:
            bmp_dir = ensure_bmp_dir(Path(tmp))
            return convert_all(bmp_dir)

    return convert_all(bmp_dir)


def convert_all(bmp_dir: Path) -> int:
    converted = 0
    for icon_id in range(ICON_MIN, ICON_MAX + 1):
        bmp = bmp_dir / f"{icon_id}.bmp"
        if not bmp.exists():
            print(f"skip missing {bmp.name}")
            continue
        out = OUT_DIR / f"ms_scribble_{icon_id:02d}.png"
        bmp_to_png(bmp, out)
        converted += 1
    print(f"wrote {converted} icons to {OUT_DIR}")
    return 0 if converted == ICON_MAX else 1


if __name__ == "__main__":
    raise SystemExit(main())
