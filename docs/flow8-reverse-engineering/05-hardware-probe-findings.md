# 05 — Hardware Probe Findings (local unit)

This records what was directly observed on the FLOW 8 attached to this machine during
the investigation. The key takeaway: the unit is present but the **USB link is
currently faulty**, which blocked a live SysEx capture.

## What was confirmed

- The mixer **is physically connected** and identifies correctly when it enumerates:

  ```
  usb 1-1.1: New USB device found, idVendor=1397, idProduct=050c, bcdDevice=2.10
  usb 1-1.1: Product: FLOW 8
  ```

- A Bluetooth controller is present on the host (`Controller … NB-TVK [default]`), so
  the BLE path (auth + dump trigger, doc 03) is viable once needed.

- ALSA MIDI tooling is installed and available: `amidi`, `arecordmidi`, `aseqdump`,
  `aconnect`.

## The blocking problem: unstable USB port / cable

The device **does not stay enumerated**. `dmesg` repeatedly shows:

```
usb 1-1-port1: Cannot enable. Maybe the USB cable is bad?
usb 1-1-port1: Cannot enable. Maybe the USB cable is bad?
usb 1-1-port1: attempt power cycle
```

and the FLOW 8 cycles through `New USB device found … Product: FLOW 8` over and over
without settling. As a result, at probe time:

```
$ lsusb | grep -i 1397:050c
FLOW8_NOT_PRESENT

$ amidi -l
Dir Device    Name
(no FLOW 8 MIDI port)

$ cat /proc/asound/cards
 0 [NVidia ] HDA-Intel
 1 [PCH    ] HDA-Intel
(no USB-audio card for the FLOW 8)
```

Because the device never presents a stable ALSA MIDI port, **`amidi -r` could not be
run against it** and no SysEx dump (and therefore no live channel names) could be
captured during this session.

### This is a physical-layer fault, not software

`Cannot enable … Maybe the USB cable is bad?` followed by `attempt power cycle` is the
kernel/hub reporting that the port cannot bring the link up. Typical causes, in order
of likelihood:

1. **Bad / marginal USB cable** (most common — try a known-good data cable, not a
   charge-only one).
2. **Faulty or under-powered hub/port** — connect the FLOW 8 directly to a rear
   motherboard port, not a front-panel header or unpowered hub.
3. Insufficient bus power — ensure the mixer's own PSU is connected.
4. Less likely: a failing USB jack on the mixer.

## Recommended remediation to enable a live capture

1. Swap to a **known-good USB data cable** and use a **direct rear USB port**.
2. Confirm a stable link:
   ```bash
   watch -n1 'lsusb | grep -i 1397:050c; amidi -l'
   ```
   You want `1397:050c` to remain listed and a `FLOW 8` MIDI port to appear and
   **stay**, with no new `Cannot enable` lines in `dmesg -w`.
3. Then run the capture + decode workflow in
   [`04-channel-name-extraction.md`](./04-channel-name-extraction.md):
   ```bash
   ./tools/capture_sysex.sh dump.syx 8     # then trigger the dump (app/BLE)
   python3 ./tools/extract_channel_names.py dump.syx
   ```

## Verified offline instead

Since live hardware was unavailable, the decode toolchain was validated against a
**synthetic dump** built to match the documented framing and the 7-byte rotating-MSB
packing. `tools/extract_channel_names.py` correctly round-tripped names through the
carrier-byte-skipping logic (e.g. `VOX`, `GUITAR`), confirming the parser is ready to
run against a real capture once the USB link is restored.

## Environment summary

| Item              | State                                             |
| ----------------- | ------------------------------------------------- |
| FLOW 8 USB ID     | `1397:050c`, Product `FLOW 8`, `bcdDevice 2.10`   |
| USB link          | **Unstable** — `Cannot enable … power cycle` loop |
| ALSA MIDI port    | Not present (device never settled)                |
| BLE controller    | Present (`NB-TVK`)                                 |
| MIDI tools        | `amidi`, `aseqdump`, `arecordmidi`, `aconnect`    |
| Live SysEx dump   | **Not captured** (blocked by USB fault)           |
| Parser validation | Passed against synthetic dump                     |
