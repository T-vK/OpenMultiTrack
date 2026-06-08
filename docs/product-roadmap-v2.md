# OpenMultiTrack Product Roadmap v2

Professional multitrack recording and virtual soundcheck for Android tablets.

## Goals

| Goal | Detail |
|------|--------|
| Reliability | Recording survives USB glitches, backgrounding, crashes; gaps = silence |
| Multi-mixer | Mixer dropdown per interface; user-curated device list |
| Per-channel files | `channel01.wav` in `MixerName-Serial/YYYY-MM-DD-HH-mm-ss/` |
| DAW UI | Vertical strips: arm, monitor, solo, waveform, rename, color |
| Soundcheck | Library, seek, smart waveforms, loop region, W64 + WAV dirs + MP3 |
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

- **XR18/X32:** UDP OSC port 10024; `/routing/usb/NN/src` + strip `config/name` + `config/color`
- **Flow 8:** BLE manual import only (single client)
- Auto on first mixer add / after USB probe; manual refresh in menu
- **Never write** back to mixer

## Virtual soundcheck loop

- User selects a **loop region** (in/out markers) on the timeline
- Playback repeats the selected section until loop is disabled
- Works alongside seek and transport (play/pause/stop)

## Channel strip visibility settings

Global settings (Settings sheet) to simplify the UI on small screens or during soundcheck:

| Setting | Effect |
|---------|--------|
| Hide record arm button | Per-channel arm toggle hidden |
| Hide monitor button | Per-channel monitor toggle hidden |
| Hide solo button | Per-channel solo toggle hidden |
| Show waveforms | When off, waveform area is not rendered (saves GPU/CPU) |

## Implementation phases (priority order)

| Phase | Scope | Status |
|-------|--------|--------|
| 0 | Bug fixes, device labels, auto-probe, USB silence, monitor gain | Done |
| 1 | MixerDeviceStore, per-channel WAV, session dirs, DAW toolbar | Done |
| 2 | Live waveforms, responsive strip height, record/monitor stability | Done |
| **3** | **Scribble strip OSC (XR18/X32), channel label/color import** | **In progress** |
| **4** | **Soundcheck library, playback, seek, loop region** | Next |
| 5 | Waveform peak cache for playback files |
| 6 | W64, MP3 export |
| 7 | Flow 8 BLE scribble (manual import) |
| 8 | Settings import/export, crash resume, polish |

## Phase 4 detail — Soundcheck library

- Session browser under `Soundcheck` mode (per mixer folder)
- Play/pause/stop, scrub/seek on timeline
- **Loop:** set loop in/out, toggle loop playback for practice
- Route playback to monitor output device (same as live monitor)

See also: `docs/architecture.md`, `docs/xr18-scribble-strip/`, `docs/flow8-reverse-engineering/`.
