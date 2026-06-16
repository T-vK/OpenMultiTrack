"""FLOW Mix icon label catalog (APK / native library strings).

Picker grid icons have **no on-screen text**; descriptive names live in
``libcom_musicgroup_xairbt.so`` and are returned by JNI:

- ``getInputChannelPresetNameAtIndex(type, preset)``
- ``getInputChannelPresetDescriptionAtIndex(type, preset)``

Run ``extract_flow8_apk_labels.py`` on a device with Flow Mix installed to
refresh ``flow8_apk_labels.json`` from the live native tables. The bundled JSON
also lists FLOW-specific artwork names used by the mapper when MS ids 1–74 do
not match the drawable.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
LABELS_JSON = TOOLS_DIR / "flow8_apk_labels.json"

_LABEL_TO_SLUG: dict[str, str] = {}


def slugify(label: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", label.strip().casefold()).strip("-")
    return slug or "label"


def load_apk_label_entries() -> list[dict]:
    if not LABELS_JSON.is_file():
        return []
    data = json.loads(LABELS_JSON.read_text(encoding="utf-8"))
    entries = data.get("labels", [])
    if isinstance(entries, dict):
        return [{"slug": k, "label": v, "group": "APK"} for k, v in entries.items()]
    return list(entries)


def apk_labels_dict() -> dict[str, str]:
    out: dict[str, str] = {}
    for entry in load_apk_label_entries():
        slug = str(entry.get("slug") or slugify(str(entry["label"])))
        out[slug] = str(entry["label"])
    return out


def label_group(slug: str, entry: dict) -> str:
    return str(entry.get("group") or "APK / FLOW artwork")


def slug_for_apk_label(label: str) -> str | None:
    if not _LABEL_TO_SLUG:
        for slug, name in apk_labels_dict().items():
            _LABEL_TO_SLUG[name.casefold()] = slug
    return _LABEL_TO_SLUG.get(label.strip().casefold())


def all_apk_labels() -> list[tuple[str, str, str]]:
    rows: list[tuple[str, str, str]] = []
    for entry in load_apk_label_entries():
        label = str(entry["label"])
        slug = str(entry.get("slug") or slugify(label))
        group = label_group(slug, entry)
        rows.append((slug, label, group))
    return sorted(rows, key=lambda row: row[1].casefold())
