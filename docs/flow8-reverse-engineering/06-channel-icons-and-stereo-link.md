# 06 — Channel Icons and Stereo Link

How the official *Behringer FLOW Mixer* app (`Flowmix_v1.9.apk`) stores and
retrieves **channel icon IDs** and **stereo-link state** over Bluetooth LE.

Icons are **not** embedded in channel names (unlike XR18/Mixing Station OSC
`_NN` suffixes). The resolved picture is a **Mixing Station scribble icon ID**
(1–74). Over BLE the mixer does **not** send that ID directly — it sends an
**input-type category** plus a **preset index** that the official app resolves
through `getInputChannelPresetIconIdAtIndex` in `libcom_musicgroup_xairbt.so`.
Flow Mix drawable assets are named `input_icon_{type×100+preset:03d}` (e.g.
`input_icon_004`, `input_icon_304`, `input_icon_507`).

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

The reassembled `0x38` buffer supplies the **six names** and per-strip **input
type** bytes. The `0x80` response supplies the **preset index** for each strip
(groups 0–5 of the 48-byte payload). **Both** are required to resolve the final
Mixing Station icon ID.

---

## Icon encoding — input type + preset

| Layer | Source | Meaning |
| ----- | ------ | ------- |
| Resolved icon | `getChannelIconId` / `Channel::getIconId` | Mixing Station ID **1–74** (stored at channel offset `+0x28`) |
| Input type | BLE `0x38` compact name record | Category 0–5 (mic / guitar / line / playback — see table below) |
| Preset index | ParamQuery `0x80`, byte 1 of each 4-byte group | Index into that category’s icon picker list |
| Flow drawable | APK `res/drawable` | `input_icon_{type×100+preset:03d}` |
| Native lookup | `getInputChannelPresetIconIdAtIndex(inputType, presetIndex)` | Maps `(type, preset)` → MS icon byte |

**Important:** the `0x80` preset byte alone is **not** the scribble icon. The same
preset value means different icons in different categories — e.g. preset `04`
is **Wired Mic** on a dynamic-mic strip (type 0) but **Violine** on a line-instrument
strip (type 3). Hardware capture `03 04` on both Ch1 and Ch4 demonstrates this.

### FLOW input types

| Type | Native constant | Flow Mix picker category | Drawable range (APK v1.9) |
| ---- | --------------- | ------------------------ | ------------------------- |
| 0 | `InputTypeDynamicMic` | Dynamic / wired mics | `input_icon_000` … `input_icon_015` (16) |
| 1 | `InputTypeCondensorMic` | Condenser mics | `input_icon_100` … `input_icon_110` (11) |
| 2 | `InputTypeGuitarOrBass` | Guitar / bass | `input_icon_200` … `input_icon_217` (18) |
| 3 | `InputTypeLineInstrument` | Line instruments | `input_icon_300` … `input_icon_317` (18) |
| 4 | *(extended guitar page)* | Additional guitar icons | `input_icon_400` … `input_icon_407` (8) |
| 5 | *(playback / source)* | Playback, record, USB, etc. | `input_icon_500` … `input_icon_511` (12) |

Types 4 and 5 are used on hardware but are not exposed as separate `InputType*`
JNI stubs in the v1.9 library (only 0–3 are). They appear in the BLE compact
name header and in the `input_icon_4xx` / `input_icon_5xx` drawable pages.

### Method A — ParamQuery `0x80` + MixerState input type (preferred)

Maps to native **GetSetting** (`XAIRBT_CMD_GET_SETTING`). The Flow Mix app
issues:

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
- Payload layout: **12 groups × 4 bytes** (only groups **0–5** matter for input scribble)

| Offset (per group) | Field | Notes |
| ------------------ | ----- | ----- |
| `+0` | Marker | `0x03` = typed preset (current firmware); `0x00` = legacy plain encoding |
| `+1` | Preset index | Index into the strip’s input-type picker |
| `+2`, `+3` | Reserved | Always `0x00` in hardware captures |

| Group index | Strip | USB channels |
| ----------- | ----- | -------------- |
| 0–3 | Ch1–Ch4 | USB 1–4 |
| 4 | Ch5+6 | USB 5–6 |
| 5 | Ch7+8 | USB 7–8 |
| 6–11 | FX / other | *(not used for input scribble)* |

#### BLE compact name record — input type byte

After scanning the six `[len][ascii…]` names in the `0x38` buffer, read the
**input type** for each strip from the bytes **before** the length byte:

| Pattern | Input type |
| ------- | ---------- |
| `[…][0x6a][len][name]` | **0** (dynamic mic) — Ch1–3 on hardware |
| `[type][…][len][name]` where `type` ≤ 5 | **`type`** — byte at `name_offset − 3` |

Example hex around **Violine** (strip 4) in [`tools/flow8_dump.bin`](./tools/flow8_dump.bin):

```
… 03 01 34 07 56 69 6f 6c 69 6e 65 …
      ^^    ^^
      |     +-- len = 7
      +-- input type = 3 (line instrument)
```

#### Decode algorithm

```python
# See tools/flow8_icon_decode.py for the reference implementation.

def decode_icons(mixer_state: bytes, icon_payload: bytes) -> list[int]:
    name_offsets = scan_name_offsets(mixer_state)   # six [len][ascii] records
    icons = []
    for strip in range(6):
        base = strip * 4
        marker, preset = icon_payload[base], icon_payload[base + 1]
        if marker != 0x03:
            …  # legacy 0x00 marker — see tools/flow8_icon_decode.py
        input_type = decode_input_type(mixer_state, name_offsets[strip])
        icons.append(PRESET_TO_MS_ICON[(input_type, preset)])
    return icons
```

`PRESET_TO_MS_ICON` is a lookup table from `(input_type, preset)` to Mixing
Station IDs. Only a subset is validated on hardware so far (see
[Appendix C](#appendix-c-hardware-validated-preset--icon-mapping)); the native
library contains the full table behind `getInputChannelPresetIconIdAtIndex`.

The app waits up to ~2 s for the `0x80` response; if it times out, it falls back
to Method B.

### BLE vs USB buffer layouts

| Transport | Typical size | Name layout |
| --------- | ------------ | ----------- |
| BLE `0x38` MixerState | **437 bytes** (4 fragments) | Scan for six `[len][ascii…]` records |
| USB SysEx dump | **~3068 bytes** | Fixed region at `0x0554`, stride `0x1E` |

The BLE compact dump does **not** contain the `0x0554` region — scan for
length-prefixed ASCII and take the **first six names** in order (see doc 04).

#### Hardware-validated example (2026-06-08)

Mixer UI icons: **Wired Mic ×3**, **Violine**, **Acoustic Guitar** (Ch5+6),
**Record player** (Ch7+8).

`icon_config.bin` (groups 0–5):

```
03 04  03 04  03 07  03 04  03 02  03 07
```

| # | Strip | Name | Input type | Preset | Flow UI label | MS ID | MS label |
| - | ----- | ---- | ---------- | ------ | ------------- | ----- | -------- |
| 1 | Ch1 | SM58 (L) | 0 | 4 | Wired Mic | 50 | `handheld-mic` |
| 2 | Ch2 | SM58 (R) | 0 | 4 | Wired Mic | 50 | `handheld-mic` |
| 3 | Ch3 | Mic 3 | 0 | 7 | Wired Mic | 50 | `handheld-mic` |
| 4 | Ch4 | Violine | 3 | 4 | Violine | 39 | `violin` |
| 5 | Ch5+6 | ELECTRIC1 | 4 | 2 | Acoustic Guitar | 23 | `acoustic-guitar` |
| 6 | Ch7+8 | Playback | 5 | 7 | Record player | 60 | `tape` |
| — | Main L/R | *(fixed)* | — | — | Main L / Main R | 65 / 64 | `speaker-left` / `speaker-right` |

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
      names       ← first six length-prefixed strings (BLE) or 0x0554 + i*0x1E (SysEx)
      input types ← BLE compact header byte before each name (see above)
      presets     ← 0x80 payload[i*4+1] when marker[i*4] == 0x03
      icon ids    ← lookup (input_type, preset) → MS id 1–74
      USB 9–10    ← fixed "Main L" / "Main R"
```

### Worked packet examples

| Step | Hex |
| ---- | --- |
| GetMixerState | `37 01 38` |
| Icon config query | `26 01 80 A7` |
| Example icon response header | `25 01 80 30 …` (48-byte payload follows) |

---

## Mapping icons to pictures

Three related numbering schemes:

| Scheme | Example | Used for |
| ------ | ------- | -------- |
| Mixing Station ID | `50` | Resolved scribble value (`getChannelIconId`), X32 icon packs |
| Flow drawable key | `input_icon_004` | APK picker asset for type 0, preset 4 |
| Flow UI label | `Wired Mic` | On-screen picker text (not sent over BLE) |

To render from a resolved MS icon ID (e.g. in OpenMultiTrack):

```
# Mixing Station / X32 SVG packs
icon_svg = "icon_%02d.svg" % ms_id

# Or emoji stand-ins — MixingStationIcons.kt / mixing_station_icons.py
```

Community SVG packs: [Patrick-Gilles Maillot / behringer-icons](https://github.com/pmaillot/Behringer-X32-Icons).

The full MS ID → label table is in [Appendix A](#appendix-a-mixing-station-scribble-icon-ids-174).

---

## Tools

| Script | Purpose |
| ------ | ------- |
| [`tools/ble_dump_names.py`](./tools/ble_dump_names.py) | Live BLE capture: names + icon config query |
| [`tools/extract_flow8_channels.py`](./tools/extract_flow8_channels.py) | Offline decode: names, icons, Flow UI labels, USB 1–10 scribble |
| [`tools/flow8_icon_decode.py`](./tools/flow8_icon_decode.py) | Reference decoder: input type + preset → MS icon |
| [`tools/mixing_station_icons.py`](./tools/mixing_station_icons.py) | MS icon id → label / constant / emoji |
| [`tools/export_icon_tables.py`](./tools/export_icon_tables.py) | Print markdown icon tables (used to maintain this doc) |

### Offline decode example

```bash
cd docs/flow8-reverse-engineering/tools

# Live capture (FLOW 8 in pairing mode)
python3 ble_dump_names.py

# Offline decode — requires BOTH dump and 0x80 payload
python3 extract_flow8_channels.py flow8_dump.bin --icon-config icon_config.bin

# Regenerate appendix tables
python3 export_icon_tables.py all
```

---

## Hardware validation (2026-06-08)

Captured from `FLOW 8 LE` (firmware v11749) with pairing mode active:

```bash
cd docs/flow8-reverse-engineering/tools
python3 ble_dump_names.py
python3 extract_flow8_channels.py flow8_dump.bin --icon-config icon_config.bin
```

Script output matched the mixer UI:

```
  #    Strip     Icon                          Name
  1    Ch1       50 Wired Mic                   "SM58 (L)"
  2    Ch2       50 Wired Mic                   "SM58 (R)"
  3    Ch3       50 Wired Mic                   "Mic 3"
  4    Ch4       39 Violine                     "Violine"
  5    Ch5+6     23 Acoustic Guitar             "ELECTRIC1"
  6    Ch7+8     60 Record player               "Playback"
```

Fixtures: [`tools/flow8_dump.bin`](./tools/flow8_dump.bin),
[`tools/icon_config.bin`](./tools/icon_config.bin).

## Caveats

- **Do not** treat `0x80` byte 0 (`0x03`) or byte 1 alone as an MS icon ID.
  Always combine with the per-strip input type from the `0x38` buffer.
- The Kotlin decoder in `Flow8StateDecoder.kt` still uses an older marker/code
  mapping and should be updated to match `flow8_icon_decode.py`.
- Only a subset of `(input_type, preset)` pairs is validated on hardware
  ([Appendix C](#appendix-c-hardware-validated-preset--icon-mapping)); the full
  picker table lives in the native library.
- BLE compact offsets differ from the USB SysEx `0x0554` table; auto-detect by
  buffer size in `Flow8StateDecoder` / `extract_flow8_channels.py`.
- ParamQuery types `0x26` / `0x25` are observed at the GATT layer; the native
  library labels the same mechanism `XAIRBT_CMD_GET_SETTING` /
  `XAIRBT_CMD_SETTING`.
- `mon_stereo_link` is the **monitor bus** stereo toggle, not input Ch5/6 or
  Ch7/8 linking.
- Icons have no color index on FLOW 8 (names only on the compact mixer UI).

---

## Appendix A: Mixing Station scribble icon IDs (1–74)

Resolved icon values on the wire and in `getChannelIconId` use this
X32 / X-Air / Mixing Station numbering. Flow Mix drawables map to the same ids.

| MS ID | Label | Emoji |
| ----- | ----- | ----- |
| 1 | `blank` |  |
| 2 | `kick-back` | 🥁 |
| 3 | `kick-front` | 🥁 |
| 4 | `snare-top` | 🪘 |
| 5 | `snare-bottom` | 🪘 |
| 6 | `tom-high` | 🥁 |
| 7 | `tom-medium` | 🥁 |
| 8 | `floor-tom` | 🥁 |
| 9 | `hi-hat` | 🎩 |
| 10 | `crash` | 🔔 |
| 11 | `drum-kit` | 🥁 |
| 12 | `cowbell` | 🔔 |
| 13 | `bongos` | 🪘 |
| 14 | `congas` | 🪘 |
| 15 | `tambourine` | 🎵 |
| 16 | `vibraphone` | 🎵 |
| 17 | `electric-bass` | 🎸 |
| 18 | `acoustic-bass` | 🎸 |
| 19 | `contrabass` | 🎸 |
| 20 | `les-paul` | 🎸 |
| 21 | `ibanez` | 🎸 |
| 22 | `washburn` | 🎸 |
| 23 | `acoustic-guitar` | 🎸 |
| 24 | `bass-amp` | 🔊 |
| 25 | `guitar-amp` | 🔊 |
| 26 | `amp-cabinet` | 🔊 |
| 27 | `piano` | 🎹 |
| 28 | `organ` | 🎹 |
| 29 | `harpsichord` | 🎹 |
| 30 | `keyboard` | 🎹 |
| 31 | `synthesizer-1` | 🎹 |
| 32 | `synthesizer-2` | 🎹 |
| 33 | `synthesizer-3` | 🎹 |
| 34 | `keytar` | 🎹 |
| 35 | `trumpet` | 🎺 |
| 36 | `trombone` | 🎺 |
| 37 | `saxophone` | 🎷 |
| 38 | `clarinet` | 🎷 |
| 39 | `violin` | 🎻 |
| 40 | `cello` | 🎻 |
| 41 | `male-vocal` | 🎤 |
| 42 | `female-vocal` | 🎤 |
| 43 | `choir` | 👥 |
| 44 | `hand-sign` | ✋ |
| 45 | `talk-a` | 🗣 |
| 46 | `talk-b` | 🗣 |
| 47 | `large-diaphragm-mic` | 🎙 |
| 48 | `condenser-mic-left` | 🎙 |
| 49 | `condenser-mic-right` | 🎙 |
| 50 | `handheld-mic` | 🎤 |
| 51 | `wireless-mic` | 🎤 |
| 52 | `podium-mic` | 🎤 |
| 53 | `headset-mic` | 🎧 |
| 54 | `xlr` | 🔌 |
| 55 | `trs` | 🔌 |
| 56 | `trs-left` | 🔌 |
| 57 | `trs-right` | 🔌 |
| 58 | `rca-left` | 🔌 |
| 59 | `rca-right` | 🔌 |
| 60 | `tape` | 📼 |
| 61 | `fx` | ✨ |
| 62 | `computer` | 💻 |
| 63 | `wedge` | 🔊 |
| 64 | `speaker-right` | 🔈 |
| 65 | `speaker-left` | 🔉 |
| 66 | `speaker-array` | 🔊 |
| 67 | `speaker-on-pole` | 🔊 |
| 68 | `amp-rack` | 🎛 |
| 69 | `controls` | 🎛 |
| 70 | `fader` | 🎚 |
| 71 | `mix-bus` | 🔀 |
| 72 | `matrix` | 🔀 |
| 73 | `routing` | 🔀 |
| 74 | `smiley` | 😊 |

*Source: `mixing_station_icons.py` / [behringer-icons](https://github.com/pmaillot/Behringer-X32-Icons). Regenerate with `python3 tools/export_icon_tables.py ms`.*

## Appendix C: Hardware-validated preset → icon mapping

Firmware **v11749**, capture 2026-06-08. Other `(input_type, preset)` pairs must
be resolved via `getInputChannelPresetIconIdAtIndex` in the native library.

| Input type | Preset | Flow drawable | Flow UI label | MS ID | MS label |
| ---------- | ------ | ------------- | ------------- | ----- | -------- |
| 0 (Dynamic mic) | 4 | `input_icon_004` | Wired Mic | 50 | `handheld-mic` |
| 0 (Dynamic mic) | 7 | `input_icon_007` | Wired Mic | 50 | `handheld-mic` |
| 2 (Guitar / bass) | 2 | `input_icon_202` | Acoustic Guitar | 23 | `acoustic-guitar` |
| 3 (Line instrument) | 4 | `input_icon_304` | Violine | 39 | `violin` |
| 4 (Guitar page) | 2 | `input_icon_402` | Acoustic Guitar | 23 | `acoustic-guitar` |
| 5 (Playback / source) | 7 | `input_icon_507` | Record player | 60 | `tape` |

Drawable key formula: `type × 100 + preset`, zero-padded to three digits
(`input_icon_{key:03d}`).

*Maintained in `tools/flow8_icon_decode.py` (`PRESET_TO_MS_ICON`, `FLOW_UI_LABELS`). Regenerate with `python3 tools/export_icon_tables.py presets`.*

---

## Related documents

- [`03-bluetooth-le-protocol.md`](./03-bluetooth-le-protocol.md) — handshake, `0x37`/`0x38`
- [`04-channel-name-extraction.md`](./04-channel-name-extraction.md) — name slot layout
- [`02-sysex-dump-format.md`](./02-sysex-dump-format.md) — 7-bit packing (USB SysEx path)
