# 01 — Scribble Strip Access

The XR18 stores each input channel's **scribble-strip name** (string) and
**color** (integer 0–15) as standard OSC parameters. Query them like any other
desk value.

## OSC paths

### Scribble strip

| Parameter | Path | Type | Notes |
| --------- | ---- | ---- | ----- |
| Channel name | `/ch/NN/config/name` | string | `NN` = `01` … `16` only |
| Channel color | `/ch/NN/config/color` | int | 0–15, see color table below |

Other strips Mixing Station can rename (same `config/name` + `config/color` pattern):

| Strip | OSC base |
| ----- | -------- |
| Mix buses 1–6 | `/bus/N/config` |
| FX sends 1–4 | `/fxsend/N/config` |
| FX returns 1–4 | `/rtn/N/config` |
| Aux / USB playback | `/rtn/aux/config` |
| Main L/R | `/lr/config` |

There is **no** `/ch/17` or `/ch/18` on the XR18 — the desk has 16 input strips but
**18×18 USB audio**. USB channels 17–18 route **AuxL** / **AuxR**; their scribble
label lives under `/rtn/aux/config` (e.g. `Playback_55` in Mixing Station).

### USB record routing

| USB channel | Typical source (`/routing/usb/NN/src`) | Name/color from |
| ----------- | -------------------------------------- | --------------- |
| 1–16 | Ch01–Ch16 | `/ch/NN/config` |
| 17 | AuxL | `/rtn/aux/config` (L) |
| 18 | AuxR | `/rtn/aux/config` (R) |

The tool reads `/routing/usb/01`–`/18/src`, resolves the source to the correct OSC
strip, and prints name + color. If routing points at a bus, FX return, main L/R,
etc., that strip's label is shown instead.

Example queries (no arguments — desk replies with current value):

```
/ch/01/config/name
/ch/01/config/color
```

Example replies:

```
/ch/01/config/name ,s "E-Bass_17"
/ch/01/config/color ,i 10
```

## Color index

| Index | Label | Index | Label |
| ----- | ----- | ----- | ----- |
| 0 | OFF | 8 | OFFi |
| 1 | RD | 9 | RDi |
| 2 | GN | 10 | GNi |
| 3 | YE | 11 | YEi |
| 4 | BL | 12 | BLi |
| 5 | MG | 13 | MGi |
| 6 | CY | 14 | CYi |
| 7 | WH | 15 | WHi |

## Method A — Network OSC (recommended)

**Requirements:** Host and XR18 on the same network (Ethernet or Wi‑Fi). UDP port
**10024** must be reachable (no special mixer setting).

Works regardless of **USB-DIN Pass Thru** — network control is independent of USB
MIDI routing.

### Discover the mixer IP

Broadcast `/xinfo` to UDP port 10024:

```bash
python3 docs/xr18-scribble-strip/tools/xr18_scribble_osc.py
```

The script sends `/xinfo` and uses the source address of the reply. Typical
response:

```
/xinfo ,ssss 192.168.3.63 XR18-63-54-69 XR18 1.18
```

Manual alternative with `socat`:

```bash
echo -n '/xinfo' | socat - UDP-DATAGRAM:255.255.255.255:10024,broadcast
```

Other discovery options: router DHCP lease list, `/status` reply
(`active`, IP, mixer name), or the IP shown in X Air Edit / Mixing Station.

### Query USB channel labels

The tool does not query `/ch/17` or `/ch/18` (those paths do not exist). Instead it:

1. Queries `/routing/usb/01`–`/18/src` to see what each USB channel carries
2. Queries the matching strip (`/ch/`, `/bus/`, `/rtn/aux/`, `/lr/`, etc.)
3. Prints one row per USB audio channel

```bash
python3 docs/xr18-scribble-strip/tools/xr18_scribble_osc.py --ip 192.168.3.63
```

Example output:

```
USB  Source    Name                      Col  Color
----------------------------------------------------
  1  Ch01      E-Bass_17                  10  GNi
 ...
 17  AuxL      Playback_55 (L)            12  BLi
 18  AuxR      Playback_55 (R)            12  BLi
```

### Subscribe to live updates (optional)

For ongoing changes (e.g. user renames a channel in Mixing Station), send `/xremote`
every ~9 seconds. The desk then pushes OSC updates for changed parameters until the
timer expires.

## Method B — USB MIDI OSC SysEx (optional)

OSC commands can also be sent inside MIDI System Exclusive on the USB port:

```
F0 00 20 32 32 <osc-command-as-ascii> F7
```

Example — query channel 1 name:

```
F0 00 20 32 32 2F 63 68 2F 30 31 2F 63 6F 6E 66 69 67 2F 6E 61 6D 65 F7
```

Replies come back as SysEx with the same `00 20 32 32` wrapper (text or binary OSC).

### MIDI config requirements

USB MIDI OSC only works when the mixer **processes** USB MIDI (not pass-through
mode). In **X Air Edit → Setup → Mixer → MIDI Config**:

| Setting | Required |
| ------- | -------- |
| USB Rx | ON |
| USB Tx | ON |
| USB X/OSC | ON |
| USB-DIN Pass Thru | **OFF** |

`/-prefs/midiconfig` bitmask: USB Rx=8, USB Tx=16, USB X/OSC=32 → **56** (no bit 6).

Pass thru can be disabled over the network without touching USB MIDI:

```bash
echo -n '/-prefs/midiconfig ,i 56' | socat - UDP:192.168.3.63:10024
```

### Pass thru trade-off

With **USB-DIN Pass Thru ON**, USB MIDI is a DIN↔USB bridge and the desk ignores
all MIDI (including SysEx OSC). With it **OFF**, USB OSC queries work but transparent
DIN↔USB routing for DAW/VST setups stops. See project chat notes for implications.

### Linux raw device

When USB X/OSC is enabled, the ALSA port is typically:

```bash
amidi -l
# IO  hw:2,0,0  X18/XR18 MIDI 1
# /dev/snd/midiC2D0
```

A longer USB-MIDI probe script lives at `scripts/tmp/xr18_scribble_midi.py` (temporary).

## What does *not* work

| Approach | Result |
| -------- | ------ |
| `/ch/17/config/name`, `/ch/18/...` | **No reply** — only 16 input strips; use `/rtn/aux/config` for USB 17–18 |
| Plain MIDI CC / Program Change | Faders, mutes, snapshots only — **no names** |
| USB audio descriptors | No IP or scribble data |
| Mixing Station UI | No MIDI-config / pass-thru screen (use X Air Edit or OSC) |
| Network + pass thru on | **Works** — pass thru does not block network OSC |

## Tooling

| Script | Transport | Notes |
| ------ | --------- | ----- |
| `tools/xr18_scribble_osc.py` | UDP OSC | **Use this** — stdlib only, auto-discover |
| `scripts/tmp/xr18_scribble_midi.py` | USB MIDI SysEx | Experimental; needs pass-thru off |
