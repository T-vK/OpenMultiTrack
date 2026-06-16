#!/usr/bin/env python3
"""Extract scribble icon assets for docs/mixer-icons.md.

- Mixing Station / X32 ids 1–74: SVG from behringer-icons (community pack, same numbering).
- FLOW 8 picker: PNG from Flowmix_v1.9.apk drawable (xxhdpi), resized for docs.

Requires: apktool JAR (auto-downloaded), Pillow, Flowmix APK in flow8-reverse-engineering/.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

from PIL import Image

TOOLS_DIR = Path(__file__).resolve().parent
DOCS_DIR = TOOLS_DIR.parent.parent
REPO_ROOT = DOCS_DIR.parent
ASSETS_DIR = DOCS_DIR / "mixer-icons" / "assets"
FLOW_APK = TOOLS_DIR.parent / "Flowmix_v1.9.apk"
APKTOOL_URL = "https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar"
APKTOOL_JAR = TOOLS_DIR / ".apktool.jar"
BEHRINGER_ICONS_URL = (
    "https://codeload.github.com/mamarguerat/behringer-icons/zip/refs/heads/main"
)
BEHRINGER_ZIP = TOOLS_DIR / ".behringer-icons.zip"
ICON_SIZE = 64


def flow_drawable_key(input_type: int, preset: int) -> str:
    return f"input_icon_{input_type * 100 + preset:03d}"


# Preset slot counts (Flowmix_v1.9.apk dex strings).
PRESET_COUNTS_LOCAL = {
    0: 15,
    1: 11,
    2: 18,
    3: 18,
    4: 8,
    5: 12,
}


def ensure_apktool() -> Path:
    if APKTOOL_JAR.is_file():
        return APKTOOL_JAR
    print(f"Downloading apktool → {APKTOOL_JAR}", file=sys.stderr)
    download(APKTOOL_URL, APKTOOL_JAR)
    return APKTOOL_JAR


def decode_flow_apk() -> Path:
    if not FLOW_APK.is_file():
        raise SystemExit(f"Flow Mix APK not found: {FLOW_APK}")
    apktool = ensure_apktool()
    tmp = Path(tempfile.mkdtemp(prefix="flowmix-decode-"))
    subprocess.run(
        ["java", "-jar", str(apktool), "d", "-f", "-o", str(tmp), str(FLOW_APK)],
        check=True,
        capture_output=True,
    )
    return tmp


def pick_flow_png(decoded: Path, drawable: str) -> Path | None:
    for sub in ("drawable-xxhdpi", "drawable-xhdpi", "drawable-hdpi", "drawable-mdpi"):
        candidate = decoded / "res" / sub / f"{drawable}.png"
        if candidate.is_file():
            return candidate
    return None


def resize_png(src: Path, dest: Path, size: int = ICON_SIZE) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(src) as img:
        img = img.convert("RGBA")
        img.thumbnail((size, size), Image.Resampling.LANCZOS)
        # centre on square canvas
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        offset = ((size - img.width) // 2, (size - img.height) // 2)
        canvas.paste(img, offset, img)
        canvas.save(dest, format="PNG", optimize=True)


def download(url: str, dest: Path) -> None:
    req = urllib.request.Request(url, headers={"User-Agent": "OpenMultiTrack-docs/1.0"})
    with urllib.request.urlopen(req) as resp, dest.open("wb") as out:
        shutil.copyfileobj(resp, out)


def ensure_behringer_svgs(dest_dir: Path) -> None:
    ms_dir = dest_dir / "mixing-station"
    ms_dir.mkdir(parents=True, exist_ok=True)
    if all((ms_dir / f"{i}.svg").is_file() for i in range(1, 75)):
        return
    local = Path("/tmp/behringer-icons/svg")
    if local.is_dir() and all((local / f"{i}.svg").is_file() for i in range(1, 75)):
        print("Copying SVGs from /tmp/behringer-icons…", file=sys.stderr)
        for icon_id in range(1, 75):
            shutil.copy2(local / f"{icon_id}.svg", ms_dir / f"{icon_id}.svg")
        return
    print("Fetching behringer-icons SVG pack…", file=sys.stderr)
    download(BEHRINGER_ICONS_URL, BEHRINGER_ZIP)
    with zipfile.ZipFile(BEHRINGER_ZIP) as zf:
        prefix = None
        for name in zf.namelist():
            if name.endswith("/svg/1.svg"):
                prefix = name[: -len("svg/1.svg")]
                break
        if prefix is None:
            raise SystemExit("Could not find svg/ in behringer-icons zip")
        for icon_id in range(1, 75):
            member = f"{prefix}svg/{icon_id}.svg"
            out = ms_dir / f"{icon_id}.svg"
            with zf.open(member) as src, out.open("wb") as dst:
                shutil.copyfileobj(src, dst)
    BEHRINGER_ZIP.unlink(missing_ok=True)


def flow8_drawables() -> list[tuple[int, int, str]]:
    rows: list[tuple[int, int, str]] = []
    for input_type, count in PRESET_COUNTS_LOCAL.items():
        for preset in range(count):
            rows.append((input_type, preset, flow_drawable_key(input_type, preset)))
    return rows


def extract_flow8_icons(decoded: Path, dest_dir: Path) -> list[str]:
    flow_dir = dest_dir / "flow8"
    flow_dir.mkdir(parents=True, exist_ok=True)
    missing: list[str] = []
    for input_type, preset, drawable in flow8_drawables():
        src = pick_flow_png(decoded, drawable)
        dest = flow_dir / f"{drawable}.png"
        if src is None:
            missing.append(drawable)
            continue
        resize_png(src, dest)
    return missing


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--assets-dir",
        type=Path,
        default=ASSETS_DIR,
        help=f"Output directory (default: {ASSETS_DIR})",
    )
    args = parser.parse_args()
    assets = args.assets_dir.resolve()
    ensure_behringer_svgs(assets)
    decoded = decode_flow_apk()
    try:
        missing = extract_flow8_icons(decoded, assets)
    finally:
        shutil.rmtree(decoded, ignore_errors=True)
    if missing:
        print(f"Warning: {len(missing)} Flow drawable(s) not found in APK:", file=sys.stderr)
        for name in missing[:10]:
            print(f"  {name}", file=sys.stderr)
        if len(missing) > 10:
            print(f"  … and {len(missing) - 10} more", file=sys.stderr)
    print(f"Assets written to {assets}")


if __name__ == "__main__":
    main()
