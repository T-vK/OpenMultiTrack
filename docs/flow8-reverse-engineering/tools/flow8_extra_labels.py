"""Backward-compatible re-exports — prefer ``flow8_apk_labels``."""

from __future__ import annotations

from flow8_apk_labels import (
    all_apk_labels,
    apk_labels_dict,
    label_group,
    load_apk_label_entries,
    slug_for_apk_label,
    slugify,
)

FLOW_EXTRA_LABELS: dict[str, str] = apk_labels_dict()


def extra_label(slug: str) -> str | None:
    return FLOW_EXTRA_LABELS.get(slug)


def slug_for_label(label: str) -> str | None:
    return slug_for_apk_label(label)


def all_extra_labels() -> list[tuple[str, str]]:
    return [(slug, label) for slug, label, _group in all_apk_labels()]


FLOW_LABEL_GROUPS: dict[str, str] = {slug: group for slug, _label, group in all_apk_labels()}
