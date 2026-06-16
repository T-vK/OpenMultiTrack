#!/usr/bin/env python3
"""Interactively map each FLOW 8 picker drawable to a Mixing Station icon label.

Opens a window showing the current Flow Mix icon (large, on a dark background).
Type in the search box to filter; pick one unused MS label and press Enter or
Assign. Each MS label can be assigned only once. Progress saves to
``flow8_icon_mapping.json`` so you can quit and resume.

After finishing (or partial progress), regenerate docs::

    python3 export_icon_tables.py all

Requires a graphical display. On Debian/Ubuntu install::

    sudo apt install python3-tk python3-pil.imagetk

For terminal-only hosts with ``chafa`` installed, pass ``--terminal``.
"""

from __future__ import annotations

import argparse
import json
import os
import readline
import shutil
import subprocess
import sys
import tkinter as tk
import tkinter.font as tkfont
from pathlib import Path
from tkinter import ttk

from PIL import Image

try:
    import PIL.ImageTk as ImageTk
except ImportError:  # pragma: no cover - without python3-tk
    ImageTk = None  # type: ignore[misc, assignment]

TOOLS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS_DIR))

from flow8_icon_catalog import INPUT_TYPE_NAMES, PRESET_COUNTS, drawable_key
from mixing_station_display_labels import display_label

DOCS_DIR = TOOLS_DIR.parent.parent
FLOW_ASSETS = DOCS_DIR / "mixer-icons" / "assets" / "flow8"
MAPPING_JSON = TOOLS_DIR / "flow8_icon_mapping.json"

ICON_DISPLAY_PX = 384
BG_RGB = (32, 32, 36)

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


def filter_label_strings(text: str, assignments: dict[str, int]) -> list[str]:
    text = text.strip()
    pool = remaining_labels(assignments)
    if not text:
        return pool
    lowered = text.casefold()
    scored: list[tuple[int, str]] = []
    for full in pool:
        name = full.rsplit(" (", 1)[0]
        if full.casefold().startswith(lowered):
            score = 0
        elif name.casefold().startswith(lowered):
            score = 1
        elif lowered in name.casefold():
            score = 2
        else:
            continue
        scored.append((score, full))
    scored.sort()
    return [full for _, full in scored]


def load_display_image(path: Path, size: int = ICON_DISPLAY_PX) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (*BG_RGB, 255))
    if not path.is_file():
        return canvas
    with Image.open(path) as icon:
        icon = icon.convert("RGBA")
        icon = icon.resize((size, size), Image.Resampling.NEAREST)
        canvas.paste(icon, (0, 0), icon)
    return canvas


def show_icon_chafa(path: Path) -> None:
    if not shutil.which("chafa"):
        raise SystemExit("Install chafa for --terminal mode (e.g. apt install chafa)")
    if not path.is_file():
        print(f"(missing asset: {path})")
        return
    with Image.open(path) as icon:
        icon = icon.convert("RGBA")
        icon = icon.resize((128, 128), Image.Resampling.NEAREST)
        tmp = Path("/tmp/flow8_assign_preview.png")
        bg = Image.new("RGBA", (128, 128), (*BG_RGB, 255))
        bg.paste(icon, (0, 0), icon)
        bg.save(tmp)
    subprocess.run(
        ["chafa", "-s", "64x64", "--colors", "full", str(tmp)],
        check=False,
    )


class LabelCompleter:
    def __init__(self, assignments: dict[str, int]) -> None:
        self.assignments = assignments

    def complete(self, text: str, state: int) -> str | None:
        if state == 0:
            pool = remaining_labels(self.assignments)
            self.matches = [option for option in pool if option.casefold().startswith(text.casefold())]
        return self.matches[state] if state < len(self.matches) else None


class AssignFlow8Gui:
    def __init__(self, start_at: str | None, reset: bool) -> None:
        self.drawables = flow_drawables()
        self.assignments: dict[str, int] = {} if reset else load_mapping()
        self.history: list[str] = []
        self.index = 0
        self._photo: object | None = None

        if start_at:
            if start_at not in self.drawables:
                raise SystemExit(f"Unknown drawable: {start_at}")
            self.index = self.drawables.index(start_at)
        else:
            for i, drawable in enumerate(self.drawables):
                if drawable not in self.assignments:
                    self.index = i
                    break

        self.root = tk.Tk()
        self.root.title("FLOW 8 → Mixing Station icon assignment")
        self.root.configure(bg=f"#{BG_RGB[0]:02x}{BG_RGB[1]:02x}{BG_RGB[2]:02x}")
        self.root.minsize(720, 640)

        mono = tkfont.Font(family="DejaVu Sans Mono", size=11)
        title_font = tkfont.Font(family="DejaVu Sans", size=14, weight="bold")
        body_font = tkfont.Font(family="DejaVu Sans", size=11)

        outer = ttk.Frame(self.root, padding=12)
        outer.grid(row=0, column=0, sticky="nsew")
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)
        outer.columnconfigure(0, weight=1)
        outer.rowconfigure(1, weight=1)

        self.progress_var = tk.StringVar()
        ttk.Label(outer, textvariable=self.progress_var, font=body_font).grid(
            row=0, column=0, sticky="w", pady=(0, 8)
        )

        content = ttk.Frame(outer)
        content.grid(row=1, column=0, sticky="nsew")
        content.columnconfigure(0, weight=0)
        content.columnconfigure(1, weight=1)
        content.rowconfigure(0, weight=1)

        icon_frame = tk.Frame(content, bg=f"#{BG_RGB[0]:02x}{BG_RGB[1]:02x}{BG_RGB[2]:02x}")
        icon_frame.grid(row=0, column=0, padx=(0, 16), sticky="n")
        self.icon_label = tk.Label(icon_frame, bg=icon_frame["bg"])
        self.icon_label.pack()

        right = ttk.Frame(content)
        right.grid(row=0, column=1, sticky="nsew")
        right.columnconfigure(0, weight=1)
        right.rowconfigure(3, weight=1)

        self.title_var = tk.StringVar()
        ttk.Label(right, textvariable=self.title_var, font=title_font, wraplength=360).grid(
            row=0, column=0, sticky="w"
        )
        self.meta_var = tk.StringVar()
        ttk.Label(right, textvariable=self.meta_var, font=body_font, wraplength=360).grid(
            row=1, column=0, sticky="w", pady=(4, 12)
        )

        ttk.Label(right, text="Search unused MS labels:", font=body_font).grid(row=2, column=0, sticky="w")
        self.search_var = tk.StringVar()
        search_entry = ttk.Entry(right, textvariable=self.search_var, font=mono)
        search_entry.grid(row=3, column=0, sticky="new", pady=(4, 0))
        search_entry.bind("<KeyRelease>", self._on_search_change)
        search_entry.bind("<Down>", self._focus_list)
        search_entry.bind("<Return>", lambda _e: self._assign_selected())

        list_frame = ttk.Frame(right)
        list_frame.grid(row=4, column=0, sticky="nsew", pady=(4, 8))
        right.rowconfigure(4, weight=1)
        list_frame.columnconfigure(0, weight=1)
        list_frame.rowconfigure(0, weight=1)

        self.listbox = tk.Listbox(
            list_frame,
            font=mono,
            activestyle="dotbox",
            exportselection=False,
            height=16,
        )
        scroll = ttk.Scrollbar(list_frame, orient="vertical", command=self.listbox.yview)
        self.listbox.configure(yscrollcommand=scroll.set)
        self.listbox.grid(row=0, column=0, sticky="nsew")
        scroll.grid(row=0, column=1, sticky="ns")
        self.listbox.bind("<Double-Button-1>", lambda _e: self._assign_selected())
        self.listbox.bind("<Return>", lambda _e: self._assign_selected())

        self.status_var = tk.StringVar()
        ttk.Label(right, textvariable=self.status_var, font=body_font, wraplength=360).grid(
            row=5, column=0, sticky="w", pady=(4, 8)
        )

        buttons = ttk.Frame(right)
        buttons.grid(row=6, column=0, sticky="w")
        ttk.Button(buttons, text="Assign (Enter)", command=self._assign_selected).pack(side="left")
        ttk.Button(buttons, text="Skip", command=self._skip).pack(side="left", padx=(8, 0))
        ttk.Button(buttons, text="Undo", command=self._undo).pack(side="left", padx=(8, 0))
        ttk.Button(buttons, text="Save & Quit", command=self._quit).pack(side="left", padx=(8, 0))

        self.root.protocol("WM_DELETE_WINDOW", self._quit)
        self.search_entry = search_entry
        self._refresh_view(focus_search=True)

    def _focus_list(self, _event: tk.Event) -> str:
        if self.listbox.size():
            self.listbox.focus_set()
            self.listbox.selection_clear(0, tk.END)
            self.listbox.selection_set(0)
            self.listbox.activate(0)
        return "break"

    def _on_search_change(self, _event: tk.Event | None = None) -> None:
        self._populate_list(self.search_var.get())

    def _populate_list(self, query: str) -> None:
        labels = filter_label_strings(query, self.assignments)
        self.listbox.delete(0, tk.END)
        for label in labels:
            self.listbox.insert(tk.END, label)
        if labels:
            self.listbox.selection_clear(0, tk.END)
            self.listbox.selection_set(0)
            self.listbox.activate(0)

    def _current_drawable(self) -> str | None:
        if self.index >= len(self.drawables):
            return None
        return self.drawables[self.index]

    def _advance_index(self) -> None:
        while self.index < len(self.drawables):
            drawable = self.drawables[self.index]
            if drawable not in self.assignments:
                return
            self.index += 1

    def _refresh_view(self, focus_search: bool = False) -> None:
        self._advance_index()
        drawable = self._current_drawable()
        done = len(self.assignments)
        total = len(self.drawables)
        left = len(remaining_ids(self.assignments))
        self.progress_var.set(
            f"Assigned {done}/{total} · {left} unused MS labels · saves to {MAPPING_JSON.name}"
        )

        if drawable is None:
            self.title_var.set("All drawables assigned")
            self.meta_var.set("Run: python3 export_icon_tables.py all")
            self.status_var.set("")
            self.search_entry.configure(state="disabled")
            self.listbox.delete(0, tk.END)
            blank = Image.new("RGBA", (ICON_DISPLAY_PX, ICON_DISPLAY_PX), (*BG_RGB, 255))
            self._photo = ImageTk.PhotoImage(blank)
            self.icon_label.configure(image=self._photo)
            return

        input_type, preset, type_name = drawable_meta(drawable)
        icon_path = FLOW_ASSETS / f"{drawable}.png"
        display = load_display_image(icon_path)
        self._photo = ImageTk.PhotoImage(display)
        self.icon_label.configure(image=self._photo)

        self.title_var.set(drawable)
        self.meta_var.set(f"Input type {input_type} ({type_name}) · preset {preset}")
        if left == 0:
            self.status_var.set("No unused labels left — undo a previous assignment or quit.")
        else:
            self.status_var.set("Type to filter the list, pick a label, press Enter or Assign.")

        self.search_var.set("")
        self._populate_list("")
        if focus_search:
            self.search_entry.focus_set()

    def _assign_selected(self) -> None:
        drawable = self._current_drawable()
        if drawable is None:
            return
        selection = self.listbox.curselection()
        text = self.listbox.get(selection[0]) if selection else self.search_var.get()
        icon_id = parse_label_choice(text, self.assignments)
        if icon_id is None:
            self.status_var.set("Pick exactly one unused label from the list.")
            return
        self.assignments[drawable] = icon_id
        self.history.append(drawable)
        save_mapping(self.assignments)
        self.index += 1
        self._refresh_view(focus_search=True)

    def _skip(self) -> None:
        if self._current_drawable() is None:
            return
        self.index += 1
        self._refresh_view(focus_search=True)

    def _undo(self) -> None:
        if not self.history:
            self.status_var.set("Nothing to undo.")
            return
        last = self.history.pop()
        self.assignments.pop(last, None)
        save_mapping(self.assignments)
        self.index = self.drawables.index(last)
        self.status_var.set(f"Undid {last}")
        self._refresh_view(focus_search=True)

    def _quit(self) -> None:
        save_mapping(self.assignments)
        self.root.destroy()

    def run(self) -> None:
        self.root.mainloop()
        done = len(self.assignments)
        total = len(self.drawables)
        print(f"Saved {MAPPING_JSON} ({done}/{total} assigned).")


def run_terminal(start_at: str | None, reset: bool) -> None:
    drawables = flow_drawables()
    assignments: dict[str, int] = {} if reset else load_mapping()
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

    print("FLOW 8 → Mixing Station icon assignment (terminal + chafa)")
    print(f"Mapping file: {MAPPING_JSON}")

    readline.parse_and_bind("tab: complete")
    index = start_index
    while index < len(drawables):
        drawable = drawables[index]
        if drawable in assignments and not reset:
            index += 1
            continue

        input_type, preset, type_name = drawable_meta(drawable)
        icon_path = FLOW_ASSETS / f"{drawable}.png"
        print()
        print("=" * 72)
        print(f"{drawable}  ·  type {input_type} ({type_name})  ·  preset {preset}")
        show_icon_chafa(icon_path)
        readline.set_completer(LabelCompleter(assignments).complete)

        while True:
            try:
                raw = input("MS label> ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                save_mapping(assignments)
                return

            if not raw:
                suggestions = filter_label_strings("", assignments)[:8]
                if suggestions:
                    print("Suggestions:", ", ".join(suggestions))
                continue
            cmd = raw.casefold()
            if cmd in {"q", "quit", "exit"}:
                save_mapping(assignments)
                return
            if cmd in {"s", "skip"}:
                index += 1
                break
            if cmd in {"u", "undo"}:
                if history:
                    last = history.pop()
                    assignments.pop(last, None)
                    save_mapping(assignments)
                    index = drawables.index(last)
                    print(f"Undid {last}")
                break

            icon_id = parse_label_choice(raw, assignments)
            if icon_id is None:
                suggestions = filter_label_strings(raw, assignments)[:8]
                if suggestions:
                    print("Did you mean:")
                    for suggestion in suggestions:
                        print(f"  {suggestion}")
                continue

            assignments[drawable] = icon_id
            history.append(drawable)
            save_mapping(assignments)
            print(f"→ {label_with_id(icon_id)}")
            index += 1
            break

    save_mapping(assignments)
    print(f"Saved {MAPPING_JSON} ({len(assignments)}/{len(drawables)} assigned).")


def run_gui(start_at: str | None, reset: bool) -> None:
    if ImageTk is None:
        raise SystemExit(
            "Pillow ImageTk unavailable — install python3-tk (e.g. apt install python3-tk), "
            "or use --terminal with chafa."
        )
    if not os.environ.get("DISPLAY"):
        raise SystemExit(
            "No DISPLAY set — run on a machine with a graphical desktop, or use --terminal with chafa."
        )
    app = AssignFlow8Gui(start_at, reset)
    app.run()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--start", metavar="DRAWABLE", help="Start at drawable, e.g. input_icon_012")
    parser.add_argument("--reset", action="store_true", help="Discard existing mapping and start over")
    parser.add_argument(
        "--terminal",
        action="store_true",
        help="Terminal UI with inline chafa preview (no GUI window)",
    )
    args = parser.parse_args()
    if not FLOW_ASSETS.is_dir():
        raise SystemExit(
            f"Flow assets missing at {FLOW_ASSETS}\nRun: python3 extract_icon_assets.py"
        )
    if args.terminal:
        run_terminal(args.start, args.reset)
    else:
        run_gui(args.start, args.reset)


if __name__ == "__main__":
    main()
