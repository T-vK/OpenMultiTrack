"""FLOW-only scribble labels not in the Mixing Station 1–74 catalog.

Flow Mix uses distinct artwork for TS jacks (mono), female panel connectors,
speaker mount variants, groups/bus icons, etc. MS ids 1–74 cover TRS/XLR/RCA
but not always the same pictures — assign MS labels when the art matches the
BMP, otherwise pick a FLOW-only label here.

Type 0 picker order (first slots, hardware UI)::

    000 None · 001 DCA · 002 Effects · 003 Groups · 004 TS Plug ·
    005 XLR Female · 006 DIN 5-pin MIDI · 007 RCA Plug · 008 TS Jack Female · …
"""

from __future__ import annotations

# slug → display name (stable id prefix ``flow:slug`` in the mapper UI)
FLOW_EXTRA_LABELS: dict[str, str] = {
    # Bus / routing
    "dca": "DCA",
    "effects": "Effects",
    "groups": "Groups",
    # Connectors — TS (mono); MS only has TRS Plug / TRS L / TRS R
    "ts-plug": "TS Plug",
    "ts-plug-left": "TS Plug Left",
    "ts-plug-right": "TS Plug Right",
    "ts-jack-female": "TS Jack Female",
    # Connectors — other panel / cable types
    "xlr-female": "XLR Female",
    "din-midi": "DIN 5-pin MIDI",
    "rca-plug": "RCA Plug",
    "rca-jack-female": "RCA Jack Female",
    # Speakers — mount variants (MS has wedge / array / on-pole, not these)
    "speaker-wall-mount": "Speaker (wall mount)",
    "speaker-ceiling-mount": "Speaker (ceiling mount)",
    "speaker-floor-stand": "Speaker (floor stand)",
    # Music notation
    "treble-clef": "Treble clef",
    "bass-clef": "Bass clef",
    "whole-note": "Whole note",
    "half-note": "Half note",
    "quarter-note": "Quarter note",
    "eighth-note": "Eighth note",
    "beamed-notes": "Beamed notes",
    "sharp": "Sharp",
    "flat": "Flat",
    "natural": "Natural",
    "segno": "Segno",
    "coda": "Coda",
    "repeat": "Repeat",
    "pause": "Pause",
    "mp": "mp",
    "mf": "mf",
    "ff": "ff",
    # Playback / devices
    "turntable": "Turntable",
    "cd-disc": "CD",
    "cassette": "Cassette",
    "mp3-player": "Media player",
    "smartphone": "Smartphone",
    "tablet-portrait": "Tablet",
    "tablet-landscape": "Tablet (landscape)",
    # Monitoring / misc
    "listen-ear": "Listen",
}

# slug → group heading in the mapper UI
FLOW_LABEL_GROUPS: dict[str, str] = {
    "dca": "Routing / bus",
    "effects": "Routing / bus",
    "groups": "Routing / bus",
    "ts-plug": "Connectors",
    "ts-plug-left": "Connectors",
    "ts-plug-right": "Connectors",
    "ts-jack-female": "Connectors",
    "xlr-female": "Connectors",
    "din-midi": "Connectors",
    "rca-plug": "Connectors",
    "rca-jack-female": "Connectors",
    "speaker-wall-mount": "Speakers",
    "speaker-ceiling-mount": "Speakers",
    "speaker-floor-stand": "Speakers",
    "turntable": "Playback",
    "cd-disc": "Playback",
    "cassette": "Playback",
    "mp3-player": "Playback",
    "smartphone": "Devices",
    "tablet-portrait": "Devices",
    "tablet-landscape": "Devices",
    "listen-ear": "Monitoring",
}

_LABEL_TO_SLUG: dict[str, str] = {v.casefold(): k for k, v in FLOW_EXTRA_LABELS.items()}


def extra_label(slug: str) -> str | None:
    return FLOW_EXTRA_LABELS.get(slug)


def slug_for_label(label: str) -> str | None:
    return _LABEL_TO_SLUG.get(label.strip().casefold())


def label_group(slug: str) -> str:
    return FLOW_LABEL_GROUPS.get(slug, "FLOW only")


def all_extra_labels() -> list[tuple[str, str]]:
    return sorted(FLOW_EXTRA_LABELS.items(), key=lambda item: item[1].casefold())
