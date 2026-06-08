# OpenMultiTrack Product Roadmap v2

Professional multitrack recording and virtual soundcheck for Android tablets.

## Goals

| Goal | Detail |
|------|--------|
| Reliability | Recording survives USB glitches, backgrounding, crashes; gaps = silence |
| Multi-mixer | Independent tabs per interface; user-curated device list |
| Per-channel files | `channel01.wav` in `MixerName-Serial/YYYY-MM-DD-HH-mm-ss/` |
| DAW UI | Vertical strips: arm, monitor, solo, waveform, rename, color |
| Soundcheck | Library, seek, smart waveforms, W64 + WAV dirs + MP3 |
| Scribble | OSC (XR18/X32), BLE (Flow 8, manual); read-only |

## Module map

```
app/           Compose UI, settings, log viewer, MixerDeviceStore
domain/        MixerProfile, ChannelStrip, SessionLayout, Transport
session-io/    PerChannelWavWriter, W64, session.json, recovery
waveform/      Multi-resolution peak cache, live ring buffer
usb-audio/     USB enum, probe, router, AudioOutputDeviceLabel
audio-engine/  Oboe + UAC2 + monitor output
mixer-behringer/ OSC discovery, scribble strip clients
```

## Session directory layout

```
{storageRoot}/{MixerName}-{Serial}/{YYYY-MM-DD-HH-mm-ss}/
  session.json
  channel01.wav
  channel02 - Guitar Frank.wav
  .waveforms/channel01/level*.peaks
```

## USB dropout behavior

1. Detach → banner "No USB audio", write silence at native rate
2. Debounce 400 ms before tearing down native capture
3. Reattach → reopen stream, resume append to same files
4. Monitor: hot-routed; solo/arm changes without restart

## Waveform strategy

| Level | Resolution | Use |
|-------|------------|-----|
| 0 | ~10 s/peak | Full-file overview |
| 1 | ~1 s/peak | Default playback window (5 min) |
| 2 | ~100 ms/peak | Zoomed playback |
| 3 | ~10 ms/peak | Live record (15 s window) |

UI reads peaks only — never decodes PCM on main thread.

## Scribble strip

- **XR18/X32:** UDP OSC port 10024; `/ch/NN/config/name`, `/ch/NN/config/color`
- **Flow 8:** BLE manual import only (single client)
- Auto on first mixer add; prompt on new recording; manual refresh button
- **Never write** back to mixer

## Implementation phases

| Phase | Scope |
|-------|--------|
| 0 | Bug fixes, device labels, auto-probe, USB silence, monitor gain |
| 1 | MixerDeviceStore, multi-tab, per-channel WAV, session dirs |
| 2 | DAW channel strips, live waveforms |
| 3 | Waveform cache, playback seek, soundcheck library |
| 4 | W64, MP3, scribble OSC/BLE |
| 5 | Settings import/export, profiles, polish |

See also: `docs/architecture.md`, `docs/xr18-scribble-strip/`, `docs/flow8-reverse-engineering/`.
