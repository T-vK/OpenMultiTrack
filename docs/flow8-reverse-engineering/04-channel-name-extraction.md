# 04 — Channel Name Extraction

Channel names (the per-input-strip labels you set in the official app) are stored in
the FLOW 8 and are **not** exposed over plain USB MIDI Control Change messages. They
are available via BLE in two ways, described below in order of preference.

All information in this document was verified against firmware **v11749** using a
live device.

## Method A — BLE MixerState dump (verified, no USB required)

This is the recommended approach. It requires only BLE — no USB cable, no `amidi`.

### Prerequisites

Complete the BLE handshake from
[`03-bluetooth-le-protocol.md`](./03-bluetooth-le-protocol.md) (subscribe →
receive `0x35` → send `0x39` → receive `0x36`). Then:

```
Send: frame(0x37)  →  [0x37, 0x01, 0x38]   (GetMixerState)
```

The device replies with **multiple `0x38` MixerState notifications**, each
containing one fragment of the full state buffer.

### Fragment reassembly

Each `0x38` notification has the header:

```
[0x38][total : u8][0x01 : u8][seq : u8][payload ...]
```

- `total` — total number of fragments in this dump.
- `seq` — 0-based index of this fragment (order of arrival is not guaranteed).

Reassemble by collecting all `total` fragments, ordered by `seq`, then concatenate
their payloads:

```python
fragments = {}
total_count = 0

def on_notify(data):
    if data[0] == 0x38 and len(data) >= 4:
        total_count = data[1]
        seq         = data[3]
        fragments[seq] = data[4:]

# After all fragments received:
full_buf = b"".join(fragments[i] for i in range(total_count))
```

### Name format in the reassembled buffer

Channel names are stored as **length-prefixed ASCII strings** scattered through the
raw state buffer. Scan the buffer for valid name entries:

```
[length : u8][length × printable-ASCII bytes]
```

A valid entry satisfies:
- `2 ≤ length ≤ 18` (names are at most 18 characters in the app UI)
- All `length` bytes are in the printable ASCII range `0x20–0x7E`

Scanning pseudocode (language-agnostic):

```
i = 0
names = []
while i < len(buf):
    L = buf[i]
    if 2 <= L <= 18 and i + 1 + L <= len(buf):
        s = buf[i+1 .. i+L]
        if all bytes in s are in 0x20..0x7E:
            names.append(ascii_decode(s))
            i += 1 + L
            continue
    i += 1
```

The mixer **always returns exactly six names**, in scan order:

| # | Mixer strip | USB capture channels |
| - | ----------- | -------------------- |
| 1 | Ch1 | USB 1 |
| 2 | Ch2 | USB 2 |
| 3 | Ch3 | USB 3 |
| 4 | Ch4 | USB 4 |
| 5 | Ch5+6 (one shared label) | USB 5 **and** USB 6 |
| 6 | Ch7+8 (one shared label) | USB 7 **and** USB 8 |

USB **9** and **10** are not named by the mixer — label them **Main L** and
**Main R** (the pre-fader main bus tap described in the FLOW 8 manual).

Stereo-link toggles in the app do not change how many names are returned; name 5
is always the Ch5/6 strip label and name 6 is always the Ch7/8 strip label.

### Verified offsets (firmware v11749, USB SysEx layout)

In a full USB SysEx dump, the six names appear at:

| # | Strip | Offset in buf |
| - | ----- | ------------- |
| 1 | Ch1 | `0x0554` |
| 2 | Ch2 | `0x0572` |
| 3 | Ch3 | `0x0590` |
| 4 | Ch4 | `0x05AE` |
| 5 | Ch5+6 | `0x05CC` |
| 6 | Ch7+8 | `0x05EA` |

In the **BLE compact** dump (~436 bytes), scan for length-prefixed ASCII instead
of using these absolute offsets.

Stride is `0x1E` (30 bytes) per slot. Each slot is 14 bytes; within those 14 bytes
the length byte and ASCII payload follow the SysEx 7-bit packing described in
doc 02, but for pure-ASCII names the scan approach above avoids the need to implement
the packing decoder.

### USB capture channel mapping

When recording multitrack audio over USB, the FLOW 8 presents **10 channels**
(8 analog inputs + main L/R pre-fader). Assign labels like this:

| USB ch | Source | Name from mixer? |
| ------ | ------ | ---------------- |
| 1–4 | Ch1–Ch4 | Names 1–4 |
| 5–6 | Ch5+6 pair | Name 5 (same string on both) |
| 7–8 | Ch7+8 pair | Name 6 (same string on both) |
| 9 | Main L | Always **Main L** (fixed) |
| 10 | Main R | Always **Main R** (fixed) |

Stereo-link toggles in the app do not change this table — name 5 is always the
Ch5/6 strip label and name 6 is always the Ch7/8 strip label.

### Full example (Python / bleak)

See [`tools/ble_dump_names.py`](./tools/ble_dump_names.py) for a complete,
event-driven reference implementation that:
- generates and persists a client ID,
- follows the full handshake,
- reassembles `0x38` fragments,
- decodes all six mixer names and the USB 1–10 mapping (incl. Main L/R),
- optionally queries icon IDs (`0x80`),
- saves the raw buffer to `flow8_dump.bin`.

---

## Method B — USB MIDI SysEx dump (alternative, requires USB + BLE)

This method produces a full SysEx state dump (`F0 00 20 32 21 … F7`) on the USB
MIDI port. It requires **both** BLE (to send the trigger) and USB (to receive the
dump), so Method A is usually simpler. Use this method when you need the complete
SysEx payload for parameters beyond channel names (levels, EQ, gains, FX, etc.).

### 1. Complete the BLE handshake

Follow steps 1–6 of doc 03 (subscribe → `0x35` → `0x39` → `0x36`).

### 2. Start the USB MIDI capture

On Linux with `amidi`:

```bash
amidi -l                                        # find the FLOW 8 port, e.g. hw:1,0,0
amidi -p hw:1,0,0 -r dump.syx -t 5             # record SysEx for 5 s
```

On macOS the port appears as a CoreMIDI endpoint named `FLOW 8`. On Windows it
appears in the standard MIDI device list. Any tool that can capture raw MIDI bytes
(including SysEx) will work.

### 3. Send the DumpTrigger over BLE

While the capture is running:

```
Send: frame(0x4B)  →  [0x4B, 0x01, 0x4C]
```

The mixer emits `F0 00 20 32 21 … F7` on USB MIDI within ~1 second. The `amidi -r`
file will contain the complete SysEx blob.

### 4. Decode channel names from the SysEx blob

The SysEx payload uses a 7-bit rotating-MSB packing scheme (see
[`02-sysex-dump-format.md`](./02-sysex-dump-format.md)). Offsets inside the dump
(from byte 0 = `0xF0`):

| # | Strip | Offset | Slot size |
| - | ----- | ------ | --------- |
| 1 | Ch1 | `0x0554` | 14 bytes |
| 2 | Ch2 | `0x0572` | 14 bytes |
| 3 | Ch3 | `0x0590` | 14 bytes |
| 4 | Ch4 | `0x05AE` | 14 bytes |
| 5 | Ch5+6 | `0x05CC` | 14 bytes |
| 6 | Ch7+8 | `0x05EA` | 14 bytes |

Only **six** name slots are used for scribble labels. Decode each with
`restore_byte` (doc 02), skip control bytes `< 0x20`, then read to the first `0x00`
null terminator.

---

## Related: icons and stereo link

Channel **icon IDs** and **stereo-link flags** for Ch5/6 and Ch7/8 live in the
same MixerState buffer (and a separate ParamQuery `0x80` response). See
[`06-channel-icons-and-stereo-link.md`](./06-channel-icons-and-stereo-link.md).

---

## USB MIDI alone: why it cannot retrieve names

The FLOW 8 does **not** respond to any USB MIDI SysEx request for its state. The
dump trigger (`0x4B` BLE packet → `SysexMidiDump` in the native library) is the
only mechanism that causes the mixer to emit a SysEx blob, and it is issued over
BLE. No equivalent trigger exists in the USB MIDI command set. This was confirmed
by examining all MIDI-related symbols in `libcom_musicgroup_xairbt.so`: the only
USB references are audio routing controls (`usb_streaming_mode`, `ch_56_usb_12`,
etc.), not dump triggers.

## Caveats

- Offsets (`0x0554`, stride `0x1E`) are from firmware v11749. A major firmware
  update could shift them; re-validate if names decode as garbage.
- The mixer never returns names for USB 9–10; those are always Main L/R.
- The BLE compact dump (~436 bytes) has no fixed offsets — scan for the first six
  length-prefixed ASCII strings.
- The heuristic scan works because channel names are pure printable ASCII. Names
  with characters outside `0x20–0x7E` would need the full MSB-restoration approach
  from doc 02.
