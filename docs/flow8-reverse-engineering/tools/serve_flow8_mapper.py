#!/usr/bin/env python3
"""Serve a browser UI to map FLOW 8 drawables → labels.

    cd docs/flow8-reverse-engineering/tools
    python3 serve_flow8_mapper.py
    # open http://127.0.0.1:8765/

Saves to ``flow8_icon_mapping.json``. Then::

    python3 export_icon_tables.py all
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import sys
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

TOOLS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS_DIR))

from flow8_mapping import (  # noqa: E402
    DOCS_DIR,
    FLOW_ASSETS,
    app_drawable_order,
    assign,
    clear_assignment,
    current_drawable,
    drawable_meta,
    jump_to,
    label_catalog,
    load_picker_hints,
    load_state,
    save_state,
    skip_to_end,
)

HTML_FILE = TOOLS_DIR / "flow8_mapper.html"
ASSETS_ROOT = DOCS_DIR / "mixer-icons" / "assets"


class MapperHandler(BaseHTTPRequestHandler):
    server_version = "Flow8Mapper/1.0"

    def log_message(self, fmt: str, *args) -> None:
        if self.path.startswith("/api/"):
            super().log_message(fmt, *args)

    def _send_json(self, code: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        return json.loads(raw.decode("utf-8") or "{}")

    def _state_payload(self) -> dict:
        state = load_state()
        order = app_drawable_order()
        used_labels: dict[str, list[str]] = {}
        for drawable, entry in state.assignments.items():
            used_labels.setdefault(entry.label, []).append(drawable)

        hints = load_picker_hints()
        drawables = []
        for drawable in order:
            meta = drawable_meta(drawable)
            entry = state.assignments.get(drawable)
            drawables.append(
                {
                    **meta,
                    "assigned": entry is not None,
                    "label": entry.label if entry else None,
                    "ms_id": entry.ms_id if entry else None,
                    "flow_slug": entry.flow_slug if entry else None,
                    "hint": hints.get(drawable),
                }
            )

        current = current_drawable(state)
        current_hint = hints.get(current) if current else None
        return {
            "version": state.version,
            "total": len(order),
            "assigned_count": len(state.assignments),
            "queue_length": len(state.queue),
            "current": current,
            "current_meta": drawable_meta(current) if current else None,
            "current_hint": current_hint,
            "queue": state.queue,
            "drawables": drawables,
            "labels": label_catalog(),
            "duplicate_labels": {
                label: ids for label, ids in used_labels.items() if len(ids) > 1
            },
        }

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path in ("/", "/index.html"):
            body = HTML_FILE.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if path == "/api/state":
            self._send_json(200, self._state_payload())
            return
        if path.startswith("/assets/"):
            rel = unquote(path.removeprefix("/assets/"))
            file_path = (ASSETS_ROOT / rel).resolve()
            if not str(file_path).startswith(str(ASSETS_ROOT.resolve())) or not file_path.is_file():
                self.send_error(404)
                return
            data = file_path.read_bytes()
            mime, _ = mimetypes.guess_type(str(file_path))
            self.send_response(200)
            self.send_header("Content-Type", mime or "application/octet-stream")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        self.send_error(404)

    def do_POST(self) -> None:
        path = urlparse(self.path).path
        data = self._read_json()
        state = load_state()

        try:
            if path == "/api/assign":
                drawable = str(data["drawable"])
                label = str(data["label"])
                assign(state, drawable, label)
                save_state(state)
                self._send_json(200, {"ok": True, **self._state_payload()})
                return
            if path == "/api/clear":
                drawable = str(data["drawable"])
                clear_assignment(state, drawable)
                save_state(state)
                self._send_json(200, {"ok": True, **self._state_payload()})
                return
            if path == "/api/skip":
                drawable = str(data.get("drawable") or (state.queue[0] if state.queue else ""))
                if drawable:
                    skip_to_end(state, drawable)
                    save_state(state)
                self._send_json(200, {"ok": True, **self._state_payload()})
                return
            if path == "/api/jump":
                drawable = str(data["drawable"])
                jump_to(state, drawable)
                save_state(state)
                self._send_json(200, {"ok": True, **self._state_payload()})
                return
        except (KeyError, ValueError) as exc:
            self._send_json(400, {"ok": False, "error": str(exc)})
            return

        self.send_error(404)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-open", action="store_true")
    args = parser.parse_args()

    if not FLOW_ASSETS.is_dir():
        raise SystemExit(f"Missing assets at {FLOW_ASSETS}\nRun: python3 extract_icon_assets.py")
    if not HTML_FILE.is_file():
        raise SystemExit(f"Missing {HTML_FILE}")

    # Migrate v1 → v2 on startup if needed.
    save_state(load_state())

    url = f"http://{args.host}:{args.port}/"
    httpd = ThreadingHTTPServer((args.host, args.port), MapperHandler)
    print(f"FLOW 8 mapper → {url}")
    print(f"Mapping file: {TOOLS_DIR / 'flow8_icon_mapping.json'}")
    if not args.no_open:
        webbrowser.open(url)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
