# Project Status — OpenMultiTrack

Maps the **original product specification** to what exists in the repo today.  
Update this file when milestones advance.

**Last reviewed:** 2026-06-10 · **App version:** see `gradle/version.properties`

**Documentation:** [docs/README.md](README.md) (developer hub)

---

## Summary

| Phase | Status |
|-------|-------|
| Architecture & developer docs | ✅ Restructured under `docs/` |
| DAW UI (multi-mixer, strips, waveforms) | ✅ |
| USB probe + Oboe/UAC2 dual backend | ✅ |
| Per-channel multitrack record + session.json | ✅ |
| Monitor + VU + live waveforms | ✅ |
| Virtual soundcheck (library, playback, seek, loop) | 🟡 Core shipped; seek polish ongoing |
| USB dropout → silence + resume | ✅ |
| Scribble strip (XR18 OSC, Flow 8 BLE/USB) | ✅ |
| LAN Android remote (Host/Remote) | ✅ |
| OSC routing snapshots | 🟡 Stubs in `X32Mixer` / `Xr18Mixer` |
| Browser web remote (Ktor) | ❌ Superseded by Android LAN sync |
| F-Droid official inclusion | 🟡 Self-hosted repo live; `fdroiddata` draft stale |

---

## Core features (original spec)

### 1. Multi-track recorder

| Requirement | Status | Notes |
|-------------|--------|-------|
| Capture all USB input channels | ✅ | Oboe or UAC2 path via `AudioEngineRouter` |
| XR18 18ch / X32 up to 32ch | 🟡 | Code supports up to 64; **validate on hardware** |
| 24-bit / 48 kHz WAV | ✅ | `PerChannelWavWriter` |
| Per-channel files | ✅ | One WAV per armed channel |
| Interleaved multichannel file | 🟡 | Legacy `WavWriter` only; new sessions per-channel |
| Sample-accurate sync | ✅ | Single clock domain; timeline includes silence gaps |
| Buffer overrun handling | 🟡 | Native drop counter; limited UI surfacing |
| USB disconnect handling | ✅ | Silence insertion + debounced resume |
| Long sessions (multi-hour) | 🟡 | No RF64; per-channel files mitigate size |
| Disk space monitoring | ❌ | Not implemented |
| FLAC / BWF | ❌ | Not implemented |

### 2. Virtual soundcheck (playback)

| Requirement | Status | Notes |
|-------------|--------|-------|
| Play session to mixer USB returns | ✅ | `VIRTUAL_SOUNDCHECK` + `SIMPLE_PLAY` modes |
| Channel N → USB return N | 🟡 | Assumes OS channel order; **UNVERIFIED** on all mixers |
| Sample-aligned seeking | 🟡 | `PerChannelWavReader.seekFrame` + engine; UX polish |
| Scrubbing / loop regions | 🟡 | Loop markers; scrub coalescing ongoing |
| Per-track solo/mute (monitoring) | ✅ | Strip solo in soundcheck |
| Transport UI | ✅ | `SoundcheckPanel`, play/pause/stop/seek |

### 3. Mixer targeting (X32 / XR18)

| Requirement | Status | Notes |
|-------------|--------|-------|
| `Mixer` interface | ✅ | `domain/mixer/Mixer.kt` |
| X32 / XR18 drivers | 🟡 | `connect()` + OSC send; snapshots **stub** |
| USB audio path | ✅ | Generic UAC2, not driver-specific |
| OSC UDP | 🟡 | Encode/send; feedback parser partial |
| Routing via OSC | ❌ | `applySnapshot` not implemented |
| Wired into app UI | 🟡 | Scribble import yes; snapshot UI no |

### 4. Snapshot / mode toggling

| Requirement | Status | Notes |
|-------------|--------|-------|
| Record / soundcheck routing snapshots | ❌ | Designed in [mixer-drivers.md](mixer-drivers.md) |
| Named storable snapshots | ❌ | `MixerSnapshot` type exists |
| One-tap mode switch | ❌ | App modes exist; OSC routing not automated |

### 5. Remote control

| Requirement | Status | Notes |
|-------------|--------|-------|
| Embedded server (FOSS) | ✅ | `remote-server` — NanoHTTPD |
| Responsive control UI | ✅ | Second Android app instance (Compose mirror) |
| WebSocket real-time sync | ✅ | [remote-control.md](remote-control.md) |
| Browser web UI | ❌ | Not pursued; [control-api.md](control-api.md) superseded |
| Documented protocol | ✅ | `RemoteProtocol` + remote-control doc |

---

## Module implementation matrix

| Module | Implemented | Gaps |
|--------|-------------|------|
| **app** | DAW UI, service, multi-mixer, remote wiring, scribble | Mixer IP connect UI, snapshot recall |
| **domain** | Models, `Mixer`, `AppMode`, remote constants | Full transport state machine |
| **usb-audio** | Enum, probe, router, Behringer IDs | Verified PID table expansion |
| **audio-engine** | Oboe + UAC2, record/play/monitor, rings | Native playback seek hardening |
| **session-io** | Per-channel WAV, metadata, waveforms, cues | RF64, FLAC, BWF |
| **mixer-behringer** | OSC, scribble, Flow 8 decoders | Snapshot apply/capture |
| **remote-server** | Host, client, discovery, codec | Protocol v2 if breaking changes |

---

## Test coverage

| Module | Tests | Gaps |
|--------|-------|------|
| `domain` | Unit | Transport models |
| `session-io` | Strong unit coverage | Multi-hour soak |
| `mixer-behringer` | OSC + scribble unit | Live snapshot round-trip |
| `usb-audio` | Identifier unit | Mock enumerator |
| `audio-engine` | UAC2 descriptor host + device tests | Oboe gtest harness |
| `remote-server` | JSON codec unit | — |
| `app` | JVM + many instrumented + E2E | CI does not run device tests |

See [development/testing.md](development/testing.md).

---

## CI / release infrastructure

| Item | Status |
|------|--------|
| PR CI (unit + native UAC2 + assembleDebug) | ✅ |
| Semver from conventional commits | ✅ |
| GitHub Releases + Pages F-Droid repo | ✅ |
| Pinned debug APK signing | ✅ |
| `remote-server` in CI unit tests | ✅ |
| Instrumented/E2E in CI | ❌ Hardware/LAN dependent |

---

## Milestone roadmap

### M1 — USB probe ✅

### M2 — Record / playback foundation ✅

- Per-channel WAV, DAW UI, monitor, multi-mixer

### M3 — Virtual soundcheck 🔄

- [x] Session library, playback, loop regions, waveforms
- [ ] Seek/scrub polish and hardware validation
- [ ] Disk space monitor
- [ ] RF64 or export formats for very long shows

### M4 — Mixer OSC integration

- [ ] `applySnapshot` / `captureSnapshot` on real X32/XR18
- [ ] Mixer connection UI, snapshot storage
- [ ] Feedback parser for verify paths

### M5 — Remote ✅ (Android LAN)

- [x] `remote-server`, Host/Remote roles, E2E tests
- [ ] Optional: revisit browser remote (product decision)

### M6 — F-Droid main repo

- [ ] Refresh `fdroiddata`, release/reproducible builds

---

## Known issues / tech debt

1. **Hardware assumptions unverified** on all target mixers — [hardware-assumptions.md](hardware-assumptions.md)
2. **`fdroiddata` recipe stale**
3. **Pre-0.2.2 APK signatures** — one-time uninstall for pinned key users
4. **OSC snapshots stubbed** despite scribble/routing research docs
5. **Debug-only CI publishes** — release signing deferred
6. **Documentation was stale** — addressed by 2026-06 docs restructure; keep [README.md](README.md) updated with code

---

## Honest assessment

OpenMultiTrack has moved well beyond the initial USB-probe vertical slice: it is a **usable DAW-style recorder** with per-channel sessions, soundcheck playback, scribble labels, USB dropout recovery, and **LAN remote control**. The main gaps versus the original vision are **OSC routing snapshots** (automated record ↔ soundcheck mixer routing), **disk space safety**, and **official F-Droid source inclusion**. Treat M4 (OSC snapshots) as the next differentiator for hands-free mixer mode switching.
