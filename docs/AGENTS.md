# AI Agent Guide — OpenMultiTrack

**Read this file first** before making changes. For the full documentation tree, see [README.md](README.md).

| If you need… | Read |
|--------------|------|
| Documentation index | [README.md](README.md) |
| Build and repo layout | [development/getting-started.md](development/getting-started.md), [development/codebase-map.md](development/codebase-map.md) |
| What is done vs missing | [PROJECT_STATUS.md](PROJECT_STATUS.md) |
| Architecture | [architecture/overview.md](architecture/overview.md) |
| LAN remote protocol | [remote-control.md](remote-control.md) |
| USB/OSC hardware assumptions | [hardware-assumptions.md](hardware-assumptions.md) |
| CI, releases, signing | [ci-and-releases.md](ci-and-releases.md) |

---

## Mission

FOSS Android app for:

1. **Live multitrack recording** — USB input channels from Behringer X32/XR18/Flow 8 (UAC2) as synchronized per-channel sessions.
2. **Virtual soundcheck** — playback to mixer USB returns with seek, loop, and waveforms.
3. **LAN remote** — second Android device mirrors Host state (not a browser SPA).
4. **OSC mixer control** — routing snapshots (designed; apply/capture still stubbed).

Target: F-Droid compliance, GPLv3, no Google proprietary stack.

---

## Hard constraints (non-negotiable)

| Rule | Enforcement |
|------|-------------|
| No Play Services / Firebase / GMS / ads / telemetry | CI greps `:app:dependencies` |
| FOSS-only dependencies | Apache-2.0 / MIT / BSD / GPL-compatible |
| No binary blobs | Oboe submodule + vendored libusb built from source |
| No external CDNs in bundled assets | Self-contained APK |
| License GPLv3-or-later | [LICENSE](../LICENSE) |
| AndroidX + Kotlin + Oboe (NDK) | No proprietary audio SDKs |

---

## Repository map

```
OpenMultiTrack/
├── app/                    # Compose DAW, MainViewModel, AudioSessionService
├── domain/                 # Pure Kotlin models, Mixer interface
├── usb-audio/              # USB enum, permissions, Oboe/UAC2 router
├── audio-engine/           # JNI + C++/Oboe + UAC2/libusb
├── mixer-behringer/        # X32/XR18 OSC, scribble, Flow 8
├── session-io/             # Per-channel WAV, session.json, waveforms
├── remote-server/          # NanoHTTPD host, OkHttp client, discovery
├── docs/                   # Developer documentation hub
├── scripts/                # Semver, F-Droid, test runners
├── third_party/oboe/       # Git submodule (required)
├── third_party/libusb/     # Vendored for UAC2
└── .github/workflows/      # ci.yml, publish.yml
```

**7 modules** in `settings.gradle.kts`. `remote-server` **exists** and is wired into `app`.

---

## Current implementation snapshot

**Version:** `gradle/version.properties`

**Working today:**

```
USB enumerate → permission → Oboe/UAC2 probe
  → DAW: arm channels, record per-channel WAV under session.json
  → Monitor + VU + live waveforms
  → Soundcheck: library, playback, seek, loop
  → USB dropout → silence gaps → resume
  → Optional: LAN Remote mirrors Host
  → Scribble labels: XR18 OSC, Flow 8 BLE/USB
```

**Not working / stubbed:**

- OSC `applySnapshot` / `captureSnapshot` for routing
- Browser web remote (Ktor draft superseded)
- Disk space pre-stop, RF64/FLAC

Feature matrix: [PROJECT_STATUS.md](PROJECT_STATUS.md)

---

## Module responsibilities

| Module | Owns | Extend when… |
|--------|------|--------------|
| `domain` | Shared models, `Mixer`, `RemoteProtocol` | New types for UI + remote + tests |
| `usb-audio` | Enumeration, router, permissions | New USB IDs, routing rules |
| `audio-engine` | Native real-time audio | Latency, seek, UAC2 isoch |
| `session-io` | Disk format, WAV, peaks | New formats, cues |
| `mixer-behringer` | OSC drivers, scribble | New console or OSC commands |
| `remote-server` | Wire protocol | New sync messages |
| `app` | UI, service, wiring | Screens, orchestration |

**Dependency direction:** `app` → libraries; `domain` has no Android deps.

Per-module docs: [modules/](modules/)

---

## Key source areas (no line numbers)

| Area | Location |
|------|----------|
| UI state | `app/.../MainViewModel.kt`, `DawUiState` |
| Per-mixer audio | `app/.../service/MixerSessionController.kt` |
| DAW UI | `app/.../ui/daw/DawMainScreen.kt` |
| Capture engine | `app/.../audio/CaptureSessionEngine.kt` |
| USB + router | `usb-audio/.../UsbAudioEnumerator.kt`, `AudioEngineRouter.kt` |
| Native JNI | `audio-engine/src/main/cpp/jni_bridge.cpp`, `uac2/` |
| Session files | `session-io/.../session/SessionMetadata.kt`, `wav/PerChannelWavWriter.kt` |
| Remote | `remote-server/.../RemoteHostServer.kt`, `app/.../remote/RemoteControlManager.kt` |
| Mixer API | `domain/.../mixer/Mixer.kt` |

Full map: [development/codebase-map.md](development/codebase-map.md)

---

## Build & test

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug

./gradlew :domain:test :mixer-behringer:test :session-io:test \
  :usb-audio:testDebugUnitTest :remote-server:test

./scripts/run-uac2-native-tests.sh
```

Device/E2E: [development/testing.md](development/testing.md)

`keystore/debug.keystore` is **required** for debug builds.

---

## Common pitfalls

| Pitfall | Correct approach |
|---------|------------------|
| Forgetting Oboe submodule | `git submodule update --init --recursive` |
| Docs say `remote-server` missing | It exists — see [modules/remote-server.md](modules/remote-server.md) |
| Implement Ktor browser API | Use [remote-control.md](remote-control.md) Android sync model |
| Blocking audio callback | SPSC rings only; no alloc/locks |
| Hardcoding channel count | Use probe + `RecordingChannels` (max 64) |
| Stale architecture.md only | Use [architecture/](architecture/) split docs |

---

## When finishing a task

1. Update [PROJECT_STATUS.md](PROJECT_STATUS.md) if feature status changed.
2. Add tests in the lowest applicable module.
3. Update relevant doc under `docs/` if contracts or behavior changed.
4. Run unit tests before pushing.
5. Use conventional commit messages (`feat:`, `fix:`).
6. Flag new UNVERIFIED hardware assumptions in [hardware-assumptions.md](hardware-assumptions.md).

---

## Quick links

- **Repo:** https://github.com/T-vK/OpenMultiTrack
- **F-Droid repo:** https://T-vK.github.io/OpenMultiTrack/fdroid/repo
- **Developer docs:** [README.md](README.md)
