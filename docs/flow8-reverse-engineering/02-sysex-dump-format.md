# 02 — SysEx State Dump Format

The FLOW 8 can emit a single **System Exclusive (SysEx) dump** containing its entire
mixer state: every channel's level, gain, pan, EQ, sends, mutes/solos, the bus
parameters, FX, **and the channel names**. This is the richest data source and the
basis for channel-name extraction.

## Framing

```
F0 00 20 32 21 <payload …> F7
│  └──────┘ │  │            └ SysEx end  (0xF7)
│  Behr. ID │  └ payload (packed parameter memory)
│           └ model byte = 0x21 (FLOW 8)
└ SysEx start (0xF0)
```

| Field            | Bytes        | Value          |
| ---------------- | ------------ | -------------- |
| Start            | `F0`         | `0xF0`         |
| Manufacturer ID  | `00 20 32`   | Behringer / Music Tribe |
| Model            | `21`         | FLOW 8         |
| Payload          | variable     | packed state   |
| End              | `F7`         | `0xF7`         |

Total length observed by the reference client: **~3068 bytes**. A valid dump is
≥ 100 bytes, begins with `0xF0`, ends with `0xF7`, and matches the ID + model bytes.

> **Offsets in this document are absolute from the leading `0xF0` byte** (i.e. the
> first byte of the dump is offset `0x0000`). When you capture with `amidi` the
> saved file starts at `0xF0`, so the offsets line up directly.

## 7-byte rotating-MSB packing

MIDI SysEx data bytes must stay in the range `0x00–0x7F` (bit 7 clear). The FLOW 8
therefore stores 8-bit values using a **rotating most-significant-bit (MSB) carrier**
scheme: within each 7-byte group, one byte is an MSB carrier holding the high bits
of the other bytes in the group.

A byte at absolute position `pos` is reconstructed as follows (faithful to the
reference parser):

```python
def restore_byte(data, pos):
    group_pos = (pos + 2) % 7
    if group_pos == 0:
        return None              # this position IS an MSB carrier byte
    if pos < group_pos:
        return data[pos]         # too close to the start to reach a carrier
    msb_off = pos - group_pos    # carrier byte for this group
    msb = data[msb_off]
    bit_index = group_pos - 1    # which bit of the carrier (0..5)
    b = data[pos]
    if msb & (1 << bit_index):
        b |= 0x80                # restore bit 7
    return b
```

- Carrier bytes occur where `(pos + 2) % 7 == 0`, i.e. `pos = 5, 12, 19, …`.
- Each carrier supplies bit 7 for the (up to six) data bytes that follow it in the
  group, via `bit_index = group_pos − 1`.
- For pure ASCII (`< 0x80`) the restoration is a no-op, but it must still be applied
  because the carrier byte is skipped (`None`) when iterating a region.

## Packed-float parameter encoding

Continuous parameters (levels, gain, EQ dB, pan, etc.) are stored as **IEEE-754
little-endian 32-bit floats**, but the four float bytes are **scattered** across the
packed memory, and each byte's bit 7 is supplied by a nearby MSB carrier byte. The
reference parser models every float parameter with:

```rust
struct FloatParam {
    msb_off: usize,        // carrier byte holding bit 7 of each data byte
    data_offs: [usize; 4], // the four little-endian float bytes (scattered)
    bit_indices: [u8; 4],  // which carrier bit feeds each data byte
}
```

Decode:

```python
def decode_float(data, p):           # p = FloatParam
    msb = data[p.msb_off]
    out = bytearray(4)
    for i in range(4):
        b = data[p.data_offs[i]]
        if msb & (1 << p.bit_indices[i]):
            b |= 0x80
        out[i] = b
    return struct.unpack('<f', bytes(out))[0]   # little-endian f32
```

The decoded float is a real-world value (dB, Hz, −1..+1 pan, …) that the client maps
back to a 0–127 CC scale. These per-parameter offset tables were produced by an
automated **calibrate → digest** process (set each CC to min/max, diff the dumps,
search for the 1 carrier + 4 data + bit-index combination whose float lands in the
expected range).

## Memory map (high level)

| Region            | Approx. offset range | Contents                              |
| ----------------- | -------------------- | ------------------------------------- |
| Header            | `0x0000`–`0x0004`    | `F0 00 20 32 21`                      |
| Channel sends     | `0x0050`–`0x0062` …  | Mon1/Mon2/FX1/FX2 sends per channel   |
| Channel strip     | `0x0067`–`0x022B` …  | level / pan blocks (Ch1–Ch7)          |
| Bus levels/bal    | `0x0336`–`0x054E`    | Main/Mon/FX levels & balances         |
| **Channel names** | **`0x0554` +**       | **6 × 30-byte slots (Ch1–4, Ch5+6, Ch7+8) — doc 04** |
| Gain / comp / EQ  | `0x0736`–`0x08E0`    | per-channel gain, compressor, 4-band EQ |
| Bus limiters / 9-band EQ | `0x08FD`–`0x0B72` | bus limiters + graphic EQ          |
| FX slots          | `0x0BC5`–`0x0BD1`    | FX1/FX2 params + preset               |
| End               | last byte            | `F7`                                  |

For exhaustive per-parameter offsets (all 7 channels × ~15 params, all buses, FX),
see the `FloatParam` / `BoolParam` tables in the reference client's
`src/service/sysex_parser.rs`. The **channel-name region** — the focus of this
research — is detailed in [`04-channel-name-extraction.md`](./04-channel-name-extraction.md).

## How the dump is triggered

The mixer emits this SysEx **only after** it receives the BLE **DumpTrigger** packet
`4B 01 4C` on its proprietary GATT characteristic. The SysEx then arrives on the
**USB MIDI** input. See [`03-bluetooth-le-protocol.md`](./03-bluetooth-le-protocol.md).

> **Note:** this SysEx path requires both BLE (to trigger) and USB (to receive).
> If you only need channel names, the simpler **BLE-only** path — send `GetMixerState`
> (`0x37`) and collect `0x38` MixerState fragments — avoids USB entirely.
> See [`04-channel-name-extraction.md`](./04-channel-name-extraction.md).
