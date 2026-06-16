"""Load/save FLOW drawable → label assignments (``flow8_icon_mapping.json``)."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from flow8_extra_labels import FLOW_EXTRA_LABELS, label_group, slug_for_label
from flow8_icon_catalog import INPUT_TYPE_NAMES, PRESET_COUNTS, drawable_key
from mixing_station_display_labels import display_label

TOOLS_DIR = Path(__file__).resolve().parent
MAPPING_JSON = TOOLS_DIR / "flow8_icon_mapping.json"
HINTS_JSON = TOOLS_DIR / "flow8_picker_hints.json"

DOCS_DIR = TOOLS_DIR.parent.parent
FLOW_ASSETS = DOCS_DIR / "mixer-icons" / "assets" / "flow8"


@dataclass
class Assignment:
    label: str
    ms_id: int | None = None
    flow_slug: str | None = None

    def to_json(self) -> dict:
        out: dict = {"label": self.label}
        if self.ms_id is not None:
            out["ms_id"] = self.ms_id
        if self.flow_slug is not None:
            out["flow_slug"] = self.flow_slug
        return out

    @classmethod
    def from_json(cls, value: int | dict) -> Assignment:
        if isinstance(value, int):
            return cls(label=display_label(value) or f"MS {value}", ms_id=value)
        label = str(value.get("label", "")).strip()
        ms_id = value.get("ms_id")
        flow_slug = value.get("flow_slug")
        if ms_id is not None:
            ms_id = int(ms_id)
        if flow_slug is not None:
            flow_slug = str(flow_slug)
        if not label and ms_id is not None:
            label = display_label(ms_id) or f"MS {ms_id}"
        return cls(label=label, ms_id=ms_id, flow_slug=flow_slug)


@dataclass
class MappingState:
    version: int
    assignments: dict[str, Assignment]
    queue: list[str]
    focus: str | None = None

    def to_json(self) -> dict:
        out = {
            "version": self.version,
            "assignments": {k: v.to_json() for k, v in sorted(self.assignments.items())},
            "queue": self.queue,
        }
        if self.focus:
            out["focus"] = self.focus
        return out


def current_drawable(state: MappingState) -> str | None:
    if state.focus:
        return state.focus
    return state.queue[0] if state.queue else None


def app_drawable_order() -> list[str]:
    """All FLOW picker drawables in app order (100 slots)."""
    drawables: list[str] = []
    for input_type in sorted(PRESET_COUNTS):
        count = PRESET_COUNTS[input_type]
        for preset in range(count):
            drawables.append(drawable_key(input_type, preset))
    return drawables


def drawable_meta(drawable: str) -> dict:
    key = int(drawable.removeprefix("input_icon_"))
    input_type, preset = divmod(key, 100)
    return {
        "drawable": drawable,
        "input_type": input_type,
        "preset": preset,
        "type_name": INPUT_TYPE_NAMES.get(input_type, str(input_type)),
        "asset": f"flow8/{drawable}.png",
        "has_asset": (FLOW_ASSETS / f"{drawable}.png").is_file(),
    }


def ms_id_for_label(label: str) -> int | None:
    trimmed = label.strip()
    for icon_id in range(1, 75):
        if display_label(icon_id) == trimmed:
            return icon_id
    return None


def assignment_for_label(label: str) -> Assignment:
    trimmed = label.strip()
    ms_id = ms_id_for_label(trimmed)
    if ms_id is not None:
        return Assignment(label=trimmed, ms_id=ms_id)
    slug = slug_for_label(trimmed)
    if slug is not None:
        return Assignment(label=FLOW_EXTRA_LABELS[slug], ms_id=None, flow_slug=slug)
    return Assignment(label=trimmed, ms_id=None)


def load_picker_hints() -> dict[str, str]:
    if not HINTS_JSON.is_file():
        return {}
    data = json.loads(HINTS_JSON.read_text(encoding="utf-8"))
    raw = data.get("hints", {})
    return {str(k): str(v) for k, v in raw.items()}


def label_catalog() -> list[dict]:
    """Assignable labels for the mapper UI."""
    items: list[dict] = []
    for icon_id in range(1, 75):
        name = display_label(icon_id)
        if not name:
            continue
        items.append(
            {
                "id": f"ms:{icon_id}",
                "label": name,
                "ms_id": icon_id,
                "group": "Mixing Station",
            }
        )
    for slug, name in sorted(FLOW_EXTRA_LABELS.items(), key=lambda x: x[1].casefold()):
        items.append(
            {
                "id": f"flow:{slug}",
                "label": name,
                "ms_id": None,
                "flow_slug": slug,
                "group": label_group(slug),
            }
        )
    return items


def default_queue(assignments: dict[str, Assignment]) -> list[str]:
    return [d for d in app_drawable_order() if d not in assignments]


def normalize_queue(queue: list[str], assignments: dict[str, Assignment]) -> list[str]:
    order = app_drawable_order()
    known = set(order)
    seen: set[str] = set()
    out: list[str] = []
    for drawable in queue:
        if drawable in known and drawable not in assignments and drawable not in seen:
            out.append(drawable)
            seen.add(drawable)
    for drawable in order:
        if drawable not in assignments and drawable not in seen:
            out.append(drawable)
            seen.add(drawable)
    return out


def load_state() -> MappingState:
    if not MAPPING_JSON.is_file():
        return MappingState(version=2, assignments={}, queue=app_drawable_order())
    data = json.loads(MAPPING_JSON.read_text(encoding="utf-8"))
    version = int(data.get("version", 1))
    raw = data.get("assignments", {})
    assignments = {str(k): Assignment.from_json(v) for k, v in raw.items()}
    queue = data.get("queue")
    if not isinstance(queue, list):
        queue = default_queue(assignments)
    queue = normalize_queue([str(x) for x in queue], assignments)
    focus = data.get("focus")
    focus = str(focus) if focus else None
    return MappingState(version=max(version, 2), assignments=assignments, queue=queue, focus=focus)


def save_state(state: MappingState) -> None:
    state.queue = normalize_queue(state.queue, state.assignments)
    MAPPING_JSON.write_text(json.dumps(state.to_json(), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def assign(state: MappingState, drawable: str, label: str) -> None:
    if drawable not in app_drawable_order():
        raise ValueError(f"Unknown drawable: {drawable}")
    state.assignments[drawable] = assignment_for_label(label)
    state.queue = [d for d in state.queue if d != drawable]
    state.focus = None


def clear_assignment(state: MappingState, drawable: str) -> None:
    state.assignments.pop(drawable, None)
    state.queue = normalize_queue([drawable, *state.queue], state.assignments)
    state.focus = drawable


def skip_to_end(state: MappingState, drawable: str) -> None:
    state.queue = [d for d in state.queue if d != drawable]
    state.queue.append(drawable)
    state.focus = None


def jump_to(state: MappingState, drawable: str) -> None:
    if drawable not in app_drawable_order():
        raise ValueError(f"Unknown drawable: {drawable}")
    state.focus = drawable


def ms_id_lookup(drawable: str, state: MappingState | None = None) -> int | None:
    state = state or load_state()
    entry = state.assignments.get(drawable)
    if entry is None:
        return None
    if entry.ms_id is not None:
        return entry.ms_id
    return None


def label_lookup(drawable: str, state: MappingState | None = None) -> str | None:
    state = state or load_state()
    entry = state.assignments.get(drawable)
    if entry is not None:
        return entry.label
    return None
