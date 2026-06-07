# Goals and Constraints

## Primary goals

1. **Multichannel USB capture** — Record N interleaved PCM channels (target: 10 Flow 8, 18 XR18, up to 32 X32) at device-native sample rate (typically 48 kHz) and bit depth (16/24-bit).
2. **Multichannel USB playback** — Play interleaved PCM to mixer USB returns for virtual soundcheck (stereo minimum, up to device max).
3. **Class-compliant, mixer-agnostic** — One engine for all UAC2 mixers; no Behringer-specific kernel modules.
4. **FOSS / F-Droid** — Apache-2.0 / GPL-3.0 compatible stack only; no proprietary SDKs (eXtream, etc.).
5. **Oboe fallback** — When Android already exposes full channel count via AAudio, use existing Oboe path to avoid double-claiming USB.

## Success criteria (MVP)

| Criterion | Target |
|-----------|--------|
| Flow 8 Recording mode | ≥10 input channels captured to interleaved WAV |
| XR18 18ch mode | ≥18 input channels |
| Playback | 2ch return minimum; multichannel when device supports |
| Latency | Playback buffer configurable; record drop counter surfaced |
| Graceful degrade | Clear UI: "UAC2: 18ch" vs "Oboe: 2ch (fallback)" |

## Constraints

- **minSdk 26**, **NDK r26d**, **C++17** — match existing `audio-engine`.
- **No root required** — must work on stock Android with `UsbManager` permission.
- **No Play Services** — unchanged FOSS policy.
- **Real-time safety** — isochronous callback threads: no allocations, no locks with UI thread.
- **Single USB audio claim** — cannot have kernel `snd-usb-audio` and userspace lib both streaming same interface; must coordinate or bypass.

## Non-goals (v1)

- USB MIDI, HID control surfaces, network audio.
- Bit-perfect DSD / MQA playback.
- Sample-rate conversion inside omt-uac2 (resample in separate layer if needed).
- Windows/macOS/Linux desktop ports (Android-first; keep POSIX-friendly C++ for possible reuse).
- Replacing Oboe entirely — Oboe remains for built-in mic and fallback USB.

## Maintenance philosophy

- **Spec-first, quirks-second** — parse UAC2 descriptors per USB spec; add a small `quirks.cpp` table when hardware deviates (mirroring Linux `quirks-table.h` pattern).
- **Reference Linux, don't run it** — port algorithms and quirk knowledge from `sound/usb/`, not load kernel modules.
- **Log richly in debug** — descriptor dumps, alt-setting choice, per-transfer stats (existing `OmtLog` / `audio_log.h`).
