# Behringer FLOW 8 — Control Protocol Reverse Engineering

Reverse-engineering notes for the **Behringer FLOW 8** digital mixer, covering its
two control surfaces:

1. **USB MIDI** — a class-compliant, one-way Control Change / Program Change interface.
2. **Bluetooth LE (BLE)** — the proprietary transport used by the official
   *Behringer FLOW Mixer* app for full state read/write, including the full
   **SysEx state dump** and **snapshot/channel names**.

The primary goal of this research is to **extract the channel names** that the
official app stores in the mixer — values that are not exposed over plain USB MIDI.

> **Status:** The protocol layer is fully documented below from public sources and
> the reference open-source client. A *live* capture from the locally connected unit
> could **not** be completed because the USB link is currently faulty
> (see [`05-hardware-probe-findings.md`](./05-hardware-probe-findings.md)). All
> tooling needed to capture and decode names once the cable/port is fixed is
> included under [`tools/`](./tools).

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
| [`04-channel-name-extraction.md`](./04-channel-name-extraction.md) | **Main goal** — where channel names live, the decode algorithm, end-to-end capture + parse workflow |
| [`05-hardware-probe-findings.md`](./05-hardware-probe-findings.md) | What was observed on the locally attached unit (incl. the USB fault) |
| [`tools/capture_sysex.sh`](./tools/capture_sysex.sh) | Capture a raw SysEx dump from USB MIDI with `amidi` |
| [`tools/extract_channel_names.py`](./tools/extract_channel_names.py) | Decode channel names from a captured dump (no dependencies) |

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
                 channel names @ 0x0554, stride 0x1E
```

The key insight: **channel names are carried inside the SysEx state dump**, and the
only documented way to make the mixer emit that dump is the BLE `0x4B` trigger — but
the dump itself is delivered over **USB MIDI**, where it can be captured with
standard ALSA tools.

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
