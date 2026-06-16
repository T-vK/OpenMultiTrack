#!/usr/bin/env python3
"""Interactively map each FLOW 8 picker drawable to a Mixing Station icon label.

Shows one Flow Mix icon at a time. Type to filter; Tab completes. Each MS label
can be assigned only once. Progress is saved to ``flow8_icon_mapping.json`` so
you can quit and resume.

After finishing (or partial progress), regenerate docs:

    python3 export_icon_tables.py all
"""

from __future__ import annotations

import argparse
import json
import readline
import sys
from pathlib import Path

from PIL import Image

TOOLS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS_DIR))

from flow8_icon_catalog import INPUT_TYPE_NAMES, PRESET_COUNTS, drawable_key
from mixing_station_display_labels import display_label

DOCS_DIR = TOOLS_DIR.parent.parent
FLOW_ASSETS = DOCS_DIR / "mixer-icons" / "assets" / "flow8"
MAPPING_JSON = TOOLS_DIR / "flow8_icon_mapping.json"

# id 1 … 74
ALL_MS_IDS = list(range(1, 75))


def ms_label(icon_id: int) -> str:
    return display_label(icon_id) or f"icon {icon_id}"


def label_with_id(icon_id: int) -> str:
    return f"{ms_label(icon_id)} ({icon_id})"


def flow_drawables() -> list[str]:
    drawables: list[str] = []
    for input_type, count in PRESET_COUNTS.items():
        for preset in range(count):
            drawables.append(drawable_key(input_type, preset))
    return drawables


def drawable_meta(drawable: str) -> tuple[int, int, str]:
    key = int(drawable.removeprefix("input_icon_"))
    input_type, preset = divmod(key, 100)
    type_name = INPUT_TYPE_NAMES.get(input_type, str(input_type))
    return input_type, preset, type_name


def load_mapping() -> dict[str, int]:
    if not MAPPING_JSON.is_file():
        return {}
    data = json.loads(MAPPING_JSON.read_text(encoding="utf-8"))
    raw = data.get("assignments", data)
    out: dict[str, int] = {}
    for drawable, value in raw.items():
        if isinstance(value, dict):
            out[drawable] = int(value["ms_id"])
        else:
            out[drawable] = int(value)
    return out


def save_mapping(assignments: dict[str, int]) -> None:
    payload = {
        "version": 1,
        "assignments": {k: assignments[k] for k in sorted(assignments)},
    }
    MAPPING_JSON.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def icon_preview(path: Path, width: int = 56) -> str:
    if not path.is_file():
        return f"(missing asset: {path})"
    with Image.open(path) as img:
        img = img.convert("L")
        height = max(10, int(img.height / img.width * width * 0.45))
        img = img.resize((width, height))
        ramp = " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$"
        lines: list[str] = []
        for y in range(height):
            row = "".join(ramp[img.getpixel((x, y)) * (len(ramp) - 1) // 255] for x in range(width))
            lines.append(row)
        return "\n".join(lines)


def remaining_ids(assignments: dict[str, int]) -> list[int]:
    used = set(assignments.values())
    return [icon_id for icon_id in ALL_MS_IDS if icon_id not in used]


def remaining_labels(assignments: dict[str, int]) -> list[str]:
    return [label_with_id(icon_id) for icon_id in remaining_ids(assignments)]


def parse_label_choice(text: str, assignments: dict[str, int]) -> int | None:
    text = text.strip()
    if not text:
        return None
    remaining = remaining_ids(assignments)
    # Exact "Name (id)" or bare id
    if text.isdigit():
        icon_id = int(text)
        if icon_id in remaining:
            return icon_id
        return None
    if text.endswith(")") and "(" in text:
        tail = text.rsplit("(", 1)[-1].rstrip(")")
        if tail.isdigit():
            icon_id = int(tail)
            if icon_id in remaining:
                return icon_id
    lowered = text.casefold()
    matches = [
        icon_id
        for icon_id in remaining
        if lowered in ms_label(icon_id).casefold() or lowered in label_with_id(icon_id).casefold()
    ]
    if len(matches) == 1:
        return matches[0]
    return None


def filter_suggestions(text: str, assignments: dict[str, int], limit: int = 8) -> list[str]:
    text = text.strip()
    remaining = remaining_ids(assignments)
    if not text:
        return [label_with_id(icon_id) for icon_id in remaining[:limit]]
    lowered = text.casefold()
    scored: list[tuple[int, int, str]] = []
    for icon_id in remaining:
        label = ms_label(icon_id)
        full = label_with_id(icon_id)
        if full.casefold().startswith(lowered):
            score = 0
        elif label.casefold().startswith(lowered):
            score = 1
        elif lowered in label.casefold():
            score = 2
        else:
            continue
        scored.append((score, icon_id, full))
    scored.sort()
    return [full for _, _, full in scored[:limit]]


class LabelCompleter:
    def __init__(self, assignments: dict[str, int]) -> None:
        self.assignments = assignments

    def complete(self, text: str, state: int) -> str | None:
        if state == 0:
            pool = remaining_labels(self.assignments)
            self.matches = [option for option in pool if option.casefold().startswith(text.casefold())]
        return self.matches[state] if state < len(self.matches) else None


def print_help(assignments: dict[str, int]) -> None:
    print()
    print("Commands:")
    print("  <label>     assign this drawable (Tab completes, partial name OK if unique)")
    print("  ?           list remaining labels")
    print("  u           undo previous assignment")
    print("  s           skip for now (leave unassigned)")
    print("  q           save and quit")
    print(f"  Assigned {len(assignments)}/{len(flow_drawables())} · "
          f"{len(remaining_ids(assignments))} labels left")
    print()


def prompt_assignment(
    drawable: str,
    assignments: dict[str, int],
    history: list[str],
) -> str | None:
    """Return 'quit', 'skip', 'undo', or accept and return drawable (assigned)."""
    input_type, preset, type_name = drawable_meta(drawable)
    icon_path = FLOW_ASSETS / f"{drawable}.png"
    remaining = remaining_ids(assignments)

    print()
    print("=" * 72)
    print(f"{drawable}  ·  type {input_type} ({type_name})  ·  preset {preset}")
    print(f"Asset: {icon_path}")
    print(icon_preview(icon_path))
    print("-" * 72)
    if not remaining:
        print("No unused MS labels left. Undo a previous assignment (u) or quit (q).")
    else:
        print(f"{len(remaining)} unused labels · type to filter, Tab to complete")

    readline.set_completer(LabelCompleter(assignments).complete)
    readline.parse_and_bind("tab: complete")

    while True:
        try:
            raw = input("MS label> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return "quit"

        if not raw:
            suggestions = filter_suggestions("", assignments)
            if suggestions:
                print("Suggestions:", ", ".join(suggestions))
            continue

        cmd = raw.casefold()
        if cmd in {"?", "help", "h"}:
            print_help(assignments)
            suggestions = filter_suggestions("", assignments)
            if suggestions:
                print("Remaining:", ", ".join(remaining_labels(assignments)))
            continue
        if cmd in {"q", "quit", "exit"}:
            return "quit"
        if cmd in {"s", "skip"}:
            return "skip"
        if cmd in {"u", "undo"}:
            return "undo"

        icon_id = parse_label_choice(raw, assignments)
        if icon_id is None:
            suggestions = filter_suggestions(raw, assignments)
            if suggestions:
                print("No unique match. Did you mean:")
                for suggestion in suggestions:
                    print(f"  {suggestion}")
            else:
                print("No matching unused label. Press ? to list remaining options.")
            continue

        assignments[drawable] = icon_id
        history.append(drawable)
        print(f"→ {label_with_id(icon_id)}")
        save_mapping(assignments)
        return drawable


def run(start_at: str | None, reset: bool) -> None:
    drawables = flow_drawables()
    assignments = {} if reset else load_mapping()
    history: list[str] = []

    if start_at:
        if start_at not in drawables:
            raise SystemExit(f"Unknown drawable: {start_at}")
        start_index = drawables.index(start_at)
    else:
        start_index = 0
        for index, drawable in enumerate(drawables):
            if drawable not in assignments:
                start_index = index
                break

    print("FLOW 8 → Mixing Station icon assignment")
    print(f"Mapping file: {MAPPING_JSON}")
    print_help(assignments)

    index = start_index
    while index < len(drawables):
        drawable = drawables[index]
        if drawable in assignments and not reset:
            index += 1
            continue

        result = prompt_assignment(drawable, assignments, history)
        if result == "quit":
            break
        if result == "skip":
            index += 1
            continue
        if result == "undo":
            if not history:
                print("Nothing to undo.")
                continue
            last = history.pop()
            assignments.pop(last, None)
            save_mapping(assignments)
            index = drawables.index(last)
            print(f"Undid {last}")
            continue
        index += 1

    save_mapping(assignments)
    done = len(assignments)
    total = len(drawables)
    print()
    print(f"Saved {MAPPING_JSON} ({done}/{total} assigned).")
    if done < total:
        unassigned = [d for d in drawables if d not in assignments]
        print(f"Still unassigned: {len(unassigned)} (resume anytime with the same command)")
    else:
        print("All drawables assigned. Regenerate docs:")
        print("  python3 export_icon_tables.py all")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--start",
        metavar="DRAWABLE",
        help="Start at drawable, e.g. input_icon_012",
    )
    parser.add_argument(
        "--reset",
        action="store_true",
        help="Discard existing mapping and start over",
    )
    args = parser.parse_args()
    if not FLOW_ASSETS.is_dir():
        raise SystemExit(
            f"Flow assets missing at {FLOW_ASSETS}\n"
            "Run: python3 extract_icon_assets.py"
        )
    run(args.start, args.reset)


if __name__ == "__main__":
    main()
