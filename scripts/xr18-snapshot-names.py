#!/usr/bin/env python3
"""
Probe XR18 snapshot names over UDP OSC (port 10024).

Usage:
  ./scripts/xr18-snapshot-names.py --host 192.168.3.63
  ./scripts/xr18-snapshot-names.py --discover
  ./scripts/xr18-snapshot-names.py --host 192.168.3.63 --slot 1 --expect "01 HomeSnapshot1"

Success criterion for slot 1 on this desk: name == "01 HomeSnapshot1".
"""

from __future__ import annotations

import argparse
import ipaddress
import socket
import struct
import sys
import time
from dataclasses import dataclass

XR18_PORT = 10024
PLACEHOLDER_NAMES = {"", "-", "—", "_"}


def align4(n: int) -> int:
    return (n + 3) & ~3


def encode_osc_message(path: str, args: list[tuple[str, object]] | None = None) -> bytes:
    out = bytearray()
    path_bytes = path.encode("utf-8")
    out += path_bytes
    out.append(0)
    out += b"\x00" * ((align4(len(path_bytes) + 1) - len(path_bytes) - 1))
    if not args:
        return bytes(out)
    tags = ","
    for tag, _ in args:
        tags += tag
    tag_bytes = tags.encode("utf-8")
    out += tag_bytes
    out.append(0)
    out += b"\x00" * ((align4(len(tag_bytes) + 1) - len(tag_bytes) - 1))
    for tag, value in args:
        if tag == "i":
            out += struct.pack(">i", int(value))
        elif tag == "f":
            out += struct.pack(">f", float(value))
        elif tag == "s":
            s = str(value).encode("utf-8")
            out += s
            out.append(0)
            out += b"\x00" * ((align4(len(s) + 1) - len(s) - 1))
        else:
            raise ValueError(f"unsupported OSC tag: {tag}")
    return bytes(out)


def read_padded_string(data: bytes, offset: int) -> tuple[str, int]:
    end = data.index(0, offset)
    value = data[offset:end].decode("utf-8", errors="replace")
    return value, align4(end + 1)


def decode_osc_messages(data: bytes) -> list[tuple[str, list[object]]]:
    if not data:
        return []
    try:
        path_end = data.index(0)
    except ValueError:
        return []
    path = data[:path_end].decode("utf-8", errors="replace")
    if path == "#bundle":
        return decode_bundle(data, align4(path_end + 1))
    msg = decode_message(data, 0)
    return [msg] if msg else []


def decode_bundle(data: bytes, offset: int) -> list[tuple[str, list[object]]]:
    if offset + 8 > len(data):
        return []
    pos = offset + 8
    messages: list[tuple[str, list[object]]] = []
    while pos + 4 <= len(data):
        size = struct.unpack(">i", data[pos : pos + 4])[0]
        pos += 4
        if size <= 0 or pos + size > len(data):
            break
        msg = decode_message(data, pos)
        if msg:
            messages.append(msg)
        pos += size
    return messages


def decode_message(data: bytes, start: int) -> tuple[str, list[object]] | None:
    if start >= len(data):
        return None
    try:
        path_end = data.index(0, start)
    except ValueError:
        return None
    path = data[start:path_end].decode("utf-8", errors="replace")
    offset = align4(path_end + 1)
    if offset >= len(data):
        return path, []
    try:
        tags_end = data.index(0, offset)
    except ValueError:
        return path, []
    tags = data[offset:tags_end].decode("utf-8", errors="replace")
    offset = align4(tags_end + 1)
    if not tags.startswith(","):
        return path, []
    args: list[object] = []
    for tag in tags[1:]:
        if offset >= len(data):
            break
        if tag == "i":
            args.append(struct.unpack(">i", data[offset : offset + 4])[0])
            offset += 4
        elif tag == "f":
            args.append(struct.unpack(">f", data[offset : offset + 4])[0])
            offset += 4
        elif tag == "s":
            value, offset = read_padded_string(data, offset)
            args.append(value)
        else:
            break
    return path, args


def snap_slot_paths(slot: int) -> list[str]:
    pad = f"{slot:02d}"
    return [
        f"/-snap/{pad}/name/01",
        f"/-snap/{pad}/name",
    ]


def normalize_name(name: str) -> str:
    trimmed = name.strip()
    if trimmed in PLACEHOLDER_NAMES:
        return ""
    return trimmed


def match_pending_path(msg_path: str, pending: set[str]) -> str | None:
    if msg_path in pending:
        return msg_path
    if not msg_path.startswith("/-snap/") or "/name" not in msg_path:
        return None
    msg_slot = msg_path.split("/")[2]
    for pending_path in pending:
        if not pending_path.startswith("/-snap/"):
            continue
        if pending_path.split("/")[2] == msg_slot and "/name" in pending_path:
            return pending_path
    return None


@dataclass
class OscClient:
    host: str
    port: int = XR18_PORT
    timeout_s: float = 0.9

    def __post_init__(self) -> None:
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind(("", 0))
        self.sock.settimeout(self.timeout_s)
        self.addr = (self.host, self.port)

    def close(self) -> None:
        self.sock.close()

    def send(self, path: str, args: list[tuple[str, object]] | None = None) -> None:
        payload = encode_osc_message(path, args)
        self.sock.sendto(payload, self.addr)

    def query(self, paths: list[str], timeout_s: float | None = None, rounds: int = 3) -> dict[str, list[object]]:
        pending = set(paths)
        replies: dict[str, list[object]] = {}
        deadline = time.monotonic() + (timeout_s or self.timeout_s)
        for _ in range(rounds):
            if not pending:
                break
            for path in list(pending):
                self.send(path)
            while pending and time.monotonic() < deadline:
                remaining = max(0.02, deadline - time.monotonic())
                self.sock.settimeout(remaining)
                try:
                    data, _ = self.sock.recvfrom(4096)
                except socket.timeout:
                    break
                for path, args in decode_osc_messages(data):
                    matched = match_pending_path(path, pending)
                    if matched and args:
                        replies[matched] = args
                        pending.discard(matched)
        return replies

    def probe(self) -> bool:
        for path in ("/xinfo", "/info"):
            self.send(path)
            deadline = time.monotonic() + 1.0
            while time.monotonic() < deadline:
                self.sock.settimeout(max(0.02, deadline - time.monotonic()))
                try:
                    data, _ = self.sock.recvfrom(4096)
                except socket.timeout:
                    break
                for reply_path, _ in decode_osc_messages(data):
                    if reply_path in ("/xinfo", "/info"):
                        return True
        return False


def local_subnet_prefixes() -> list[str]:
    prefixes: list[str] = []
    for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
        ip = info[4][0]
        parts = ip.split(".")
        if len(parts) == 4:
            prefixes.append(".".join(parts[:3]))
    return sorted(set(prefixes))


def discover_mixer(timeout_s: float = 8.0) -> str | None:
    payload = encode_osc_message("/xinfo")
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("", 0))
    sock.settimeout(0.2)
    deadline = time.monotonic() + timeout_s
    for prefix in local_subnet_prefixes():
        for last in range(1, 255):
            if time.monotonic() >= deadline:
                break
            host = f"{prefix}.{last}"
            try:
                ipaddress.IPv4Address(host)
            except ipaddress.AddressValueError:
                continue
            try:
                sock.sendto(payload, (host, XR18_PORT))
                data, addr = sock.recvfrom(4096)
                for path, _ in decode_osc_messages(data):
                    if path in ("/xinfo", "/info"):
                        sock.close()
                        return addr[0]
            except socket.timeout:
                continue
            except OSError:
                continue
    sock.close()
    return None


def resolve_slot_name(client: OscClient, slot: int) -> str:
    replies = client.query(snap_slot_paths(slot), timeout_s=client.timeout_s, rounds=3)
    for path in snap_slot_paths(slot):
        args = replies.get(path)
        if not args:
            continue
        first = args[0]
        if isinstance(first, str):
            normalized = normalize_name(first)
            if normalized:
                return normalized
    return ""


def read_all_snapshot_names(client: OscClient, max_slot: int = 64) -> list[tuple[int, str]]:
    client.send("/xremote")
    time.sleep(0.05)
    out: list[tuple[int, str]] = []
    for slot in range(1, max_slot + 1):
        name = resolve_slot_name(client, slot)
        out.append((slot, name))
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description="Read XR18 snapshot names over OSC")
    parser.add_argument("--host", help="XR18 IP address")
    parser.add_argument("--discover", action="store_true", help="Scan local /24 subnets")
    parser.add_argument("--slot", type=int, default=1, help="Slot to highlight (default: 1)")
    parser.add_argument("--expect", default="HomeSnapshot1", help="Expected name for --slot")
    parser.add_argument("--all", action="store_true", help="Print every slot, including empty")
    parser.add_argument("--timeout", type=float, default=0.9, help="Per-slot query timeout seconds")
    args = parser.parse_args()

    host = args.host
    if args.discover or not host:
        print("Discovering XR18 on LAN…", flush=True)
        host = discover_mixer()
        if not host:
            print("ERROR: XR18 not found — pass --host <ip>", file=sys.stderr)
            return 1
        print(f"Found XR18 at {host}")

    client = OscClient(host=host, timeout_s=args.timeout)
    try:
        if not client.probe():
            print(f"ERROR: no OSC response from {host}:{XR18_PORT}", file=sys.stderr)
            return 1
        print(f"OSC probe OK on {host}:{XR18_PORT}")

        snapshots = read_all_snapshot_names(client)
        named = [(slot, name) for slot, name in snapshots if name]
        print(f"Named snapshots: {len(named)} / {len(snapshots)}")

        target = next((name for slot, name in snapshots if slot == args.slot), "")
        print(f"Slot {args.slot:02d}: {target!r}")

        if args.expect:
            if target == args.expect:
                print(f"PASS: slot {args.slot} matches expected {args.expect!r}")
            else:
                print(f"FAIL: expected {args.expect!r}, got {target!r}", file=sys.stderr)
                return 2

        if args.all:
            for slot, name in snapshots:
                label = name if name else "(empty)"
                print(f"  {slot:02d}: {label}")
        else:
            for slot, name in named:
                print(f"  {slot:02d}: {name}")
        return 0 if (not args.expect or target == args.expect) else 2
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
