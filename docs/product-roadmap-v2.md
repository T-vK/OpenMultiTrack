# OpenMultiTrack Product Roadmap v2

> **Superseded** by [product/roadmap.md](product/roadmap.md).  
> Kept for historical context. Some items below (e.g. separate `waveform/` module) were implemented differently.

---

Original content preserved below.

## Goals

| Goal | Detail |
|------|--------|
| Reliability | Recording survives USB glitches, backgrounding, crashes; gaps = silence |
| Multi-mixer | Mixer dropdown per interface; user-curated device list |
| Per-channel files | `channel01.wav` in `MixerName-Serial/YYYY-MM-DD-HH-mm-ss/` |
| DAW UI | Vertical strips: arm, monitor, solo, waveform, rename, color |
| Soundcheck | Library, seek, smart waveforms, loop region, W64 + WAV dirs + MP3 |
| Scribble | OSC (XR18/X32), BLE (Flow 8, manual); read-only |

## Module map (historical)

```
app/           Compose UI, settings, log viewer, MixerDeviceStore
domain/        MixerProfile, ChannelStrip, SessionLayout, Transport
session-io/    PerChannelWavWriter, W64, session.json, recovery
waveform/      Multi-resolution peak cache, live ring buffer  ← never split out; lives in app + session-io
usb-audio/     USB enum, probe, router, AudioOutputDeviceLabel
audio-engine/  Oboe + UAC2 + monitor output
mixer-behringer/ OSC discovery, scribble strip clients
remote-server/ LAN sync (added after this draft)
```

For current module docs see [modules/](modules/).
