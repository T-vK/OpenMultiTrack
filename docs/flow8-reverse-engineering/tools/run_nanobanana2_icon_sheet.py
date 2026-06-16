#!/usr/bin/env python3
"""Run Nano Banana 2 Edit on the icon sprite sheet via RunPod.

Reads API key from ``docs/mixer-icons/.runpod-api-key`` or ``RUNPOD_API_KEY``.
Saves output under ``docs/mixer-icons/generated/``.

Example::

    python3 run_nanobanana2_icon_sheet.py --resolution 2k
    python3 run_nanobanana2_icon_sheet.py --resolution 4k --async
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
DOCS_DIR = TOOLS_DIR.parent.parent
GENERATED = DOCS_DIR / "mixer-icons" / "generated"
DEFAULT_INPUT = GENERATED / "icon_sprite_sheet.png"
API_KEY_FILE = DOCS_DIR / "mixer-icons" / ".runpod-api-key"
ENDPOINT = "google-nano-banana-2-edit"

PROMPT = """Restyle this contact sheet into one unified premium mobile app icon pack.

Preserve exactly: 15x15 grid, 202 icons, same cell positions, same order, same subjects per cell. Each cell has icon on top and small text label underneath.

Transform every icon into the SAME cohesive art direction: modern premium 3D comic UI icons for a professional audio mixer app.

Style: soft 3D rounded volumetric forms, subtle bevel, gentle ambient occlusion, bold comic silhouettes, friendly but professional, satin materials, soft gradient shading, studio lighting from top-left, 3-5 colors per icon, dark UI-ready backgrounds.

Critical: each icon must remain instantly recognizable at 32x32px - one dominant subject, large in frame, thick readable shapes, high contrast, no micro-detail, no thin hairlines.

Keep semantic meaning of each source icon. Mixer brand cells: stylized 3D product thumbnails of the actual mixer hardware.

Do not add, remove, merge, reorder, or crop icons. Keep grid alignment pixel-sharp."""


def api_key() -> str:
    env = os.environ.get("RUNPOD_API_KEY")
    if env:
        return env.strip()
    if API_KEY_FILE.is_file():
        return API_KEY_FILE.read_text(encoding="utf-8").strip()
    raise SystemExit("Set RUNPOD_API_KEY or create docs/mixer-icons/.runpod-api-key")


def post_json(url: str, body: dict, key: str) -> dict:
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=300) as resp:
        return json.loads(resp.read().decode())


def get_json(url: str, key: str) -> dict:
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {key}"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode())


def image_data_uri(path: Path) -> str:
    encoded = base64.b64encode(path.read_bytes()).decode()
    return f"data:image/png;base64,{encoded}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--resolution", choices=("1k", "2k", "4k"), default="2k")
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--prompt", default=PROMPT)
    parser.add_argument("--async", dest="use_async", action="store_true")
    parser.add_argument("--poll-interval", type=float, default=10.0)
    args = parser.parse_args()

    if not args.input.is_file():
        raise SystemExit(f"Input not found: {args.input}. Run make_contact_sheet.py first.")

    key = api_key()
    body = {
        "input": {
            "images": [image_data_uri(args.input)],
            "prompt": args.prompt,
            "resolution": args.resolution,
            "aspect_ratio": "1:1",
            "output_format": "png",
            "enable_safety_checker": True,
        }
    }

    mode = "run" if args.use_async else "runsync"
    base = f"https://api.runpod.ai/v2/{ENDPOINT}/{mode}"
    print(f"Submitting {args.input.name} at {args.resolution} via {mode}…", file=sys.stderr)

    try:
        result = post_json(base, body, key)
    except urllib.error.HTTPError as exc:
        print(exc.read().decode(), file=sys.stderr)
        raise SystemExit(1) from exc

    if args.use_async:
        job_id = result["id"]
        print(f"Job {job_id} queued", file=sys.stderr)
        while True:
            status = get_json(f"https://api.runpod.ai/v2/{ENDPOINT}/status/{job_id}", key)
            state = status.get("status")
            print(f"  {state}", file=sys.stderr)
            if state == "COMPLETED":
                result = status
                break
            if state == "FAILED":
                raise SystemExit(json.dumps(status, indent=2))
            time.sleep(args.poll_interval)

    output = result.get("output") or {}
    image_url = output.get("result") or output.get("image_url")
    if not image_url:
        raise SystemExit(json.dumps(result, indent=2))

    out = args.output or GENERATED / f"icon_sprite_sheet_nanobanana2_{args.resolution}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve(image_url, out)
    print(json.dumps({"output": str(out), "image_url": image_url, "cost": output.get("cost")}, indent=2))


if __name__ == "__main__":
    main()
