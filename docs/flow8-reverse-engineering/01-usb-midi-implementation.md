# 01 — USB MIDI Implementation

The FLOW 8 exposes a **class-compliant USB Audio + MIDI** device. No driver is
required on Linux; it binds to `snd-usb-audio` and appears as an ALSA raw-MIDI and
sequencer port named `FLOW 8`.

## USB enumeration

```
idVendor  = 0x1397   (Music Tribe / Behringer)
idProduct = 0x050C
Product   = "FLOW 8"
bcdDevice = 2.10
```

Confirm on Linux:

```bash
lsusb | grep -i 1397:050c
amidi -l                # raw MIDI ports
aconnect -l             # ALSA sequencer ports
cat /proc/asound/cards  # sound card index
```

## MIDI direction & scope

The FLOW 8's plain MIDI surface is **essentially one-way control input**: the host
sends **Control Change (CC)** and **Program Change (PC)** messages to move faders,
EQ, sends, FX, mutes, etc. The mixer does **not** continuously echo its parameter
state back as CC, and channel/snapshot **names are never transmitted over MIDI CC**.

### USB MIDI cannot retrieve channel names

Channel names are **not accessible via USB MIDI alone**. There is no USB MIDI SysEx
request that causes the mixer to transmit its state. The only way to read channel
names is over **Bluetooth LE** (see `03-bluetooth-le-protocol.md`):

- **Simplest path (BLE only):** send the `GetMixerState` command (`0x37`) and
  collect the `0x38` MixerState fragments — names are embedded as plain ASCII.
  No USB connection is needed.
- **Alternative path (BLE + USB):** send the `DumpTrigger` command (`0x4B`) over
  BLE; the mixer then emits a SysEx blob (`F0 00 20 32 21 … F7`) on the USB MIDI
  port, which can be captured with `amidi -r`.

This was confirmed by examining all MIDI/USB symbols in the official app's native
library (`libcom_musicgroup_xairbt.so`): the only USB references are audio routing
controls; there is no USB-side dump-request command.

The only bulk state the mixer emits over USB is the **SysEx state dump** described
in `02-sysex-dump-format.md`, and it is always triggered out-of-band over BLE.

## Channel addressing

Input channels and buses are addressed by **MIDI channel** (1–7 for the input
strips Ch1–Ch6 plus the USB/BT return on Ch7; buses use their own channels in the
reference client). The CC number selects *which parameter* on that channel. Values
are standard 7-bit (`0–127`).

> **Names vs MIDI channels:** BLE/SysEx expose **six** custom strip names (Ch1–4,
> Ch5+6, Ch7+8). USB capture channels 9–10 are Main L/R and are not named by the
> mixer. See [`04-channel-name-extraction.md`](./04-channel-name-extraction.md).

## Control Change map

Per-input-channel parameters (channel selected via MIDI channel):

| Parameter   | CC# | Type / range            |
| ----------- | --- | ----------------------- |
| EQ Low      | 1   | 0–127 (≙ −15..+15 dB)   |
| EQ Low-Mid  | 2   | 0–127                   |
| EQ Hi-Mid   | 3   | 0–127                   |
| EQ Hi       | 4   | 0–127                   |
| Mute        | 5   | 0 = off, ≥64 = on       |
| Solo        | 6   | 0 = off, ≥64 = on       |
| Level       | 7   | 0–127 (fader)           |
| Gain        | 8   | 0–127 (≙ −20..+60 dB)   |
| Low Cut     | 9   | 0–127 (≙ 20..600 Hz)    |
| Pan / Bal   | 10  | 0–127 (64 = center)     |
| Compressor  | 11  | 0–127                   |
| Phantom +48V | 12 | 0/127 (Ch1–Ch2 only)    |
| Send Mon 1  | 14  | 0–127                   |
| Send Mon 2  | 15  | 0–127                   |
| Send FX 1   | 16  | 0–127                   |
| Send FX 2   | 17  | 0–127                   |

Bus parameters (Main, Mon1, Mon2, FX1, FX2) reuse a similar layout:

| Parameter            | CC#    | Notes                          |
| -------------------- | ------ | ------------------------------ |
| Level                | 7      | bus master fader               |
| Limiter              | 8      | Main / FX buses                |
| Balance              | 10     | where applicable               |
| 9-band graphic EQ    | 11–19  | 62 Hz, 125, 250, 500 Hz, 1, 2, 4, 8, 16 kHz |

FX control:

| Parameter | Message            | Notes                    |
| --------- | ------------------ | ------------------------ |
| FX param 1 | CC 1              | per FX slot              |
| FX param 2 | CC 2              | per FX slot              |
| FX preset  | Program Change 0–15 | selects FX algorithm     |

> The exact CC numbers above match the reference client
> ([`abelroes/flow-8-midi`](https://github.com/abelroes/flow-8-midi)) and its
> calibration tooling. Behringer does not publish an official MIDI chart, so treat
> these as community-verified rather than vendor-documented.

## Sending CC from the shell

Once the device enumerates, you can drive it directly with ALSA tools. Example —
set Ch1 level (CC 7) to mid on MIDI channel 1 (`amidi` sends raw bytes;
`B0 07 40` = CC ch1, controller 7, value 64):

```bash
amidi -p hw:1,0,0 -S 'B0 07 40'
```

`B0` = Control Change on MIDI channel 1; change the low nibble for other channels
(`B1` = ch2, …). This only *sets* values — it does not read names. For names you
need the SysEx dump (next document).
