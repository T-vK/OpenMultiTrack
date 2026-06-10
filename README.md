# OpenMultiTrack

FOSS Android app for **live multitrack recording** and **virtual soundcheck** with Behringer **X32** / **XR18**, **Flow 8**, and other UAC2 mixers. Designed for [F-Droid](https://f-droid.org/).

Record per-channel sessions on a tablet at the mixer, play them back for rehearsal, and optionally control the Host from a second Android device over Wi‑Fi.

## Status

| Capability | State |
|------------|-------|
| DAW UI (strips, waveforms, multi-mixer) | ✅ |
| USB probe + multichannel record (per-channel WAV) | ✅ |
| Monitor, VU meters, virtual soundcheck playback | ✅ |
| USB dropout recovery (silence + resume) | ✅ |
| LAN remote (second Android device) | ✅ |
| Channel labels (XR18 OSC, Flow 8 BLE/USB) | ✅ |
| OSC routing snapshots (record ↔ soundcheck) | 🟡 planned / stubbed |
| Official F-Droid main repo | 🟡 self-hosted repo live |

**Detailed tracker:** [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md)

## License

**GPL-3.0-or-later** — see [LICENSE](LICENSE). Oboe is Apache-2.0 ([`third_party/oboe`](third_party/oboe)).

## Install (CI builds)

Add F-Droid repository: **`https://T-vK.github.io/OpenMultiTrack/fdroid/repo`**

Debug builds only. GitHub Releases attach the **same APK** as the F-Droid repo.  
If you installed a build from before signing was pinned (≤0.2.1), uninstall once, then reinstall — see [docs/ci-and-releases.md](docs/ci-and-releases.md).

## Build

```bash
git submodule update --init --recursive
./scripts/install-git-hooks.sh
./gradlew :app:assembleDebug
```

Requires Android SDK API 35, NDK r26d, JDK 17. Debug signing uses committed `keystore/debug.keystore`. See [docs/reproducible-builds.md](docs/reproducible-builds.md).

## Hardware

Connect the mixer via **USB OTG** (powered hub recommended). Grant USB permission in the app, then add or probe your mixer. Validate channel counts against [docs/hardware-assumptions.md](docs/hardware-assumptions.md).

**Remote control:** On the same Wi‑Fi, set one tablet to **Host** (at the mixer) and another to **Remote** in Settings → Remote control.

## F-Droid

- Fastlane metadata: [`fastlane/metadata`](fastlane/metadata)
- Self-hosted repo metadata: [`fdroid/metadata`](fdroid/metadata)
- Draft upstream recipe (needs refresh): [`fdroiddata/org.openmultitrack.yml`](fdroiddata/org.openmultitrack.yml)

## Documentation

| Audience | Start here |
|----------|------------|
| **Users** (install, hardware) | This README |
| **Developers** | [**docs/README.md**](docs/README.md) |
| **AI coding agents** | [docs/AGENTS.md](docs/AGENTS.md) |

### Developer docs (summary)

| Doc | Contents |
|-----|----------|
| [docs/README.md](docs/README.md) | Full documentation index |
| [docs/development/getting-started.md](docs/development/getting-started.md) | Toolchain, build, test |
| [docs/architecture/overview.md](docs/architecture/overview.md) | System design |
| [docs/product/ui-daw.md](docs/product/ui-daw.md) | DAW UI for designers |
| [docs/remote-control.md](docs/remote-control.md) | LAN Host/Remote sync |
| [docs/ci-and-releases.md](docs/ci-and-releases.md) | CI, signing, releases |

## Modules

| Module | Role |
|--------|------|
| `app` | Compose DAW UI, foreground service, session orchestration |
| `domain` | Session/mixer models, `Mixer` interface |
| `usb-audio` | USB enumeration, Oboe/UAC2 routing |
| `audio-engine` | Native Oboe + UAC2 record/playback |
| `mixer-behringer` | X32/XR18 OSC, scribble import |
| `session-io` | Per-channel WAV, session.json, waveforms |
| `remote-server` | LAN discovery and WebSocket sync |
