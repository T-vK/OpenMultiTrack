# Product roadmap

Phased delivery for OpenMultiTrack. For exact implementation status, cross-check [../PROJECT_STATUS.md](../PROJECT_STATUS.md).

## Vision

Professional **multitrack recording and virtual soundcheck** on Android tablets: reliable long-form capture, per-channel files, DAW-style UI, LAN remote, and OSC mixer integration — all FOSS and F-Droid compatible.

---

## Completed (major themes)

- Gradle multi-module skeleton, Oboe native engine, CI + F-Droid self-hosted repo
- USB enumeration, permission flow, Oboe + UAC2 dual backend routing
- DAW Compose UI: channel strips, arm/monitor/solo, VU, live waveforms
- Per-channel 24-bit WAV sessions with `session.json`
- Multi-mixer profiles and shared capture registry
- Virtual soundcheck: session library, playback, seek (ongoing polish), loop regions
- Scribble strip import (XR18 OSC, Flow 8 BLE/USB)
- LAN Android remote sync (Host/Remote roles)
- USB dropout silence insertion and resume (interrupted recording)
- Extensive unit + hardware-tagged instrumented tests

---

## In progress / polish

| Area | Work remaining |
|------|----------------|
| **Transport UX** | Sample-aligned seek refinement, position display, scrubbing coalescing |
| **OSC snapshots** | `applySnapshot` / `captureSnapshot` for record ↔ soundcheck routing |
| **Mixer UI** | Connect X32/XR18 by IP, snapshot recall in app |
| **Disk space** | Pre-stop when storage low |
| **Hardware validation** | XR18 18ch, X32 32ch on production tablets |
| **F-Droid official** | Refresh `fdroiddata` recipe, release/reproducible build story |

---

## USB dropout behavior (spec)

1. USB detach → banner "No USB audio"; writers insert silence at native rate
2. Debounce ~400 ms before tearing down native capture
3. Reattach → reopen stream; append to same session files
4. Monitor routing stays hot where possible

---

## Waveform strategy

| Level | Resolution | Use |
|-------|------------|-----|
| 0 | ~10 s/peak | Full overview |
| 1 | ~1 s/peak | Default 5 min window |
| 2 | ~100 ms/peak | Zoomed playback |
| 3 | ~10 ms/peak | Live record (short window) |

UI and remote protocol read peaks only.

---

## Scribble strip

| Source | Mechanism |
|--------|-----------|
| XR18/X32 | UDP OSC; LAN discovery |
| Flow 8 | BLE manual import or USB state decode |

Auto-import on mixer add / after probe; manual refresh in menu. **Never write** to mixer.

---

## Channel strip visibility (settings)

| Setting | Effect |
|---------|--------|
| Hide record arm | Arm toggle hidden |
| Hide monitor | Monitor toggle hidden |
| Hide solo | Solo toggle hidden |
| Show waveforms off | Skip waveform rendering (CPU/GPU savings) |

---

## Future phases (not scheduled)

| Phase | Description |
|-------|-------------|
| **OSC routing snapshots** | One-tap record vs soundcheck mixer routing |
| **W64 / RF64 / FLAC** | Large sessions and export formats |
| **Browser web remote** | Only if product revisits Ktor/HTML approach ([control-api.md](../control-api.md) draft) |
| **Additional consoles** | Midas M32, other OSC maps — after abstraction proven |
| **F-Droid main repo** | Upstream inclusion with reproducible release APK |

---

## Non-goals

- Cloud backup or collaboration
- MIDI sequencing or VST hosting
- Proprietary mixer SDKs
- Writing scribble back to hardware

---

## Historical note

An earlier roadmap draft lives at [../product-roadmap-v2.md](../product-roadmap-v2.md) (mentioned a separate `waveform/` module — waveform logic shipped in `app` + `session-io` instead).
