# Project Status — OpenMultiTrack

Maps the **original product specification** to what exists in the repo today.  
Update this file when milestones advance.

**Last reviewed:** 2026-06-15 · **App version:** see `gradle/version.properties`

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
| XR18 per-channel routing automation | ✅ | `RoutingOverrideCoordinator`, settings UI, E2E |
| `Mixer` API snapshots (`applySnapshot`) | 🟡 | Stub on `X32Mixer`/`Xr18Mixer`; per-channel routing is separate path |
| Mixer health + connectivity UI | ✅ |
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
| Disk space monitoring | 🟡 | Free-space + time estimate in toolbar; **no auto-stop** |
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
| Routing via OSC | ✅ | Per-channel automation via `RoutingOverrideCoordinator` |
| Wired into app UI | ✅ | Scribble import, routing automation settings, input-source screen |

### 4. Snapshot / mode toggling

| Requirement | Status | Notes |
|-------------|--------|-------|
| Record / soundcheck routing (per-channel) | ✅ | `RoutingAutomationHooksImpl` + restore policy |
| Soundcheck capture-only before USB | ✅ | OSC deferred until playback route open |
| Named `MixerSnapshot` slot recall | 🟡 | Snapshot **slot** recall in automation settings; full `Mixer` API still stub |
| One-tap mode switch | 🟡 | Transport-triggered automation; pre-apply on arm still open |

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
| **app** | DAW UI, service, multi-mixer, remote wiring, scribble, routing automation, health UI | Transport step UI, session v2, Flow 8 return matrix |
| **domain** | Models, `Mixer`, `AppMode`, remote constants, `MixerHealth` | Full transport state machine |
| **usb-audio** | Enum, probe, router, Behringer IDs, permission queue | Verified PID table expansion |
| **audio-engine** | Oboe + UAC2, record/play/monitor, rings | Native playback seek hardening |
| **session-io** | Per-channel WAV, metadata, waveforms, cues | RF64, FLAC, BWF, session v2 naming |
| **mixer-behringer** | OSC, scribble, Flow 8 decoders, XR18 routing OSC | `Mixer.applySnapshot` / `captureSnapshot` |
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

### M4 — Mixer OSC integration 🔄

- [x] Per-channel record/soundcheck routing automation (XR18/X-Air)
- [x] Routing automation settings UI + restore policy
- [x] Soundcheck capture-only routing (OSC after USB playback)
- [ ] `Mixer.applySnapshot` / `captureSnapshot` on `X32Mixer`/`Xr18Mixer` (legacy API)
- [ ] Pre-apply routing on arm / `routingReady` flags (see transport latency doc)
- [ ] Flow 8 USB return matrix settings

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
4. **`Mixer` snapshot API stubbed** — per-channel routing automation is the working path
5. **Debug-only CI publishes** — release signing deferred
6. **Flow 8 full icon preset table** — inferred in `Flow8IconPresets`; native extraction tool not automated

---

## Honest assessment

OpenMultiTrack is a **usable DAW-style recorder** with per-channel sessions, soundcheck playback, scribble labels, USB dropout recovery, LAN remote control, **XR18 per-channel routing automation**, and **mixer connectivity health UI**. Main gaps: **transport step visibility**, **session format v2**, **Flow 8 return-matrix UI**, **pre-apply routing on arm** (for sub-200 ms transport), **disk auto-stop**, and **official F-Droid source inclusion**.
