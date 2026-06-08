#!/usr/bin/env python3
"""
Temporary probe: read XR18 scribble-strip channel names and colors over USB MIDI.

Protocol: Behringer OSC-over-MIDI SysEx (X32 / X-Air family)
  wire:  F0 00 20 32 32 <osc-command-ascii> F7
  OSC:   /ch/NN/config/name
         /ch/NN/config/color   (0-15 scribble color index)

USB only — does not open any network socket.

XR18 setup (X Air Edit -> Setup -> MIDI Config):
  - Enable USB Rx, USB Tx, and USB X/OSC
  - Disable USB-DIN Pass Thru (pass-thru bypasses the mixer entirely)

Delete this script when exploration is done.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import select
import struct
import subprocess
import sys
import time
from abc import ABC, abstractmethod
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterator

SYSEX_HEADER = bytes([0x00, 0x20, 0x32, 0x32])
XR18_CHANNELS = 18

COLOR_NAMES = (
    "OFF", "RD", "GN", "YE", "BL", "MG", "CY", "WH",
    "OFFi", "RDi", "GNi", "YEi", "BLi", "MGi", "CYi", "WHi",
)

SETUP_HINT = """\
XR18 returned no MIDI data. On the mixer (X Air Edit -> Setup -> MIDI Config):
  [x] USB Rx
  [x] USB Tx
  [x] USB X/OSC
  [ ] USB-DIN Pass Thru   (must be OFF — otherwise USB MIDI bypasses the desk)

midiconfig bitmask (/-prefs/midiconfig): USB Rx=8, USB Tx=16, USB X/OSC=32 -> 56
"""


@dataclass
class ChannelScribble:
    channel: int
    name: str | None = None
    color: int | None = None

    @property
    def color_label(self) -> str:
        if self.color is None:
            return "?"
        if 0 <= self.color < len(COLOR_NAMES):
            return COLOR_NAMES[self.color]
        return str(self.color)


class MidiLink(ABC):
    @abstractmethod
    def send_osc(self, command: str) -> None:
        ...

    @abstractmethod
    def receive(self, timeout: float) -> list[bytes]:
        """Return complete raw MIDI messages (each including F0..F7 for sysex)."""
        ...

    @abstractmethod
    def close(self) -> None:
        ...

    def __enter__(self) -> MidiLink:
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()


class RawAlsaMidi(MidiLink):
    """Talk directly to /dev/snd/midiC* (bypasses PipeWire sequencer routing)."""

    def __init__(self, device: str | Path) -> None:
        self._path = str(device)
        self._fd = os.open(self._path, os.O_RDWR | os.O_NONBLOCK)
        self._rx_buffer = bytearray()

    def send_osc(self, command: str) -> None:
        packet = bytes([0xF0]) + SYSEX_HEADER + command.encode("ascii") + bytes([0xF7])
        os.write(self._fd, packet)

    def receive(self, timeout: float) -> list[bytes]:
        deadline = time.monotonic() + timeout
        messages: list[bytes] = []
        while time.monotonic() < deadline:
            wait = min(0.05, max(0.0, deadline - time.monotonic()))
            ready, _, _ = select.select([self._fd], [], [], wait)
            if not ready:
                continue
            try:
                chunk = os.read(self._fd, 4096)
            except BlockingIOError:
                continue
            if not chunk:
                continue
            self._rx_buffer.extend(chunk)
            messages.extend(self._pop_complete_messages())
        messages.extend(self._pop_complete_messages())
        return messages

    def _pop_complete_messages(self) -> list[bytes]:
        out: list[bytes] = []
        buf = self._rx_buffer
        i = 0
        while i < len(buf):
            status = buf[i]
            if status == 0xF0:
                end = buf.find(0xF7, i + 1)
                if end == -1:
                    break
                out.append(bytes(buf[i : end + 1]))
                i = end + 1
                continue
            if status < 0x80:
                i += 1
                continue
            if status in (0xF1, 0xF3):
                length = 2
            elif status == 0xF2:
                length = 3
            elif status in (0xF4, 0xF5, 0xF9, 0xFD):
                length = 1
            elif status == 0xF6:
                length = 1
            elif status == 0xF8:
                i += 1
                continue
            elif status == 0xFF:
                end = buf.find(0xF7, i + 1)
                if end == -1:
                    break
                out.append(bytes(buf[i : end + 1]))
                i = end + 1
                continue
            elif status < 0xF0:
                high = status & 0xF0
                length = 3 if high in (0x80, 0xB0, 0xE0) else 2 if high in (0x90, 0xA0, 0xD0) else 1
            else:
                i += 1
                continue
            if i + length > len(buf):
                break
            out.append(bytes(buf[i : i + length]))
            i += length
        del buf[:i]
        return out

    def close(self) -> None:
        os.close(self._fd)


class MidoMidi(MidiLink):
    def __init__(self, port_hint: str | None) -> None:
        try:
            import mido
        except ImportError as exc:
            raise SystemExit(
                "mido + python-rtmidi required for --port mode.\n"
                "  python3 -m venv /tmp/xr18-midi-venv\n"
                "  /tmp/xr18-midi-venv/bin/pip install mido python-rtmidi\n"
                "Or use --raw-device /dev/snd/midiC2D0 (stdlib only)."
            ) from exc

        self._mido = mido
        outputs = mido.get_output_names()
        inputs = mido.get_input_names()
        out_name = _find_port(outputs, port_hint)
        in_name = _find_port(inputs, port_hint)
        self._in = mido.open_input(in_name)
        self._out = mido.open_output(out_name)
        self._names = (in_name, out_name)

    @property
    def port_names(self) -> tuple[str, str]:
        return self._names

    def send_osc(self, command: str) -> None:
        data = [*SYSEX_HEADER, *command.encode("ascii")]
        self._out.send(self._mido.Message("sysex", data=data))

    def receive(self, timeout: float) -> list[bytes]:
        deadline = time.monotonic() + timeout
        messages: list[bytes] = []
        while time.monotonic() < deadline:
            for msg in self._in.iter_pending():
                if msg.type == "sysex":
                    messages.append(bytes([0xF0, *msg.data, 0xF7]))
                else:
                    messages.append(bytes(msg.bytes()))
            time.sleep(0.01)
        return messages

    def close(self) -> None:
        self._in.close()
        self._out.close()


def _find_port(names: list[str], hint: str | None) -> str:
    if hint:
        for name in names:
            if hint.lower() in name.lower():
                return name
        raise SystemExit(f"No MIDI port matching {hint!r}. Available: {names}")

    for pattern in ("X18/XR18", "XR18", "X18", "X AIR"):
        for name in names:
            if pattern in name:
                return name

    raise SystemExit(
        "Could not auto-detect XR18 MIDI port. Use --port or --raw-device.\n"
        f"Available ports: {names}"
    )


def discover_raw_device() -> str | None:
    try:
        result = subprocess.run(
            ["amidi", "-l"],
            check=True,
            capture_output=True,
            text=True,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        return None

    card: str | None = None
    for line in result.stdout.splitlines():
        if "X18/XR18" in line or "XR18" in line:
            # IO  hw:2,0,0  X18/XR18 MIDI 1
            parts = line.split()
            for part in parts:
                if part.startswith("hw:"):
                    card = part
                    break
    if not card:
        return None

    # hw:C,D,P -> /dev/snd/midiC{D}
    c, d, _p = card.replace("hw:", "").split(",")
    dev = Path(f"/dev/snd/midiC{c}D{d}")
    return str(dev) if dev.exists() else None


def sysex_body(message: bytes) -> bytes | None:
    if len(message) < 6 or message[0] != 0xF0 or message[-1] != 0xF7:
        return None
    body = message[1:-1]
    if not body.startswith(SYSEX_HEADER):
        return None
    return body[len(SYSEX_HEADER) :]


def decode_osc_message(raw: bytes) -> tuple[str, list[Any]] | None:
    if not raw or raw[0] != ord("/"):
        return None

    def read_padded_string(buf: bytes, offset: int) -> tuple[str, int]:
        end = buf.index(0, offset)
        text = buf[offset:end].decode("utf-8", errors="replace")
        return text, ((end + 1) + 3) & ~3

    try:
        address, pos = read_padded_string(raw, 0)
        if pos >= len(raw):
            return address, []

        typetags, pos = read_padded_string(raw, pos)
        if not typetags.startswith(","):
            return address, []

        args: list[Any] = []
        for tag in typetags[1:]:
            if tag == "i":
                args.append(struct.unpack(">i", raw[pos : pos + 4])[0])
                pos += 4
            elif tag == "f":
                args.append(struct.unpack(">f", raw[pos : pos + 4])[0])
                pos += 4
            elif tag == "s":
                value, pos = read_padded_string(raw, pos)
                args.append(value)
            elif tag == "T":
                args.append(True)
            elif tag == "F":
                args.append(False)
            else:
                return address, args
        return address, args
    except (ValueError, struct.error, IndexError):
        return None


def parse_osc_text(text: str) -> tuple[str, list[Any]] | None:
    text = text.strip()
    if not text.startswith("/"):
        return None

    typed = re.match(r"^(\S+)\s*,\s*([ifs])\s+(.+)$", text)
    if typed:
        path, tag, value = typed.groups()
        if tag == "i":
            return path, [int(float(value.split()[0]))]
        if tag == "f":
            return path, [float(value.split()[0])]
        return path, [value.strip()]

    parts = text.split(maxsplit=1)
    if len(parts) == 1:
        return parts[0], []
    path, rest = parts
    if rest.lstrip("-").isdigit():
        return path, [int(rest)]
    try:
        return path, [float(rest)]
    except ValueError:
        return path, [rest]


def parse_sysex_osc(message: bytes) -> tuple[str, list[Any]] | None:
    payload = sysex_body(message)
    if payload is None:
        return None

    if payload[:1] == b"/":
        if b"\x00" in payload or (len(payload) > 8 and b"," in payload[:64]):
            decoded = decode_osc_message(payload)
            if decoded:
                return decoded
        text = payload.decode("ascii", errors="replace").rstrip("\x00")
        return parse_osc_text(text)
    return None


def channel_from_path(path: str) -> int | None:
    match = re.match(r"/ch/(\d{2})/config/(name|color)", path)
    return int(match.group(1)) if match else None


def config_field(path: str) -> str | None:
    match = re.match(r"/ch/\d{2}/config/(name|color)", path)
    return match.group(1) if match else None


def apply_message(
    result: dict[int, ChannelScribble],
    message: bytes,
    verbose: bool,
) -> None:
    if verbose:
        print(f"  rx: {message.hex(' ')}", file=sys.stderr)

    parsed = parse_sysex_osc(message)
    if not parsed:
        return

    path, args = parsed
    ch = channel_from_path(path)
    field = config_field(path)
    if ch is None or field is None or not args:
        if verbose:
            print(f"  skip: {path} {args}", file=sys.stderr)
        return

    if field == "name":
        result[ch].name = str(args[0])
    else:
        result[ch].color = int(args[0])


def collect_scribble(
    link: MidiLink,
    channels: int,
    timeout: float,
    verbose: bool,
) -> dict[int, ChannelScribble]:
    result = {ch: ChannelScribble(channel=ch) for ch in range(1, channels + 1)}

    link.send_osc("/xremote")
    time.sleep(0.1)
    for msg in link.receive(0.15):
        apply_message(result, msg, verbose)

    for ch in range(1, channels + 1):
        nn = f"{ch:02d}"
        for field in ("name", "color"):
            link.send_osc(f"/ch/{nn}/config/{field}")
            time.sleep(0.04)

    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        batch = link.receive(0.08)
        if not batch:
            if all(
                result[c].name is not None and result[c].color is not None
                for c in result
            ):
                break
            continue
        for msg in batch:
            apply_message(result, msg, verbose)

    return result


def run_diagnose(link: MidiLink) -> int:
    print("Sending /xremote, /info, /ch/01/config/name ...")
    for cmd in ("/xremote", "/info", "/ch/01/config/name", "/ch/01/config/color"):
        link.send_osc(cmd)
        time.sleep(0.08)

    messages = link.receive(3.0)
    print(f"Received {len(messages)} MIDI message(s) in 3s")
    for msg in messages:
        body = sysex_body(msg)
        if body is not None:
            preview = body[:80]
            try:
                text = preview.decode("ascii")
            except UnicodeDecodeError:
                text = preview.hex(" ")
            print(f"  sysex osc: {text!r}")
        else:
            print(f"  other: {msg.hex(' ')}")

    if not messages:
        print(SETUP_HINT, file=sys.stderr)
        return 2
    return 0


def print_table(scribbles: dict[int, ChannelScribble]) -> None:
    print(f"{'Ch':>3}  {'Name':<20}  {'Color':>5}  Label")
    print("-" * 42)
    for ch in sorted(scribbles):
        row = scribbles[ch]
        name = row.name if row.name is not None else "(no reply)"
        color = "" if row.color is None else str(row.color)
        print(f"{ch:3d}  {name:<20}  {color:>5}  {row.color_label}")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Read XR18 scribble strip via USB MIDI OSC SysEx (no network)."
    )
    parser.add_argument("--port", help="ALSA/PipeWire MIDI port name substring")
    parser.add_argument(
        "--raw-device",
        help="Raw ALSA device path, e.g. /dev/snd/midiC2D0 (recommended on Linux)",
    )
    parser.add_argument("--channels", type=int, default=XR18_CHANNELS)
    parser.add_argument("--timeout", type=float, default=8.0)
    parser.add_argument("--json", action="store_true", help="Print JSON instead of table")
    parser.add_argument("--diagnose", action="store_true", help="Send probe commands and exit")
    parser.add_argument("--list-ports", action="store_true")
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args()

    if args.list_ports:
        raw = discover_raw_device()
        if raw:
            print(f"Raw ALSA (amidi): {raw}")
        try:
            import mido

            print("Input ports:")
            for name in mido.get_input_names():
                print(f"  {name}")
            print("Output ports:")
            for name in mido.get_output_names():
                print(f"  {name}")
        except ImportError:
            print("(install mido to list sequencer ports)")
        return 0

    raw_device = args.raw_device or discover_raw_device()
    if raw_device and not args.port:
        if args.verbose:
            print(f"Using raw ALSA device: {raw_device}", file=sys.stderr)
        link: MidiLink = RawAlsaMidi(raw_device)
    else:
        link = MidoMidi(args.port)
        if args.verbose and isinstance(link, MidoMidi):
            in_name, out_name = link.port_names
            print(f"Using IN:  {in_name}", file=sys.stderr)
            print(f"Using OUT: {out_name}", file=sys.stderr)

    try:
        if args.diagnose:
            return run_diagnose(link)

        scribbles = collect_scribble(
            link,
            channels=args.channels,
            timeout=args.timeout,
            verbose=args.verbose,
        )
    finally:
        link.close()

    if args.json:
        payload = {
            str(ch): {
                "name": row.name,
                "color": row.color,
                "color_label": row.color_label if row.color is not None else None,
            }
            for ch, row in sorted(scribbles.items())
        }
        print(json.dumps(payload, indent=2))
    else:
        print_table(scribbles)

    missing = [
        ch
        for ch, row in scribbles.items()
        if row.name is None or row.color is None
    ]
    if missing:
        print(SETUP_HINT, file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
