# OpenMultiTrack

FOSS Android app for **live multitrack recording** and **virtual soundcheck** with Behringer **X32** / **XR18** (and other UAC2 mixers), designed for [F-Droid](https://f-droid.org/).

## Status

| Milestone | State |
|-----------|-------|
| Architecture, docs, Gradle/Oboe skeleton | ✅ |
| USB enumeration + Oboe channel probe | ✅ |
| Multichannel WAV record + basic playback | ✅ (interleaved, no seek UI yet) |
| CI, semver, GitHub Pages F-Droid repo, pinned debug signing | ✅ |
| Playback seek + transport UI (virtual soundcheck) | ❌ **next** |
| OSC routing snapshots (X32/XR18) | 🟡 stubs |
| Embedded web remote (Ktor) | ❌ |

**Detailed tracker:** [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md)  
**AI agent onboarding:** [docs/AGENTS.md](docs/AGENTS.md)

## License

**GPL-3.0-or-later** — see [LICENSE](LICENSE). Oboe is Apache-2.0 ([`third_party/oboe`](third_party/oboe)).

## Install (CI builds)

Add F-Droid repository: **`https://T-vK.github.io/OpenMultiTrack/fdroid/repo`**

Debug builds only. GitHub Releases attach the **same APK** as the F-Droid repo.  
If you installed a build from before signing was pinned (≤0.2.1), uninstall once, then reinstall — see [docs/ci-and-releases.md](docs/ci-and-releases.md).

## Build

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

Requires Android SDK API 35, NDK r26d, JDK 17. Debug signing uses committed `keystore/debug.keystore`. See [docs/reproducible-builds.md](docs/reproducible-builds.md).

## Documentation

| Doc | Contents |
|-----|----------|
| [**docs/AGENTS.md**](docs/AGENTS.md) | **Start here** (AI agents) — repo map, constraints, conventions |
| [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) | Done vs missing vs original spec |
| [docs/architecture.md](docs/architecture.md) | Layers, data flow, threading |
| [docs/technical-risks.md](docs/technical-risks.md) | Risks and mitigations |
| [docs/mixer-drivers.md](docs/mixer-drivers.md) | `Mixer` API, OSC maps |
| [docs/hardware-assumptions.md](docs/hardware-assumptions.md) | What to verify on real gear |
| [docs/control-api.md](docs/control-api.md) | Remote API draft |
| [docs/ci-and-releases.md](docs/ci-and-releases.md) | CI, F-Droid repo, signing |
| [docs/reproducible-builds.md](docs/reproducible-builds.md) | Toolchain pins |

## Hardware

Connect the mixer via **USB OTG** (powered hub recommended). Grant USB permission in the app, then **Probe channels**. Validate channel counts against [docs/hardware-assumptions.md](docs/hardware-assumptions.md).

## F-Droid

- Fastlane metadata: [`fastlane/metadata`](fastlane/metadata)
- Self-hosted repo metadata: [`fdroid/metadata`](fdroid/metadata)
- Draft upstream recipe (needs refresh): [`fdroiddata/org.openmultitrack.yml`](fdroiddata/org.openmultitrack.yml)

## Modules

| Module | Role |
|--------|------|
| `app` | Compose UI, session record/playback wiring |
| `domain` | Session/mixer models, `Mixer` interface |
| `usb-audio` | USB enumeration, channel probe |
| `audio-engine` | Oboe native probe, record, playback |
| `mixer-behringer` | X32/XR18 OSC drivers (snapshots stubbed) |
| `session-io` | 24-bit WAV read/write |

Planned: `remote-server` (Ktor HTTP/WebSocket) — not created yet.
