#!/usr/bin/env python3
"""
Discover XR18 on LAN and print name + color for each USB audio channel (1–18).

Resolves /routing/usb/NN/src to the correct OSC strip (input, bus, fx, main LR,
aux playback, etc.). XR18 has 16 input strips (/ch/01–16); USB 17–18 are AuxL/R
(playback/aux path uses /rtn/aux).
"""

from __future__ import annotations

import argparse
import socket
import struct
import sys
import time

PORT = 10024
USB_CHANNELS = 18
COLORS = "OFF RD GN YE BL MG CY WH OFFi RDi GNi YEi BLi MGi CYi WHi".split()

# /routing/usb/NN/src indices (X-Air OSC wiki)
USB_ROUTE_SRC = (
    [f"Ch{i:02d}" for i in range(1, 17)]
    + ["AuxL", "AuxR"]
    + [f"Fx{i}L" for i in range(1, 5)]
    + [f"Fx{i}R" for i in range(1, 5)]
    + [f"Bus{i}" for i in range(1, 7)]
    + [f"Send{i}" for i in range(1, 5)]
    + ["L", "R"]
)


def _pad(s: str) -> bytes:
    b = s.encode() + b"\x00"
    return b + b"\x00" * ((4 - len(b) % 4) % 4)


def _decode(data: bytes) -> tuple[str, list]:
    def read(off: int) -> tuple[str, int]:
        end = data.index(0, off)
        return data[off:end].decode(), ((end + 1) + 3) & ~3

    path, off = read(0)
    if off >= len(data):
        return path, []
    tags, off = read(off)
    args: list = []
    for tag in tags[1:]:
        if tag == "i":
            args.append(struct.unpack(">i", data[off : off + 4])[0])
            off += 4
        elif tag == "s":
            val, off = read(off)
            args.append(val)
    return path, args


def discover(timeout: float = 3.0) -> str | None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.settimeout(0.5)
    sock.bind(("", 0))
    sock.sendto(_pad("/xinfo"), ("<broadcast>", PORT))
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            data, (ip, _) = sock.recvfrom(4096)
        except socket.timeout:
            continue
        path, args = _decode(data)
        if path == "/xinfo" and args:
            return ip
    return None


def query_osc(ip: str, paths: list[str], rounds: int = 4) -> dict[str, list]:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("", 0))
    sock.settimeout(0.25)
    addr = (ip, PORT)
    pending = set(paths)
    replies: dict[str, list] = {}
    for _ in range(rounds):
        if not pending:
            break
        for path in pending:
            sock.sendto(_pad(path), addr)
        deadline = time.monotonic() + 2.5
        while time.monotonic() < deadline and pending:
            try:
                data, _ = sock.recvfrom(4096)
            except socket.timeout:
                break
            path, args = _decode(data)
            if path in pending and args:
                replies[path] = args
                pending.discard(path)
    return replies


def _collect_label_paths() -> list[str]:
    paths: list[str] = []
    for i in range(1, 17):
        nn = f"{i:02d}"
        paths += [f"/ch/{nn}/config/name", f"/ch/{nn}/config/color"]
    for i in range(1, 7):
        paths += [f"/bus/{i}/config/name", f"/bus/{i}/config/color"]
    for i in range(1, 5):
        paths += [
            f"/fxsend/{i}/config/name",
            f"/fxsend/{i}/config/color",
            f"/rtn/{i}/config/name",
            f"/rtn/{i}/config/color",
        ]
    paths += [
        "/rtn/aux/config/name",
        "/rtn/aux/config/color",
        "/lr/config/name",
        "/lr/config/color",
    ]
    for n in range(1, USB_CHANNELS + 1):
        paths.append(f"/routing/usb/{n:02d}/src")
    return paths


def _get_label(replies: dict[str, list], base: str) -> tuple[str | None, int | None]:
    name = (replies.get(f"{base}/name") or [None])[0]
    color = (replies.get(f"{base}/color") or [None])[0]
    return name, color


def _resolve_src(replies: dict[str, list], src: int) -> tuple[str, str | None, int | None]:
    """Map routing source index to (source label, name, color)."""
    if not (0 <= src < len(USB_ROUTE_SRC)):
        return ("?", None, None)

    label = USB_ROUTE_SRC[src]

    if label.startswith("Ch"):
        ch = int(label[2:])
        base = f"/ch/{ch:02d}/config"
        name, color = _get_label(replies, base)
        return (label, name, color)

    if label in ("AuxL", "AuxR"):
        name, color = _get_label(replies, "/rtn/aux/config")
        side = "L" if label == "AuxL" else "R"
        if name:
            name = f"{name} ({side})"
        return (label, name, color)

    if label[0:2] == "Fx" and label[-1] in "LR":
        n = int(label[2])
        base = f"/rtn/{n}/config"
        name, color = _get_label(replies, base)
        if name and label.endswith("R"):
            name = f"{name} (R)"
        elif name and label.endswith("L"):
            name = f"{name} (L)"
        return (label, name, color)

    if label.startswith("Bus"):
        n = int(label[3:])
        base = f"/bus/{n}/config"
        name, color = _get_label(replies, base)
        return (label, name, color)

    if label.startswith("Send"):
        n = int(label[4:])
        base = f"/fxsend/{n}/config"
        name, color = _get_label(replies, base)
        return (label, name, color)

    if label in ("L", "R"):
        name, color = _get_label(replies, "/lr/config")
        if name:
            name = f"{name} ({label})"
        return (label, name, color)

    return (label, None, None)


def fetch_usb_labels(ip: str) -> list[dict]:
    replies = query_osc(ip, _collect_label_paths())
    rows: list[dict] = []
    for usb in range(1, USB_CHANNELS + 1):
        src = (replies.get(f"/routing/usb/{usb:02d}/src") or [None])[0]
        source, name, color = _resolve_src(replies, src) if src is not None else ("?", None, None)
        rows.append(
            {
                "usb": usb,
                "src_index": src,
                "source": source,
                "name": name,
                "color": color,
            }
        )
    return rows


def _color_label(index: int | None) -> str:
    if index is None:
        return "?"
    if 0 <= index < len(COLORS):
        return COLORS[index]
    return str(index)


def main() -> int:
    p = argparse.ArgumentParser(
        description="Print XR18 USB channel names and colors (via routing + OSC)"
    )
    p.add_argument("--ip", help="Mixer IP (default: auto-discover via /xinfo)")
    args = p.parse_args()

    ip = args.ip or discover()
    if not ip:
        print("No XR18 found. Try --ip 192.168.x.x", file=sys.stderr)
        return 1

    print(f"Mixer: {ip}\n")
    rows = fetch_usb_labels(ip)

    print(f"{'USB':>3}  {'Source':<8}  {'Name':<24}  {'Col':>3}  Color")
    print("-" * 52)
    for row in rows:
        name = row["name"] or "?"
        color = row["color"]
        c = "" if color is None else str(color)
        print(
            f"{row['usb']:3d}  {row['source']:<8}  {name:<24}  {c:>3}  {_color_label(color)}"
        )

    print(
        "\nEach row is one USB audio channel on the 18×18 interface."
    )
    print("Source = mixer signal routed to that USB output (/routing/usb/NN/src).")
    print("USB 1–16 → input strips Ch01–Ch16; USB 17–18 → AuxL/R (/rtn/aux playback).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
