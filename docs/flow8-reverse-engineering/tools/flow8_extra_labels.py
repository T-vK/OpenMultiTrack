"""FLOW-only scribble labels not in the Mixing Station 1–74 catalog.

These appear in the Flow Mix picker (especially input type 6 / ``input_icon_600``…)
but have no matching X32 BMP id. Assignments may use ``ms_id: null``.
"""

from __future__ import annotations

# slug → display name (stable id prefix ``flow:slug`` in the mapper UI)
FLOW_EXTRA_LABELS: dict[str, str] = {
    "dca": "DCA",
    "treble-clef": "Treble clef",
    "bass-clef": "Bass clef",
    "effects": "Effects",
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
}

# Reverse lookup (case-insensitive) for import / migration.
_LABEL_TO_SLUG: dict[str, str] = {v.casefold(): k for k, v in FLOW_EXTRA_LABELS.items()}


def extra_label(slug: str) -> str | None:
    return FLOW_EXTRA_LABELS.get(slug)


def slug_for_label(label: str) -> str | None:
    return _LABEL_TO_SLUG.get(label.strip().casefold())


def all_extra_labels() -> list[tuple[str, str]]:
    return sorted(FLOW_EXTRA_LABELS.items(), key=lambda item: item[1].casefold())
