#!/usr/bin/env python3
"""Extract Mixing Station mixer branding images from the APK atlas.

These are the pictures MS shows in the mixer picker (``mt_x32``, ``b_wing``,
``ah_sq``, …). Used on the sprite sheet for mixer families without bundled
channel icon artwork.

Output: ``docs/mixer-icons/assets/mixing-station-brands/{key}.png``
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
DOCS_DIR = TOOLS_DIR.parent.parent
OUT_DIR = DOCS_DIR / "mixer-icons" / "assets" / "mixing-station-brands"

# Manual replacements for low-quality MS atlas art.
MANUAL_BRAND_OVERRIDES: dict[str, str] = {
    "bfl": "Blackmagic Fairlight Live.png",
}

sys.path.insert(0, str(TOOLS_DIR))
from ms_atlas_extract import extract_regions, find_apk
from ms_mixer_icon_sets import all_brand_keys


def apply_manual_overrides(output_dir: Path) -> int:
    from PIL import Image

    applied = 0
    for atlas_key, source_name in MANUAL_BRAND_OVERRIDES.items():
        source = output_dir / source_name
        if not source.is_file():
            continue
        with Image.open(source) as im:
            im = im.convert("RGBA")
            thumb = im.copy()
            thumb.thumbnail((140, 140), Image.Resampling.LANCZOS)
            thumb.save(output_dir / f"{atlas_key}.png")
        applied += 1
    return applied


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="Mixing Station APK path")
    parser.add_argument("--output", type=Path, default=OUT_DIR)
    args = parser.parse_args()

    apk = find_apk(args.apk)
    skip = {key for key, source in MANUAL_BRAND_OVERRIDES.items() if (args.output / source).is_file()}
    names = sorted(all_brand_keys() - skip)
    count = extract_regions(apk, names, args.output)
    count += apply_manual_overrides(args.output)
    missing = [name for name in sorted(all_brand_keys()) if not (args.output / f"{name}.png").is_file()]
    print(f"Extracted {count} brand images from {apk.name} -> {args.output}")
    if missing:
        print(f"Missing atlas regions ({len(missing)}):", ", ".join(missing), file=sys.stderr)


if __name__ == "__main__":
    main()
