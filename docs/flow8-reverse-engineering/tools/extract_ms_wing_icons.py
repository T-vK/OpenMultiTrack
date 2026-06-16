#!/usr/bin/env python3
"""Extract Behringer WING channel icons from the Mixing Station APK atlas.

Mixing Station stores WING scribble artwork as ``wing_ch_TTNN`` regions inside
``assets/lib/packed/packs*.png`` (libGDX atlas). X32 / X-Air / M32 share the
canonical ids 1–74 (see ``mixing-station/``); WING adds 130 picker slots.

Default APK search paths::

    $MIXING_STATION_APK
    ~/Downloads/Mixing*Station*.apk

Output: ``docs/mixer-icons/assets/mixing-station-wing/wing_ch_TTNN.png``
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
DOCS_DIR = TOOLS_DIR.parent.parent
OUT_DIR = DOCS_DIR / "mixer-icons" / "assets" / "mixing-station-wing"

sys.path.insert(0, str(TOOLS_DIR))
from ms_atlas_extract import extract_regions, find_apk, load_atlas


def wing_icon_names(regions: dict) -> list[str]:
    return sorted(name for name in regions if name.startswith("wing_ch_"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="Mixing Station APK path")
    parser.add_argument("--output", type=Path, default=OUT_DIR)
    args = parser.parse_args()

    apk = find_apk(args.apk)
    names = wing_icon_names(load_atlas(apk))
    count = extract_regions(apk, names, args.output)
    print(f"Extracted {count} WING icons from {apk.name} -> {args.output}")


if __name__ == "__main__":
    main()
