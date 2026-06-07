# AI Agent Guide — OpenMultiTrack

**Read this file first** before making changes. It orients new contributors (human or AI) to the mission, repo layout, constraints, current state, and safe working practices.

| If you need… | Read |
|--------------|------|
| What is done vs missing (mapped to original spec) | [PROJECT_STATUS.md](PROJECT_STATUS.md) |
| Layers, data flow, threading | [architecture.md](architecture.md) |
| USB/OSC hardware assumptions to verify | [hardware-assumptions.md](hardware-assumptions.md) |
| X32/XR18 OSC address maps | [mixer-drivers.md](mixer-drivers.md) |
| Risks and mitigations | [technical-risks.md](technical-risks.md) |
| CI, releases, F-Droid repo, signing | [ci-and-releases.md](ci-and-releases.md) |
| Reproducible builds / toolchain pins | [reproducible-builds.md](reproducible-builds.md) |
| Draft remote HTTP/WS API | [control-api.md](control-api.md) |

---

## Mission (original brief)

Build an **open-source Android app** for:

1. **Live multitrack recording** — capture all USB input channels from a Behringer X32/XR18 (or compatible UAC2 mixer) as a synchronized session.
2. **Virtual soundcheck** — play recordings back to individual mixer USB returns with **sample-aligned seeking** across long sessions (full concerts).

Additional goals: **OSC mixer control** (routing snapshots), **embedded web remote** (FOSS, offline), **F-Droid inclusion**.

Target mixers (initial): **Behringer X32** (OSC UDP port **10023**), **XR18** (port **10024**). Audio is always **USB Audio Class 2**; control is **OSC over UDP**.

---

## Hard constraints (non-negotiable)

These must never be violated without explicit user approval:

| Rule | Enforcement |
|------|-------------|
| **No Google Play Services / Firebase / GMS / ads / telemetry** | `ci.yml` greps `:app:dependencies` for forbidden packages |
| **FOSS-only dependencies** | Apache-2.0 / MIT / BSD / GPL-compatible; Oboe submodule is Apache-2.0 |
| **No binary blobs** in app source | Native code built from source; Oboe via git submodule |
| **No external CDNs** in bundled web UI (when built) | Self-hosted assets only |
| **License: GPLv3-or-later** | See [LICENSE](../LICENSE); chose GPLv3 over AGPLv3 — [architecture.md](architecture.md#license-choice-gplv3) |
| **AndroidX + Kotlin + Oboe (NDK)** | No proprietary audio stacks |

---

## Repository map

```
OpenMultiTrack/
├── app/                    # Compose UI, MainActivity, MainViewModel, SessionRecorder/Player
├── domain/                 # Pure Kotlin: Mixer interface, session/transport models, RecordingChannels
├── usb-audio/              # UsbManager enumeration, Behringer heuristics, probe orchestration
├── audio-engine/           # JNI + C++/Oboe: probe, record, playback, SPSC ring buffers
├── mixer-behringer/        # X32Mixer, Xr18Mixer, OscUdpClient (OSC stubs for snapshots)
├── session-io/             # 24-bit interleaved WAV read/write
├── docs/                   # Architecture, status, agent guide (this file)
├── scripts/                # Semver, F-Droid, APK signature verification
├── fdroid/                 # Repo metadata + config (CI regenerates config.yml)
├── fdroiddata/             # Draft upstream fdroiddata recipe (needs refresh)
├── fastlane/metadata/      # Store listing strings
├── keystore/               # Pinned debug.keystore + EXPECTED_SIGNER.txt (committed)
├── site/                   # GitHub Pages + F-Droid repo output
├── third_party/oboe/       # Git submodule (required for native build)
└── .github/workflows/      # ci.yml (PRs), publish.yml (main → APK + Pages + Release)
```

**Planned module not yet created:** `remote-server` (Ktor HTTP + WebSocket) — see [control-api.md](control-api.md).

---

## Current implementation snapshot

**Latest release:** check `gradle/version.properties` and [GitHub Releases](https://github.com/T-vK/OpenMultiTrack/releases).

**Working vertical slice today:**

```
USB enumerate → grant permission → Oboe probe (in/out ch count)
    → Record N channels (N = probed input count, capped at 64) to 24-bit WAV
    → Play last recording back via Oboe output
```

**Single Compose screen** — [`app/.../ui/MainScreen.kt`](../app/src/main/kotlin/org/openmultitrack/app/ui/MainScreen.kt). No navigation graph yet.

**Sessions** saved under `getExternalFilesDir(null)/sessions/` as timestamped `.wav` files (single interleaved file per take).

For a **feature-by-feature matrix** against the original spec, see [PROJECT_STATUS.md](PROJECT_STATUS.md).

---

## Module responsibilities & extension points

| Module | Owns | Extend here when… |
|--------|------|-------------------|
| `domain` | `Mixer`, `RecordingSession`, `TransportState`, `RecordingChannels` | Adding domain types shared by UI, remote API, and tests |
| `usb-audio` | `UsbAudioEnumerator`, `UsbAudioProbeService`, Behringer VID/name match | USB permission flow, device ID mapping, new mixer USB IDs |
| `audio-engine` | Oboe streams, ring buffers, JNI | Latency, seek/flush, per-track taps, meter extraction |
| `session-io` | `WavWriter`, `WavReader` | BWF/RF64, FLAC, per-track files, seek index |
| `mixer-behringer` | `X32Mixer`, `Xr18Mixer`, `OscUdpClient` | OSC routing commands, snapshot apply/capture, feedback parser |
| `app` | Compose UI, wiring, `SessionRecorder`/`SessionPlayer` | New screens, transport UX, mixer connection UI |

**Dependency direction:** `app` → all libraries; `domain` has no Android deps; `usb-audio` → `audio-engine` + `domain`; never invert.

---

## Key source files (start here)

| Area | Path |
|------|------|
| UI state machine | [`app/.../MainViewModel.kt`](../app/src/main/kotlin/org/openmultitrack/app/MainViewModel.kt) |
| Record path (Kotlin side) | [`app/.../audio/SessionRecorder.kt`](../app/src/main/kotlin/org/openmultitrack/app/audio/SessionRecorder.kt) |
| Playback path | [`app/.../audio/SessionPlayer.kt`](../app/src/main/kotlin/org/openmultitrack/app/audio/SessionPlayer.kt) |
| USB list + permission | [`usb-audio/.../UsbAudioEnumerator.kt`](../usb-audio/src/main/kotlin/org/openmultitrack/usb/UsbAudioEnumerator.kt) |
| Native JNI entry | [`audio-engine/src/main/cpp/jni_bridge.cpp`](../audio-engine/src/main/cpp/jni_bridge.cpp) |
| Record callback | [`audio-engine/src/main/cpp/audio_recorder.cpp`](../audio-engine/src/main/cpp/audio_recorder.cpp) |
| Playback callback | [`audio-engine/src/main/cpp/audio_player.cpp`](../audio-engine/src/main/cpp/audio_player.cpp) |
| Mixer interface | [`domain/.../mixer/Mixer.kt`](../domain/src/main/kotlin/org/openmultitrack/domain/mixer/Mixer.kt) |
| X32 driver (stub snapshots) | [`mixer-behringer/.../X32Mixer.kt`](../mixer-behringer/src/main/kotlin/org/openmultitrack/mixer/behringer/X32Mixer.kt) |
| WAV I/O | [`session-io/.../wav/WavWriter.kt`](../session-io/src/main/kotlin/org/openmultitrack/sessionio/wav/WavWriter.kt) |

---

## Build & test

### Prerequisites

- JDK **17**
- Android SDK **API 35**, build-tools **35.0.0**
- NDK **r26d** (`26.3.11579264`)
- CMake **3.22.1**
- **Git submodules** (Oboe)

### Commands

```bash
git submodule update --init --recursive

# Debug APK (uses committed keystore/debug.keystore)
./gradlew :app:assembleDebug

# Unit tests (no device required)
./gradlew :domain:test :mixer-behringer:test :session-io:test :usb-audio:testDebugUnitTest

# Set version locally
./scripts/write-version-properties.sh 1.2.3
```

`keystore/debug.keystore` is **required** — `app/build.gradle.kts` fails configuration if missing. Password/alias: see [ci-and-releases.md](ci-and-releases.md).

### What is NOT tested yet

- `audio-engine` native code (no gtest harness)
- `app` instrumentation / Compose UI tests (deps declared, no tests written)
- End-to-end USB record on CI (no hardware)
- Mixer OSC beyond `/info` ping encoding unit test

Add tests in the **lowest layer possible** (`domain`, `session-io`, `mixer-behringer`) before UI integration tests.

---

## CI/CD conventions

| Workflow | When | Output |
|----------|------|--------|
| [`ci.yml`](../.github/workflows/ci.yml) | PRs, non-`main` pushes | Test + debug APK artifact |
| [`publish.yml`](../.github/workflows/publish.yml) | `main` push | **One** debug APK → F-Droid Pages repo **and** GitHub Release |

**Versioning:** [Conventional Commits](https://www.conventionalcommits.org/) → [`scripts/compute-semver.sh`](../scripts/compute-semver.sh). `feat:` = minor, `fix:` = patch, `BREAKING CHANGE` / `!:` = major.

**Commit style for this repo:** conventional commits; push to `main`; **no co-author trailers**.

**F-Droid repo URL:** `https://T-vK.github.io/OpenMultiTrack/fdroid/repo`

**Signing:** All published debug APKs use the **same committed** `keystore/debug.keystore`. Users who installed builds from **before signing was pinned (≤0.2.1)** must uninstall once; see [ci-and-releases.md](ci-and-releases.md).

---

## Recommended milestone order (do not skip)

Work in this order unless the user explicitly reprioritizes:

| # | Milestone | Rationale |
|---|-----------|-----------|
| **M1** | USB probe vertical slice | ✅ Done — validates UAC2 path before scaling |
| **M2** | Multichannel record + WAV + basic playback | ✅ Mostly done — interleaved WAV, N = probed channels |
| **M3** | Playback transport + **sample-aligned seek** + UI | Blocking for “virtual soundcheck” value |
| **M4** | OSC snapshots (record ↔ soundcheck routing) | Requires real hardware validation |
| **M5** | Embedded Ktor web remote + `ControlService` | Shares backend with Compose |
| **M6** | F-Droid main inclusion (`fdroiddata` PR) | After reproducible release builds |

Within M3, suggested sub-order:

1. Expose playback position + seek in native engine (`audio_player.cpp` flush + re-prime)
2. Wire `WavReader.seekFrame()` to engine seek
3. Compose transport bar (play/pause/stop/seek/position)
4. Disk space monitor + graceful stop
5. Per-track solo/mute (monitoring only)

---

## Hardware & protocol validation

Many assumptions are **UNVERIFIED** — see [hardware-assumptions.md](hardware-assumptions.md).

Before implementing OSC routing or claiming channel counts:

1. Test on **real X32/XR18** over USB OTG (powered hub recommended).
2. Log actual `UsbDevice` VID/PID and Oboe channel counts.
3. Confirm OSC `/info` response and routing command acks.
4. Update `BehringerUsbIdentifiers.kt` and docs with verified IDs.

**Do not silently expand scope** (e.g. adding Midas M32, Dante, or non-FOSS libs) without user decision.

---

## Common pitfalls for agents

| Pitfall | Correct approach |
|---------|------------------|
| Forgetting Oboe submodule | Always `git submodule update --init --recursive` |
| Using default debug keystore | Use committed `keystore/debug.keystore` only |
| Separate release vs debug APK in CI | Only `assembleDebug`; one artifact for F-Droid + GitHub Release |
| `--create-metadata` in fdroid | Use committed `fdroid/metadata/org.openmultitrack.yml`; run `prepare-fdroid-metadata.sh` |
| `suggestedVersionCode: 2147483647` | Means broken metadata — `CurrentVersionCode` must match a real APK |
| Blocking audio thread | No allocation/locks in Oboe callbacks; use SPSC rings |
| Adding Play Services “just for crashes” | Forbidden — use logging + user reports |
| Hardcoding 2 channels | Use `RecordingChannels.fromProbe()` — cap is `AudioConstants.MAX_CHANNELS` (64) |
| Implementing web UI before `ControlService` | Define shared Kotlin service first per [control-api.md](control-api.md) |

---

## F-Droid packaging files

| File | Purpose |
|------|---------|
| [`fdroid/metadata/org.openmultitrack.yml`](../fdroid/metadata/org.openmultitrack.yml) | Binary repo metadata (CI updates version fields) |
| [`fdroiddata/org.openmultitrack.yml`](../fdroiddata/org.openmultitrack.yml) | Draft **source build** recipe for official F-Droid — **stale**, needs update before submission |
| [`fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US/) | Title, short/full description |
| [`scripts/verify-fdroid-index.sh`](../scripts/verify-fdroid-index.sh) | CI guard for index consistency |

Official F-Droid inclusion still requires: release build type (or accepted debug exception), reproducible build proof, updated `fdroiddata` recipe, and hardware-tested feature completeness.

---

## Architecture decisions already made

| Decision | Choice | Doc |
|----------|--------|-----|
| UI pattern | MVVM + Compose | [architecture.md](architecture.md) |
| Real-time audio | C++17 + Oboe, SPSC ring buffers | [architecture.md](architecture.md) |
| Mixer abstraction | `Mixer` interface + per-model OSC drivers | [mixer-drivers.md](mixer-drivers.md) |
| Session file format (v1) | 24-bit PCM interleaved WAV | [session-io](../session-io/) |
| Remote server (planned) | Ktor CIO embedded, not NanoHTTPD | [control-api.md](control-api.md) |
| Copyleft | GPLv3-or-later | [architecture.md](architecture.md) |
| Version code scheme | `major*10000 + minor*100 + patch` | [`scripts/semver-to-version-code.sh`](../scripts/semver-to-version-code.sh) |

---

## When finishing a task

1. **Update** [PROJECT_STATUS.md](PROJECT_STATUS.md) if feature status changed.
2. **Add/update tests** in the lowest applicable module.
3. **Update** relevant doc (`architecture.md`, `mixer-drivers.md`, etc.) if API contracts changed.
4. **Run** `./gradlew :domain:test :mixer-behringer:test :session-io:test :usb-audio:testDebugUnitTest` before pushing.
5. **Use conventional commit** messages so semver + F-Droid publish work.
6. **Flag** any new UNVERIFIED hardware assumption in [hardware-assumptions.md](hardware-assumptions.md).

---

## Quick links

- **Upstream:** https://github.com/T-vK/OpenMultiTrack
- **F-Droid repo:** https://T-vK.github.io/OpenMultiTrack/fdroid/repo
- **Issues:** https://github.com/T-vK/OpenMultiTrack/issues
- **Oboe:** https://github.com/google/oboe (submodule `third_party/oboe`)
