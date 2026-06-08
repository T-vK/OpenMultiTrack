# Behringer FLOW 8 — Control Protocol Reverse Engineering

Reverse-engineering notes for the **Behringer FLOW 8** digital mixer, covering its
two control surfaces:

1. **USB MIDI** — a class-compliant, one-way Control Change / Program Change interface.
2. **Bluetooth LE (BLE)** — the proprietary transport used by the official
   *Behringer FLOW Mixer* app for full state read/write, including the full
   **SysEx state dump** and **snapshot/channel names**.

The primary goal of this research is to **extract the channel names** that the
official app stores in the mixer — values that are not exposed over plain USB MIDI.

> **Status:** BLE name/icon capture is **validated on hardware** (firmware v11749,
> see docs 04 and 06). USB SysEx capture remains blocked by an unstable USB link on
> the local unit ([`05-hardware-probe-findings.md`](./05-hardware-probe-findings.md)).

## Device identity

| Field            | Value                                  |
| ---------------- | -------------------------------------- |
| USB Vendor ID    | `0x1397` (Music Tribe / Behringer)     |
| USB Product ID   | `0x050C`                               |
| USB Product name | `FLOW 8`                               |
| `bcdDevice`      | `2.10`                                 |
| USB class        | Audio Class + MIDI (class-compliant)   |
| BLE name         | contains `FLOW`                        |

## Document index

| File | Contents |
| ---- | -------- |
| [`01-usb-midi-implementation.md`](./01-usb-midi-implementation.md) | USB enumeration, MIDI CC / Program Change map, direction limits |
| [`02-sysex-dump-format.md`](./02-sysex-dump-format.md) | SysEx framing, 7-byte rotating-MSB packing, packed-float encoding, parameter offset tables |
| [`03-bluetooth-le-protocol.md`](./03-bluetooth-le-protocol.md) | GATT service/characteristic, auth + session handshake, dump trigger, snapshot-name fetch |
| [`04-channel-name-extraction.md`](./04-channel-name-extraction.md) | **Main goal** — six mixer names, USB 1–8 mapping, fixed Main L/R on USB 9–10 |
| [`05-hardware-probe-findings.md`](./05-hardware-probe-findings.md) | What was observed on the locally attached unit (incl. the USB fault) |
| [`06-channel-icons-and-stereo-link.md`](./06-channel-icons-and-stereo-link.md) | Channel **icons** (input type + preset → MS id 1–74), full icon tables, **stereo-link** |
| [`tools/capture_sysex.sh`](./tools/capture_sysex.sh) | Capture a raw SysEx dump from USB MIDI with `amidi` |
| [`tools/extract_channel_names.py`](./tools/extract_channel_names.py) | Decode channel names from a captured dump (no dependencies) |
| [`tools/extract_flow8_channels.py`](./tools/extract_flow8_channels.py) | Decode six names, icons, Flow UI labels, USB 1–10 mapping |
| [`tools/flow8_icon_decode.py`](./tools/flow8_icon_decode.py) | Reference icon decoder (input type + preset → MS id) |
| [`tools/export_icon_tables.py`](./tools/export_icon_tables.py) | Print markdown icon tables for doc 06 |
| [`tools/ble_dump_names.py`](./tools/ble_dump_names.py) | Live BLE capture: MixerState + icon config query |

## How the pieces fit together

```
                 ┌─────────────────────────┐
   BLE (app) ───▶│  Auth + session         │
                 │  0x4B "dump trigger" ───┼──┐
                 └─────────────────────────┘  │ mixer emits SysEx
                                              ▼
   USB MIDI ◀────────────  F0 00 20 32 21 … F7  (~3068 bytes)
                                              │
                                              ▼
                         decode 7-byte MSB packing
                                              │
                                              ▼
                 six channel names → USB 1–8 + Main L/R
```

**Channel naming model:** the mixer always exposes **six** custom names (Ch1–4,
Ch5+6, Ch7+8). Map them to USB capture channels 1–8; label USB 9–10 as **Main L**
and **Main R** (not returned by the mixer).

For names, the simplest path is **BLE-only** (`0x37` → `0x38` fragments, ~436 bytes).
The full **USB SysEx** dump (`F0 00 20 32 21 … F7`, ~3 KiB) requires BLE `0x4B` to
trigger and USB MIDI to receive.

## Sources & attribution

This documentation consolidates:

- Direct observation of the locally attached FLOW 8 (USB descriptors, ALSA/MIDI,
  `dmesg`), see `05-hardware-probe-findings.md`.
- The official Behringer FLOW 8 user manual.
- The open-source reference client
  [`abelroes/flow-8-midi`](https://github.com/abelroes/flow-8-midi), which derived
  the SysEx offset tables and BLE packets via an automated calibrate→digest process.
  Offset tables and packet constants below are credited to that project.

All protocol details are **empirically derived** and unofficial. Behringer publishes
no MIDI/BLE implementation chart for the FLOW 8. Use at your own risk.
