# OpenMultiTrack

> **Beta — €99 · permission required**
>
> OpenMultiTrack is in **beta**. A license costs **€99**.
>
> To **apply for beta testing** or **purchase a license**, open a [GitHub issue](https://github.com/T-vK/OpenMultiTrack/issues).
>
> **Copyright notice:** The entire project — all source code, documentation, and assets in this repository — is **copyrighted** and **not available under any free or open-source license**. **Use, copying, modification, distribution, or incorporation of any of this code into other projects is prohibited** without explicit written permission from the copyright holder. See [LICENSE](LICENSE).

Android app for **live multitrack recording** and **virtual soundcheck** with Behringer **X32** / **XR18**, **Flow 8**, and other UAC2 mixers.

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

**Detailed tracker:** [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md)

## License

**Proprietary — all rights reserved.** This project is not free or open-source software. No use of the code is permitted without a license agreement. See [LICENSE](LICENSE).

Third-party components under `third_party/` (for example Oboe, libusb) remain subject to their upstream licenses.

## Install

Licensed builds are provided to beta testers and purchasers. Contact the copyright holder via [GitHub issues](https://github.com/T-vK/OpenMultiTrack/issues) for access.

Historical CI/F-Droid distribution metadata may still exist in the repository but does not grant a license to use the Software.

## Build

Building from source requires a license. Unauthorized building, running, or redistribution is prohibited. See [LICENSE](LICENSE).

Licensed developers:

```bash
git submodule update --init --recursive
./scripts/install-git-hooks.sh
./gradlew :app:assembleDebug
```

Requires Android SDK API 35, NDK r26d, JDK 17. See [docs/reproducible-builds.md](docs/reproducible-builds.md).

## Hardware

Connect the mixer via **USB OTG** (powered hub recommended). Grant USB permission in the app, then add or probe your mixer. Validate channel counts against [docs/hardware-assumptions.md](docs/hardware-assumptions.md).

**Remote control:** On the same Wi‑Fi, set one tablet to **Host** (at the mixer) and another to **Remote** in Settings → Remote control.

## Documentation

| Audience | Start here |
|----------|------------|
| **Users** (install, hardware) | This README |
| **Contributors** | [CONTRIBUTING.md](CONTRIBUTING.md) |
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
