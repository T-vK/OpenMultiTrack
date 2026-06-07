# Project Status — OpenMultiTrack

Maps the **original product specification** to what exists in the repo today.  
Update this file when milestones advance. For agent onboarding, see [AGENTS.md](AGENTS.md).

**Last reviewed:** 2026-06-07 · **App version:** see `gradle/version.properties` (currently **0.2.3**)

---

## Summary

| Phase | Status |
|-------|--------|
| Architecture & docs | ✅ Complete (living documents) |
| Project skeleton + Oboe + USB probe | ✅ Complete |
| Multichannel record + basic playback | ✅ Initial (interleaved WAV, no seek UI) |
| Virtual soundcheck (seek, transport, routing) | ❌ Not started |
| Mixer OSC snapshots | 🟡 Stubs only |
| Web remote | ❌ Not started |
| F-Droid official inclusion | 🟡 Self-hosted repo live; `fdroiddata` draft stale |

---

## Core features (original spec)

### 1. Multi-track recorder

| Requirement | Status | Notes |
|-------------|--------|-------|
| Capture all USB input channels | ✅ | Channel count from Oboe probe; `RecordingChannels` caps at 64 |
| XR18 18ch / X32 up to 32ch | 🟡 | Code supports N channels; **not validated on real hardware** |
| 24-bit / 48 kHz WAV | ✅ | `WavWriter` / `WavReader`; sample rate from probe |
| 48 kHz minimum | ✅ | Default `AudioConstants.DEFAULT_SAMPLE_RATE = 48000` |
| FLAC option | ❌ | Not implemented |
| BWF metadata | ❌ | Plain WAV only |
| Per-track files | ❌ | Single interleaved file per session |
| Interleaved multichannel file | ✅ | Only format implemented |
| Sample-accurate sync across tracks | 🟡 | Single interleaved stream — inherently aligned; per-track not tested |
| Buffer overrun/underrun handling | 🟡 | Drop counter in native recorder; limited exposure to UI |
| USB disconnect handling | 🟡 | USB attach/detach refreshes list; no graceful record stop on disconnect |
| Long sessions (multi-hour) | ❌ | No RF64 (>4 GB), no seek index, no soak testing |
| Disk space monitoring | ❌ | No pre-stop when storage low |
| Configurable layout (per-track vs interleaved) | ❌ | Interleaved only |

**Key files:** `SessionRecorder.kt`, `audio_recorder.cpp`, `WavWriter.kt`, `RecordingChannels.kt`

---

### 2. Virtual soundcheck (playback)

| Requirement | Status | Notes |
|-------------|--------|-------|
| Play session to mixer USB returns | 🟡 | Plays interleaved WAV to Oboe **output**; routing to mixer channels not OSC-controlled |
| Channel N → USB return N | 🟡 | Assumes OS USB channel order matches mixer; **UNVERIFIED** |
| Sample-aligned seeking | ❌ | `WavReader.seekFrame()` exists; engine/UI seek not wired |
| Scrubbing / jump-to-timecode | ❌ | No UI |
| Loop regions / in-out markers | ❌ | Domain enums exist (`TransportState`); not implemented |
| Per-track solo/mute/gain (monitoring) | ❌ | Not implemented |
| Transport: play/pause/stop/seek/loop | 🟡 | Play/stop only in UI; `playbackPositionFrames` in state but unused in UI |

**Key files:** `SessionPlayer.kt`, `audio_player.cpp`, `WavReader.kt`, `MainViewModel.kt`

---

### 3. Mixer targeting (X32 / XR18)

| Requirement | Status | Notes |
|-------------|--------|-------|
| `Mixer` interface abstraction | ✅ | `domain/.../Mixer.kt` |
| X32 driver | 🟡 | `connect()` sends `/info`; snapshots **stub** |
| XR18 driver | 🟡 | Same as X32 |
| USB audio path | ✅ | Via `usb-audio` + Oboe (not driver-specific) |
| OSC control (UDP 10023 / 10024) | 🟡 | `OscUdpClient` encode + send; no feedback parser |
| Routing via OSC | ❌ | `applySnapshot` → `UnsupportedOperationException` |
| Wired into app UI | ❌ | Module is dependency-only |

**Key files:** `X32Mixer.kt`, `Xr18Mixer.kt`, `OscUdpClient.kt`, `docs/mixer-drivers.md`

---

### 4. Snapshot / mode toggling

| Requirement | Status | Notes |
|-------------|--------|-------|
| Record mode snapshot (inputs → USB) | ❌ | OSC command sequences designed in docs, not implemented |
| Soundcheck mode snapshot (USB returns → channels) | ❌ | Same |
| Named storable snapshots | ❌ | `MixerSnapshot` type exists |
| Mixer ack / verify | ❌ | `verifyPaths` in model; no implementation |
| One-tap mode switch in app | ❌ | No UI |

---

### 5. Remote control + web interface

| Requirement | Status | Notes |
|-------------|--------|-------|
| Embedded web server (FOSS) | ❌ | `remote-server` module not created; Ktor planned |
| Responsive web UI | ❌ | Not started |
| WebSocket real-time sync | ❌ | API drafted in `control-api.md` |
| Offline / no CDN | — | Constraint documented; nothing to ship yet |
| Shared `ControlService` backend | ❌ | Interface sketched in docs only |
| Documented control API | 🟡 | Draft: `docs/control-api.md` |

---

## Architecture & engineering deliverables

| Deliverable | Status | Location |
|-------------|--------|----------|
| Architecture document | ✅ | `docs/architecture.md` |
| Technical risk assessment | ✅ | `docs/technical-risks.md` |
| Mixer driver design | ✅ | `docs/mixer-drivers.md` |
| Hardware assumptions | ✅ (UNVERIFIED) | `docs/hardware-assumptions.md` |
| Project skeleton | ✅ | 6 Gradle modules |
| USB enumeration + channel probe | ✅ | Milestone 1 vertical slice |
| Unit / instrumentation tests | 🟡 | 7 JVM test files; no native/UI/integration tests |
| F-Droid metadata | 🟡 | `fastlane/`, `fdroid/metadata/`, stale `fdroiddata/` |
| Reproducible build notes | ✅ | `docs/reproducible-builds.md` |
| CI/CD | ✅ | `ci.yml`, `publish.yml`, Pages F-Droid repo |

---

## F-Droid compliance checklist

| Rule | Status |
|------|--------|
| No Play Services / Firebase / GMS | ✅ Enforced in CI |
| FOSS dependencies only | ✅ |
| No tracking / analytics | ✅ |
| Buildable from source | ✅ |
| GPLv3 license file | ✅ |
| fastlane metadata | ✅ |
| fdroiddata build recipe | 🟡 Draft outdated (`0.1.0-m1`) |
| Reproducible release APK | ❌ Debug-only publishes so far |
| Self-hosted binary repo | ✅ GitHub Pages |

---

## Module implementation matrix

| Module | Implemented | Stub / missing |
|--------|-------------|----------------|
| **app** | Single-screen Compose, USB permission, probe/record/play UI | Navigation, seek bar, mixer UI, settings |
| **domain** | Models, `Mixer` interface, `RecordingChannels` | Full transport state machine, session repository |
| **usb-audio** | Enumeration, permission, probe service, Behringer heuristics | Verified PID table, disconnect callbacks |
| **audio-engine** | Oboe probe/record/play, SPSC rings, JNI | Seek/flush, meters, per-track deinterleave, underrun to Kotlin |
| **session-io** | 24-bit WAV read/write, `seekFrame()` | BWF, RF64, FLAC, per-track files, index |
| **mixer-behringer** | OSC encode, `/info` ping, `connect()` | `applySnapshot`, `captureSnapshot`, `sendOsc`, `feedback()` |
| **remote-server** | — | Entire module |

---

## Test coverage

| Module | Tests | Gaps |
|--------|-------|------|
| `domain` | `RecordingChannelsTest`, `OscPathTest`, `SemverScriptTest` | Transport, session models |
| `session-io` | `WavRoundTripTest` (2ch) | Multichannel, large files, seek |
| `mixer-behringer` | `OscUdpClientTest`, `OscPathTest` | Live UDP, snapshot round-trip |
| `usb-audio` | `BehringerUsbIdentifiersTest` | Enumerator with mock `UsbManager` |
| `audio-engine` | — | Native tests needed |
| `app` | — | Compose/UI/integration |

---

## CI / release infrastructure

| Item | Status |
|------|--------|
| PR CI (test + assembleDebug) | ✅ |
| Semver from conventional commits | ✅ |
| GitHub Releases | ✅ Pre-releases, debug APK |
| GitHub Pages F-Droid repo | ✅ |
| Pinned debug APK signing | ✅ `keystore/debug.keystore` |
| Same APK for F-Droid + GitHub Release | ✅ Single `build` job artifact |
| Gradle/SDK/NDK caching | ✅ |
| FOSS dependency grep | ✅ |

---

## Milestone roadmap (suggested)

### M2 — Record/playback foundation ✅ (initial)

- [x] Native Oboe recorder + player
- [x] 24-bit WAV writer/reader
- [x] Multichannel (probed count, not hardcoded 2)
- [x] Compose record/play controls
- [ ] USB disconnect during record → graceful finalize

### M3 — Virtual soundcheck transport 🔄 **NEXT**

- [ ] Native playback seek + flush (sample-aligned)
- [ ] Position callback to Kotlin
- [ ] Transport UI (timeline, play/pause/stop/seek)
- [ ] Loop region support
- [ ] Disk space monitor
- [ ] RF64 or split files for >4 GB sessions

### M4 — Mixer OSC integration

- [ ] OSC feedback parser (UDP receive)
- [ ] Implement `applySnapshot` / `captureSnapshot` for X32
- [ ] Port XR18 command map (validate on hardware)
- [ ] Snapshot storage (local DB or files)
- [ ] Mixer connection UI (IP, model select)
- [ ] Verify routing on real console

### M5 — Web remote

- [ ] Create `remote-server` module (Ktor CIO)
- [ ] Implement `ControlService` in domain/app
- [ ] REST + WebSocket per `control-api.md`
- [ ] Self-contained HTML/JS/CSS (no CDN)
- [ ] Optional bearer token auth

### M6 — F-Droid main repo

- [ ] Refresh `fdroiddata/org.openmultitrack.yml` for current version
- [ ] Release signing (or document debug exception)
- [ ] Reproducible build verification
- [ ] Hardware validation notes for maintainers

---

## Known issues / tech debt

1. **Hardware assumptions unverified** — channel counts, USB routing, OSC paths ([hardware-assumptions.md](hardware-assumptions.md)).
2. **`fdroiddata` recipe stale** — still references `0.1.0-m1` / old commit.
3. **Pre-0.2.2 APK signatures differ** — users must uninstall once before pinned-key builds (documented in [ci-and-releases.md](ci-and-releases.md)).
4. **No `remote-server` module** — architecture diagram shows it; not in `settings.gradle.kts`.
5. **`domain` vs `mixer-behringer` OscPath** — duplicate test coverage; consolidate when touching OSC.
6. **Playback position not shown** — `MainUiState.playbackPositionFrames` unused in UI.
7. **Release build type** — `release` buildType exists with minify; not used in CI (debug only by design for now).

---

## Original mission reference

The project was commissioned as a **production-grade FOSS Android multitrack recorder** for live bands with Behringer X32/XR18 mixers, F-Droid compliant, with:

- Native Oboe audio path
- OSC mixer control
- Embedded web remote
- Sample-accurate multitrack record/playback/seek

Full original agent brief: see [AGENTS.md — Mission](AGENTS.md#mission-original-brief).

**Current honest assessment:** The **thin vertical slice** (USB → probe → multichannel WAV record → basic play) is working. The differentiating features — **seek**, **OSC routing snapshots**, and **web remote** — are designed but not implemented. Treat M3 as the critical path for user-visible “virtual soundcheck” value.
