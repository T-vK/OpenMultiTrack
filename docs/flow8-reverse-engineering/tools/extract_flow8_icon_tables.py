#!/usr/bin/env python3
"""Extract FLOW 8 (input_type, preset) → MS icon tables from Flow Mix on a device.

Requires Flow Mix (``com.musicgroup.xairbt``) installed and ``adb`` access.

Usage:
  ./extract_flow8_icon_tables.py
  ./extract_flow8_icon_tables.py --serial 192.168.3.42:46003
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
EXTRACTOR_JAVA = TOOLS_DIR / "Flow8IconTableExtractor.java"
OUTPUT_JSON = TOOLS_DIR / "flow8_icon_tables.json"
FLOW_APK = TOOLS_DIR.parent / "Flowmix_v1.9.apk"
FLOW_PACKAGE = "com.musicgroup.xairbt"


def run(cmd: list[str], **kwargs) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(cmd), file=sys.stderr)
    return subprocess.run(cmd, check=True, text=True, capture_output=True, **kwargs)


def adb_base(serial: str | None) -> list[str]:
    cmd = ["adb"]
    if serial:
        cmd.extend(["-s", serial])
    return cmd


def compile_extractor() -> Path:
    build_dir = TOOLS_DIR / ".extractor-build"
    build_dir.mkdir(exist_ok=True)
    dex = build_dir / "Flow8IconTableExtractor.dex"
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
    classes = build_dir / "Flow8IconTableExtractor.class"
    run(["d8", "--output", str(build_dir), str(classes)])
    return dex


def flow_apk_path(serial: str | None) -> str:
    out = run(adb_base(serial) + ["shell", "pm", "path", FLOW_PACKAGE]).stdout.strip()
    if not out.startswith("package:"):
        raise SystemExit(f"{FLOW_PACKAGE} is not installed on the device")
    return out.split(":", 1)[1].strip()


def extract(serial: str | None) -> str:
    dex = compile_extractor()
    remote_dex = "/data/local/tmp/Flow8IconTableExtractor.dex"
    apk = flow_apk_path(serial)
    run(adb_base(serial) + ["push", str(dex), remote_dex])
    cmd = adb_base(serial) + [
        "shell",
        f"CLASSPATH={remote_dex}:{apk}",
        "app_process",
        "/",
        "Flow8IconTableExtractor",
    ]
    return run(cmd).stdout


def parse_output(text: str) -> dict:
    presets: list[dict] = []
    tables: dict[int, list[int | None]] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("type ") and "count" in line:
            continue
        parts = line.split(",")
        if len(parts) < 3:
            continue
        input_type = int(parts[0])
        preset = int(parts[1])
        ms_id = int(parts[2])
        name = parts[3] if len(parts) > 3 else ""
        label = parts[4] if len(parts) > 4 else ""
        presets.append(
            {
                "input_type": input_type,
                "preset": preset,
                "ms_id": ms_id,
                "name": name,
                "label": label,
            }
        )
        tables.setdefault(input_type, [])
        while len(tables[input_type]) <= preset:
            tables[input_type].append(None)
        tables[input_type][preset] = ms_id
    return {"presets": presets, "tables": {str(k): v for k, v in tables.items()}}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="adb serial (e.g. wireless host:port)")
    parser.add_argument("--output", type=Path, default=OUTPUT_JSON)
    args = parser.parse_args()
    text = extract(args.serial)
    print(text)
    data = parse_output(text)
    args.output.write_text(json.dumps(data, indent=2), encoding="utf-8")
    print(f"Wrote {args.output} ({len(data['presets'])} presets)", file=sys.stderr)


if __name__ == "__main__":
    main()
