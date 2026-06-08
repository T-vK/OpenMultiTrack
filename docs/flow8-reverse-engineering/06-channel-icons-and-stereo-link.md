# 06 — Channel Icons and Stereo Link

How the official *Behringer FLOW Mixer* app (`Flowmix_v1.9.apk`) stores and
retrieves **channel icon IDs** and **stereo-link state** over Bluetooth LE.

Icons are **not** embedded in channel names (unlike XR18/Mixing Station OSC
`_NN` suffixes). The mixer sends a small **numeric icon ID** (1–74, Mixing
Station icon set); the app maps that ID to a local drawable
(`input_icon_NNN`).

The mixer always returns **six** names (see doc 04). Icons align with those six
strips; USB 9–10 use no mixer icon (Main L/R). Stereo linking applies only to the
hardware pairs **Ch5/6** and **Ch7/8** and affects internal routing, not how many
names are returned.

> **Sources:** `libcom_musicgroup_xairbt.so` symbols/strings from
> `Flowmix_v1.9.apk` (firmware **v11749**), plus hardware validation on a
> locally connected FLOW 8 (`FLOW 8 LE`, firmware bytes in HandshakeHost
> `…ee f4 01 00 09` → **v11749**). Captures are in
> [`tools/flow8_dump.bin`](./tools/flow8_dump.bin) (436 B) and
> [`tools/icon_config.bin`](./tools/icon_config.bin) (48 B).

---

## Overview: two BLE reads after handshake

After the standard handshake and `GetMixerState` (`0x37` → `0x38` fragments,
see doc 03), the official app performs a **second query** for icon data:

```
  Client                          FLOW 8
  ──────                          ──────
  … 0x37 GetMixerState  →
  ← 0x38 fragments (reassembled state buffer)
  … 0x26 ParamQuery id=0x80  →
  ← 0x25 ParamResponse id=0x80, 48-byte payload
```

The reassembled `0x38` buffer supplies the **six names** and optional per-slot
metadata. The `0x80` response supplies authoritative icon IDs for those six strips
(groups 0–5 of the 48-byte payload).

---

## Icon IDs — what travels over BLE

| Item | Detail |
| ---- | ------ |
| Wire format | Single byte per channel, values **1–74** |
| Images | **Not** transmitted; app uses `input_icon_001` … `input_icon_074` drawables |
| Native API | `getChannelIconId`, `Channel::setIconId(uint16_t)`, `queueChannelIconIdControlCommandPackets` |
| Presets | `getInputChannelPresetIconIdAtIndex` — separate preset library, same ID range |

The native `Channel::setIconId` takes a `uint16_t`, but the MixerState slot
stores a single-byte ID in normal use (values > 255 are not used in practice).

### Method A — ParamQuery `0x80` (preferred)

This maps to the native **GetSetting** path (`XAIRBT_CMD_GET_SETTING`,
`u.get_setting.id`). The Flow Mix app issues it as a GATT packet:

**Request**

```
frame(0x26, [0x80])
  = [0x26, 0x01, 0x80, 0xA7]
```

Checksum: `(0x26 + 0x01 + 0x80) & 0xFF = 0xA7`.

**Response** (`0x25` ParamResponse)

```
[0x25][0x01][param_id][payload_len][payload…][checksum]
```

- `param_id` = `0x80`
- `payload_len` = `0x30` (48 bytes) on hardware
- Payload layout: **12 groups × 4 bytes**. Icon ID for mixer strip *i* is
  **`payload[i * 4]`** when non-zero, otherwise **`payload[i * 4 + 1]`**.

| Group index | Strip | USB channels |
| ----------- | ----- | -------------- |
| 0–3 | Ch1–Ch4 | USB 1–4 |
| 4 | Ch5+6 | USB 5–6 |
| 5 | Ch7+8 | USB 7–8 |
| 6–11 | FX / other | *(not used for input scribble)* |

Decode pseudocode:

```python
def parse_icon_config(payload: bytes) -> list[int | None]:
    icons = []
    for i in range(len(payload) // 4):
        base = i * 4
        primary = payload[base] if base < len(payload) else 0
        fallback = payload[base + 1] if base + 1 < len(payload) else 0
        if 1 <= primary <= 74:
            icons.append(primary)
        elif 1 <= fallback <= 74:
            icons.append(fallback)
        else:
            icons.append(None)
    return icons
```

The app waits up to ~2 s for this response; if it times out, it falls back to
Method B.

### BLE vs USB buffer layouts

| Transport | Typical size | Name layout |
| --------- | ------------ | ----------- |
| BLE `0x38` MixerState | **436 bytes** (4 fragments) | Scan for six `[len][ascii…]` records |
| USB SysEx dump | **~3068 bytes** | Fixed region at `0x0554`, stride `0x1E` |

The BLE compact dump does **not** contain the `0x0554` region — scan for
length-prefixed ASCII and take the **first six names** in order (see doc 04).

Hardware-validated example (2026-06-08):

| # | Strip | Name | Icon (0x80) | USB |
| - | ----- | ---- | ----------- | --- |
| 1 | Ch1 | SM58 (L) | 3 | 1 |
| 2 | Ch2 | SM58 (R) | 3 | 2 |
| 3 | Ch3 | Mic 3 | 3 | 3 |
| 4 | Ch4 | Violine | 4 | 4 |
| 5 | Ch5+6 | ELECTRIC | 2 | 5, 6 |
| 6 | Ch7+8 | Playback | 3 | 7, 8 |
| — | Main L/R | *(fixed)* | — | 9, 10 |

### Method B — inline slot byte (fallback)

In the **USB SysEx** layout, each 30-byte name slot may carry an icon ID inline.
In the **BLE compact** layout this fallback is rarely needed when the `0x80`
query succeeds:

| Field | Offset within slot | Size | Notes |
| ----- | ------------------ | ---- | ----- |
| Name length | `+0x00` | u8 | Same as doc 04 |
| Name ASCII | `+0x01` | variable | Length-prefixed, null-terminated |
| Stereo flag | `+0x0E` | u8 | Bit 0 = stereo-linked (see below) |
| Icon ID | `+0x0F` | u8 | Used if value is 1–74 |

USB SysEx name/icon slot bases (stride `0x1E`, six slots only):

| # | Strip | Base offset |
| - | ----- | ----------- |
| 1 | Ch1 | `0x0554` |
| 2 | Ch2 | `0x0572` |
| 3 | Ch3 | `0x0590` |
| 4 | Ch4 | `0x05AE` |
| 5 | Ch5+6 | `0x05CC` |
| 6 | Ch7+8 | `0x05EA` |

If `+0x0F` is outside 1–74, scan the remaining slot bytes (`+0x10` … `+0x1D`)
for the first value in 1–74 (some firmware builds place the byte one position
later).

**Precedence:** When both sources are available, the ParamQuery `0x80` list
overrides the per-slot byte for matching indices.

---

## Stereo link — which channels are paired

The FLOW 8 has **two** stereo-link controls, exposed in the app UI as
`stereo_usb_12` (Ch5/6) and `stereo_usb_34` (Ch7/8). Ch1–4 are always mono.

### Per-slot flag in MixerState (primary for scribble import)

In the **USB SysEx** slot layout, byte **`+0x0E`** carries channel flags.
In the **BLE compact** dump, offset `+0x0E` from the length byte often lands on
fader defaults (`0x7F`) — do **not** treat those as stereo flags.

| Value | Meaning |
| ----- | ------- |
| `0x01` | **Stereo-linked** — this strip represents a linked pair |
| other | Not linked (including `0x7F` fader placeholders) |

Only name slots **5** and **6** (offsets `0x05CC` and `0x05EA`) carry stereo-link
flags in the USB SysEx layout:

| Name # | Strip | Offset `+0x0E` | Hardware pair |
| ------ | ----- | -------------- | ------------- |
| 5 | Ch5+6 | `0x05CC + 0x0E` | Ch5/6 linked when value is `0x01` |
| 6 | Ch7+8 | `0x05EA + 0x0E` | Ch7/8 linked when value is `0x01` |

Decode:

```python
flags = buf[slot_base + 0x0E]
stereo_linked = flags == 0x01   # not flags & 1 — 0x7F also has bit 0 set
```

Native symbols confirming this model:

- `getChannelIsStereo`, `Channel::setIsStereo(bool)`
- `XAIRBT_CMD_CHANNEL_CONNECTION_STATE` with fields `connected_l`, `connected_r`
- Log strings: ` connected_l: `, ` connected_r: `

The connection-state command pushes live updates when the user toggles stereo
in the app; the MixerState dump reflects the same state at snapshot time.

### Global routing flags in MixerState (secondary)

The native `MixerState` struct also exposes global booleans (likely in the
routing region near `0x04D6`):

| Field | Meaning |
| ----- | ------- |
| `ch_56_usb_12` | USB routing treats Ch5/6 as stereo pair |
| `ch_78_usb_34` | USB routing treats Ch7/8 as stereo pair |
| `mon_stereo_link` | Monitor bus stereo link (separate from input strips) |

For USB scribble import, names and icons always follow the six-name model (doc 04);
stereo flags are informational only and do not change USB 5–8 labelling.

### Names vs stereo link

The mixer **always** returns six names. Name 5 is the Ch5+6 strip label and name
6 is the Ch7+8 strip label — regardless of whether stereo link is enabled in the
app. USB mapping always copies those two names onto both channels in each pair;
USB 9–10 are always **Main L** / **Main R**.

Stereo-link flags (when reliably readable in USB SysEx) describe routing inside the
mixer, not how many names are returned. USB mapping is implemented in
`Flow8UsbScribbleMapper.kt`.

---

## End-to-end BLE workflow

```
1. Pairing mode on mixer (MENU → PAIRING → PAIR APP)
2. BLE connect + subscribe to characteristic
3. Wait for 0x35 HandshakeHost
4. Send frame(0x39, client_id)     # 16-byte non-zero ID
5. Wait for 0x36 HandshakeReply
6. Send frame(0x37)                # GetMixerState
7. Collect all 0x38 fragments → buf
8. Send frame(0x26, [0x80])        # Icon config query
9. Wait for 0x25 response (param_id=0x80, 48 bytes) — optional, 2 s timeout
10. Decode:
      names     ← first six length-prefixed strings (BLE) or 0x0554 + i*0x1E (SysEx)
      icons     ← 0x80 payload[i*4] or [i*4+1] for i in 0..5
      USB 9–10  ← fixed "Main L" / "Main R"
```

### Worked packet examples

| Step | Hex |
| ---- | --- |
| GetMixerState | `37 01 38` |
| Icon config query | `26 01 80 A7` |
| Example icon response header | `25 01 80 30 …` (48-byte payload follows) |

---

## Mapping icons to pictures

Use the Mixing Station / X-Air icon set (IDs 1–74). The OpenMultiTrack repo
ships emoji stand-ins in `MixingStationIcons.kt`; the Flow Mix APK uses
`res/drawable/input_icon_NNN` assets keyed by the same IDs.

To render like the official app:

```
drawable = "input_icon_%03d" % icon_id   # e.g. input_icon_017 for Bass
```

Community icon packs: [Patrick-Gilles Maillot / behringer-icons](https://github.com/pmaillot/Behringer-X32-Icons).

---

## Tools

| Script | Purpose |
| ------ | ------- |
| [`tools/ble_dump_names.py`](./tools/ble_dump_names.py) | Live BLE capture: names + icon config query |
| [`tools/extract_flow8_channels.py`](./tools/extract_flow8_channels.py) | Offline decode from saved dump (+ optional icon config sidecar) |

### Offline decode example

```bash
# From a SysEx file or raw 0x38 reassembly
python3 tools/extract_flow8_channels.py flow8_dump.bin

# With a saved 0x80 payload (48 bytes)
python3 tools/extract_flow8_channels.py flow8_dump.bin --icon-config icon_config.bin
```

---

## Hardware validation (2026-06-08)

Captured from `FLOW 8 LE` with pairing mode active:

```bash
cd docs/flow8-reverse-engineering/tools
python3 ble_dump_names.py
python3 extract_flow8_channels.py flow8_dump.bin --icon-config icon_config.bin
```

Results matched the mixer UI:

- Six names: SM58 (L), SM58 (R), Mic 3, Violine, ELECTRIC (Ch5+6), Playback (Ch7+8)
- USB 5–6 both labelled ELECTRIC; USB 7–8 both labelled Playback
- USB 9–10 set to Main L / Main R
- Icon IDs from ParamQuery `0x80` matched the Mixing Station set

## Caveats

- BLE compact offsets differ from the USB SysEx `0x0554` table; auto-detect by
  buffer size in `Flow8StateDecoder` / `extract_flow8_channels.py`.
- ParamQuery types `0x26` / `0x25` are observed at the GATT layer; the native
  library labels the same mechanism `XAIRBT_CMD_GET_SETTING` /
  `XAIRBT_CMD_SETTING`.
- `mon_stereo_link` is the **monitor bus** stereo toggle, not input Ch5/6 or
  Ch7/8 linking.
- Icons have no color index on FLOW 8 (names only on the compact mixer UI).

---

## Related documents

- [`03-bluetooth-le-protocol.md`](./03-bluetooth-le-protocol.md) — handshake, `0x37`/`0x38`
- [`04-channel-name-extraction.md`](./04-channel-name-extraction.md) — name slot layout
- [`02-sysex-dump-format.md`](./02-sysex-dump-format.md) — 7-bit packing (USB SysEx path)
