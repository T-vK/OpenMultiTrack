#!/usr/bin/env python3
"""Dump FLOW Mix preset name/description strings from the native library (via adb).

Requires Flow Mix installed on a connected device/emulator::

    python3 extract_flow8_apk_labels.py
    python3 extract_flow8_apk_labels.py --serial 192.168.3.42:46003

Writes ``flow8_apk_labels.json`` (slot hints) and prints CSV to stdout.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
EXTRACTOR_JAVA = TOOLS_DIR / "Flow8ApkLabelExtractor.java"
OUTPUT_JSON = TOOLS_DIR / "flow8_apk_labels.json"
FLOW_PACKAGE = "com.musicgroup.xairbt"


def run(cmd: list[str]) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(cmd), file=sys.stderr)
    return subprocess.run(cmd, check=True, text=True, capture_output=True)


def adb_base(serial: str | None) -> list[str]:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    return cmd


def compile_extractor() -> Path:
    build_dir = TOOLS_DIR / ".extractor-build"
    build_dir.mkdir(exist_ok=True)
    dex = build_dir / "Flow8ApkLabelExtractor.dex"
    run(
        [
            "javac",
            "-source",
            "8",
            "-target",
            "8",
            "-d",
            str(build_dir),
            str(EXTRACTOR_JAVA),
        ]
    )
    classes = build_dir / "Flow8ApkLabelExtractor.class"
    run(["d8", "--output", str(build_dir), str(classes)])
    return dex


def install_apk(serial: str | None, apk_path: Path) -> None:
    if not apk_path.is_file():
        raise SystemExit(f"APK not found: {apk_path}")
    run(adb_base(serial) + ["install", "-r", str(apk_path)])


def flow_apk_path(serial: str | None, install_from: Path | None = None) -> str:
    if install_from is not None:
        install_apk(serial, install_from)
    out = run(adb_base(serial) + ["shell", "pm", "path", FLOW_PACKAGE]).stdout.strip()
    if not out.startswith("package:"):
        raise SystemExit(
            f"{FLOW_PACKAGE} is not installed on the device. "
            "Pass --apk /path/to/Flowmix.apk or install Flow Mix manually."
        )
    return out.split(":", 1)[1].strip()


def extract(serial: str | None, install_from: Path | None = None) -> str:
    dex = compile_extractor()
    remote_dex = "/data/local/tmp/Flow8ApkLabelExtractor.dex"
    apk = flow_apk_path(serial, install_from)
    run(adb_base(serial) + ["push", str(dex), remote_dex])
    cmd = adb_base(serial) + [
        "shell",
        f"CLASSPATH={remote_dex}:{apk}",
        "app_process",
        "/",
        "Flow8ApkLabelExtractor",
    ]
    return run(cmd).stdout


def parse_rows(text: str) -> list[dict]:
    rows: list[dict] = []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 5:
            continue
        input_type = int(parts[0])
        preset = int(parts[1])
        drawable = parts[2]
        name = parts[3]
        description = parts[4] if len(parts) > 4 else ""
        rows.append(
            {
                "input_type": input_type,
                "preset": preset,
                "drawable": drawable,
                "name": name,
                "description": description,
            }
        )
    return rows


def merge_labels(existing: dict, rows: list[dict]) -> dict:
    labels = {entry["slug"]: entry for entry in existing.get("labels", []) if "slug" in entry}
    if isinstance(existing.get("labels"), dict):
        for slug, label in existing["labels"].items():
            labels[slug] = {"slug": slug, "label": label, "group": "APK / FLOW artwork"}

    seen_labels = {entry["label"].casefold() for entry in labels.values()}

    for row in rows:
        for field in ("name", "description"):
            text = (row.get(field) or "").strip()
            if not text or text.casefold() in seen_labels:
                continue
            from flow8_apk_labels import slugify

            slug = slugify(text)
            base = slug
            n = 2
            while slug in labels and labels[slug]["label"] != text:
                slug = f"{base}-{n}"
                n += 1
            labels[slug] = {
                "slug": slug,
                "label": text,
                "group": "JNI preset name" if field == "name" else "JNI description",
            }
            seen_labels.add(text.casefold())

    hints = existing.get("hints", {})
    for row in rows:
        drawable = row["drawable"]
        if row["name"] and drawable not in hints:
            hints[drawable] = row["name"]

    return {
        "version": 1,
        "source": "extract_flow8_apk_labels.py (JNI on device)",
        "labels": sorted(labels.values(), key=lambda e: e["label"].casefold()),
        "hints": dict(sorted(hints.items())),
        "slots": rows,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="adb serial")
    parser.add_argument(
        "--apk",
        type=Path,
        help="Install this Flow Mix APK before extracting (device required)",
    )
    parser.add_argument("--output", type=Path, default=OUTPUT_JSON)
    args = parser.parse_args()

    text = extract(args.serial, args.apk)
    print(text)
    rows = parse_rows(text)
    existing = json.loads(args.output.read_text(encoding="utf-8")) if args.output.is_file() else {}
    payload = merge_labels(existing, rows)
    args.output.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote {args.output} ({len(payload['labels'])} labels, {len(rows)} slots)", file=sys.stderr)


if __name__ == "__main__":
    main()
