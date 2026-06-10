# Contributing to OpenMultiTrack

Thank you for helping improve OpenMultiTrack. This project is **proprietary software** (see [LICENSE](LICENSE)); contributions are accepted only with explicit agreement to the copyright holder's terms.

## Before you start

1. Read the [developer documentation hub](docs/README.md) for architecture, modules, and product context.
2. Check [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for what is already implemented vs planned.
3. For hardware or OSC work, review [docs/hardware-assumptions.md](docs/hardware-assumptions.md) — many items are still **UNVERIFIED** on real consoles.

## Development setup

```bash
git submodule update --init --recursive
./scripts/install-git-hooks.sh   # recommended: commit message conventions
./gradlew :app:assembleDebug
```

Full toolchain pins and IDE notes: [docs/development/getting-started.md](docs/development/getting-started.md)

## How to contribute

| Step | Guidance |
|------|----------|
| **Pick scope** | One focused change per commit/PR. Match existing module boundaries — see [docs/development/conventions.md](docs/development/conventions.md). |
| **Write tests** | Add tests in the lowest layer that can express the behavior (`domain`, `session-io`, `mixer-behringer`, etc.). See [docs/development/testing.md](docs/development/testing.md). |
| **Update docs** | If behavior, protocols, or architecture change, update the relevant file under `docs/`. Avoid line numbers; reference packages and types. |
| **Commit messages** | Use [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, or `BREAKING CHANGE` / `!:` for semver on `main`. |
| **Run checks** | `./gradlew :domain:test :mixer-behringer:test :session-io:test :usb-audio:testDebugUnitTest :remote-server:test` |

## Hard rules (non-negotiable)

- **No** Google Play Services, Firebase, GMS, ads, or telemetry
- **No** proprietary USB/audio SDKs or binary blobs in app source
- **No** external CDNs in bundled assets
- **No** blocking work on Oboe/UAC2 audio callback threads

CI enforces several of these automatically. Details:

- [docs/development/fdroid-compliance.md](docs/development/fdroid-compliance.md) — full F-Droid / license guide
- [docs/development/conventions.md](docs/development/conventions.md) — short FOSS table
- [.cursor/rules/](.cursor/rules/) — Cursor AI rules (same policies for agents)

## Where to put code

| Change type | Module |
|-------------|--------|
| UI / screens / service wiring | `app` |
| Shared models / interfaces | `domain` |
| USB enumeration / backend routing | `usb-audio` |
| Real-time audio / native engine | `audio-engine` |
| Session files / WAV / waveforms | `session-io` |
| OSC / mixer drivers / scribble | `mixer-behringer` |
| LAN remote protocol | `remote-server` + `app/remote` |

Module reference: [docs/modules/](docs/modules/)

## Hardware and E2E testing

Most instrumented tests require a physical device and often a connected mixer. Scripts live under `scripts/` (e.g. `run-xr18-hardware-tests.sh`, `run-dual-device-e2e-tests.sh`). These are not run in CI.

## Questions and coordination

- **Issues:** https://github.com/T-vK/OpenMultiTrack/issues
- **Architecture questions:** [docs/architecture/overview.md](docs/architecture/overview.md)
- **AI agents:** [docs/AGENTS.md](docs/AGENTS.md)

## License

By contributing, you agree that your contributions become the property of the copyright holder and may be used under the project's proprietary license ([LICENSE](LICENSE)). Do not contribute unless you accept these terms.
