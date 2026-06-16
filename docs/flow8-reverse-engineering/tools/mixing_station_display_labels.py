"""Human-readable Mixing Station / X32 scribble icon names.

Source: behringer-icons readme (Patrick-Gilles Maillot BMP names, community SVG pack).
https://github.com/pmaillot/Behringer-X32-Icons
"""

from __future__ import annotations

import re
from pathlib import Path

# Fallback when readme parsing is unavailable (matches behringer-icons readme alt text).
DISPLAY_LABELS: list[str] = [
    "",  # 0
    "No icon",
    "Kick Back",
    "Kick Front",
    "Snare Top",
    "Snare Bottom",
    "High Tom",
    "Mid Tom",
    "Floor Tom",
    "Hi-Hat",
    "Ride",
    "Drum Kit",
    "Cowbell",
    "Bongos",
    "Congas",
    "Tambourine",
    "Vibraphone",
    "Electric Bass",
    "Acoustic Bass",
    "Contrabass",
    "Les Paul Guitar",
    "Ibanez Guitar",
    "Washburn Guitar",
    "Acoustic Guitar",
    "Bass Amp",
    "Guitar Amp",
    "Amp Cabinet",
    "Piano",
    "Organ",
    "Harpsichord",
    "Keyboard",
    "Synthesizer 1",
    "Synthesizer 2",
    "Synthesizer 3",
    "Keytar",
    "Trumpet",
    "Trombone",
    "Saxophone",
    "Clarinet",
    "Violin",
    "Cello",
    "Male Vocal",
    "Female Vocal",
    "Choir",
    "Hand Sign",
    "Talk A",
    "Talk B",
    "Large Diaphragm Mic",
    "Condenser Mic Left",
    "Condenser Mic Right",
    "Handheld Mic",
    "Wireless Mic",
    "Podium Mic",
    "Headset Mic",
    "XLR Jack",
    "TRS Plug",
    "TRS Plug Left",
    "TRS Plug Right",
    "RCA Plug Left",
    "RCA Plug Right",
    "Reel to Reel",
    "FX",
    "Computer",
    "Monitor Wedge",
    "Left Speaker",
    "Right Speaker",
    "Speaker Array",
    "Speaker on a Pole",
    "Amp Rack",
    "Controls",
    "Fader",
    "MixBus",
    "Matrix",
    "Routing",
    "Smiley",
]


def display_label(icon_id: int | None) -> str | None:
    if icon_id is None or not (1 <= icon_id <= 74):
        return None
    return DISPLAY_LABELS[icon_id] or None


def parse_behringer_readme(readme_path: Path) -> dict[int, str]:
    """Parse alt=\"…\" + N.svg pairs from behringer-icons readme.md."""
    text = readme_path.read_text(encoding="utf-8")
    labels: dict[int, str] = {}
    for match in re.finditer(
        r'alt="([^"]+)"[^>]*src="\./svg/(\d+)\.svg"',
        text,
    ):
        labels[int(match.group(2))] = match.group(1)
    return labels


def load_display_labels(behringer_readme: Path | None = None) -> list[str]:
    labels = list(DISPLAY_LABELS)
    if behringer_readme is None or not behringer_readme.is_file():
        return labels
    parsed = parse_behringer_readme(behringer_readme)
    for icon_id, name in parsed.items():
        if 1 <= icon_id < len(labels):
            labels[icon_id] = name
    return labels
