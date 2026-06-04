# Hardware & Protocol Assumptions

**Status**: UNVERIFIED on target hardware — validate before milestone 2 scaling.

## USB audio (UAC2)

| Assumption | Verification |
|------------|----------------|
| XR18 presents **18 input + 18 output** channels at 48 kHz over USB | Connect to Android tablet; run OpenMultiTrack probe; compare to Windows/macOS class-compliant mode |
| X32 with USB card presents **up to 32×32** (firmware/card dependent) | Same probe; note reduced counts on some hosts |
| Mixer is **USB clock master** | Oboe `getTimestamp()` drift vs local monotonic clock over 10+ minutes |
| Channel *N* USB send maps to mixer input *N* in record snapshot | Record sine on ch1, inspect WAV ch1 |
| Channel *N* USB return maps to mixer input *N* in soundcheck snapshot | Play sine on file ch1, meter mixer ch1 |

### Behringer USB identifiers (heuristic)

We flag devices with **vendor ID `0x1397`** (Behringer) and product name containing `X32`, `X18`, `XR18`, `UAX` as *likely* targets. Non-Behringer UAC2 devices may still work in generic mode.

## OSC control

| Assumption | Verification |
|------------|----------------|
| X32 OSC UDP port **10023** | `oscsend` / Wireshark |
| XR18 OSC UDP port **10024** | same |
| Routing via addresses like `/ch/01/in/src` (see mixer-drivers.md) | X32 Edit + packet capture while changing USB source |
| Snapshots via `/snapshots` or scene recall (model-specific) | Record user snapshot; dump OSC |

## Android platform

| Assumption | Verification |
|------------|----------------|
| `AAudioStreamBuilder_setDeviceId()` selects USB device (API 26+) | Probe returns >2 channels |
| `UsbManager` permission required before audio open | Deny/grant flow |
| Host mode OTG required; not all phones power XR18 | Powered USB hub test |

## What we do **not** assume (v1)

- HID control surface emulation
- MIDI over USB
- Proprietary Behringer Android drivers
- Network audio (AES67) instead of USB

## Verification checklist (lab)

```
[ ] XR18 + tablet: probe reports 18/18 @ 48 kHz
[ ] X32 + tablet: probe reports expected channel count
[ ] 2ch record 60s, zero frame slip between tracks (null test)
[ ] Playback seek ±1 hour, click-free aligned restart
[ ] Snapshot recall < 2s, routing confirmed on mixer display
```
