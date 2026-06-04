# OpenMultiTrack

FOSS Android app for **live multitrack recording** and **virtual soundcheck** with Behringer **X32** / **XR18** (and other UAC2 mixers), designed for [F-Droid](https://f-droid.org/).

## Status

- [x] Architecture, risk assessment, mixer driver design docs
- [x] Gradle multi-module skeleton (Kotlin, Compose, NDK, Oboe)
- [x] USB device enumeration + Oboe channel-count probe UI
- [x] 2-channel WAV record + playback (milestone 2, initial)
- [x] GitHub Actions CI + semantic versioning + Pages F-Droid repo
- [ ] Multitrack playback + seek (milestone 3)
- [ ] OSC snapshots (milestone 4)
- [ ] Embedded web remote (milestone 5)

## License

**GPL-3.0-or-later** — see [LICENSE](LICENSE). Oboe is Apache-2.0 ([`third_party/oboe`](third_party/oboe)).

## Install (CI builds)

Add F-Droid repository: **`https://T-vK.github.io/OpenMultiTrack/fdroid/repo`**

See [docs/ci-and-releases.md](docs/ci-and-releases.md) for versioning and workflow details.

## Build

```bash
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

Requires Android SDK API 35, NDK r26d, JDK 17. See [docs/reproducible-builds.md](docs/reproducible-builds.md).

## Documentation

| Doc | Contents |
|-----|----------|
| [docs/architecture.md](docs/architecture.md) | Layers, data flow, threading |
| [docs/technical-risks.md](docs/technical-risks.md) | Risks and mitigations |
| [docs/mixer-drivers.md](docs/mixer-drivers.md) | `Mixer` API, OSC maps |
| [docs/hardware-assumptions.md](docs/hardware-assumptions.md) | What to verify on real gear |
| [docs/control-api.md](docs/control-api.md) | Remote API draft |

## Hardware

Connect the mixer via **USB OTG** (powered hub recommended). Grant USB permission in the app, then **Probe channels**. Validate channel counts against [docs/hardware-assumptions.md](docs/hardware-assumptions.md).

## F-Droid

Fastlane metadata: [`fastlane/metadata`](fastlane/metadata). Draft recipe: [`fdroiddata/org.openmultitrack.yml`](fdroiddata/org.openmultitrack.yml).

## Modules

- `app` — Compose UI
- `domain` — session/mixer models
- `usb-audio` — USB enumeration
- `audio-engine` — Oboe native probe (recording engine next)
- `mixer-behringer` — X32/XR18 OSC (UDP `/info` ping)
- `session-io` — 24-bit WAV read/write
