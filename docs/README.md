# OpenMultiTrack — Developer Documentation

Technical documentation for engineers, architects, and designers working on OpenMultiTrack.  
For end-user install and build instructions, see the [project README](../README.md).

---

## Start here

| Audience | Start with |
|----------|------------|
| **New contributor** | [development/getting-started.md](development/getting-started.md) → [development/codebase-map.md](development/codebase-map.md) |
| **AI coding agent** | [AGENTS.md](AGENTS.md) |
| **Architect / tech lead** | [architecture/overview.md](architecture/overview.md) → [architecture/modules.md](architecture/modules.md) |
| **Product / UX** | [product/overview.md](product/overview.md) → [product/ui-daw.md](product/ui-daw.md) |
| **Current status** | [PROJECT_STATUS.md](PROJECT_STATUS.md) |

---

## Documentation map

### Architecture

| Doc | Contents |
|-----|----------|
| [architecture/overview.md](architecture/overview.md) | Purpose, license rationale, high-level diagram |
| [architecture/modules.md](architecture/modules.md) | Gradle modules, dependency graph, boundaries |
| [architecture/data-flows.md](architecture/data-flows.md) | Record, playback, monitor, remote sync, OSC |
| [architecture/threading.md](architecture/threading.md) | Real-time threads, ring buffers, backpressure |
| [architecture/decisions.md](architecture/decisions.md) | Key architectural decisions (ADR-style) |

Legacy single-file overview (redirects here): [architecture.md](architecture.md)

### Development

| Doc | Contents |
|-----|----------|
| [development/getting-started.md](development/getting-started.md) | Prerequisites, build, run, submodules |
| [development/codebase-map.md](development/codebase-map.md) | Packages, key types, where to look |
| [development/conventions.md](development/conventions.md) | Module rules, commits, FOSS constraints |
| [development/testing.md](development/testing.md) | Unit, instrumented, hardware, E2E tests |

### Product & UX

| Doc | Contents |
|-----|----------|
| [product/overview.md](product/overview.md) | Goals, supported hardware, app modes |
| [product/ui-daw.md](product/ui-daw.md) | DAW screen, channel strips, transport, settings |
| [product/session-format.md](product/session-format.md) | On-disk layout, `session.json`, waveforms |
| [product/roadmap.md](product/roadmap.md) | Phased roadmap and open work |

### Module reference

Per-module deep dives (responsibilities, extension points, key packages):

| Module | Doc |
|--------|-----|
| `app` | [modules/app.md](modules/app.md) |
| `domain` | [modules/domain.md](modules/domain.md) |
| `usb-audio` | [modules/usb-audio.md](modules/usb-audio.md) |
| `audio-engine` | [modules/audio-engine.md](modules/audio-engine.md) |
| `session-io` | [modules/session-io.md](modules/session-io.md) |
| `mixer-behringer` | [modules/mixer-behringer.md](modules/mixer-behringer.md) |
| `remote-server` | [modules/remote-server.md](modules/remote-server.md) |

### Protocols & hardware

| Doc | Contents |
|-----|----------|
| [remote-control.md](remote-control.md) | LAN Host/Remote sync (WebSocket, discovery) |
| [mixer-drivers.md](mixer-drivers.md) | `Mixer` API, OSC routing, snapshots (design) |
| [hardware-assumptions.md](hardware-assumptions.md) | USB/OSC assumptions — **verify on real gear** |
| [technical-risks.md](technical-risks.md) | Known risks and mitigations |

### Operations & release

| Doc | Contents |
|-----|----------|
| [ci-and-releases.md](ci-and-releases.md) | CI workflows, F-Droid repo, signing |
| [reproducible-builds.md](reproducible-builds.md) | Toolchain pins, reproducibility notes |
| [PROJECT_STATUS.md](PROJECT_STATUS.md) | Feature matrix vs original spec |

### Research & planning (reference)

These folders capture hardware reverse-engineering and implementation plans. They may lag the code slightly; treat [PROJECT_STATUS.md](PROJECT_STATUS.md) as the source of truth for what ships.

| Folder | Topic |
|--------|-------|
| [uac2-host-plan/](uac2-host-plan/) | UAC2 isoch host via libusb (largely implemented in `audio-engine`) |
| [flow8-reverse-engineering/](flow8-reverse-engineering/) | Flow 8 USB MIDI / BLE protocol notes |
| [xr18-scribble-strip/](xr18-scribble-strip/) | XR18 scribble strip OSC tooling |

### Deprecated / superseded

| Doc | Note |
|-----|------|
| [control-api.md](control-api.md) | **Superseded** by [remote-control.md](remote-control.md). Draft assumed a browser web UI with Ktor; the shipped system is Android-to-Android LAN sync via NanoHTTPD. |
| [product-roadmap-v2.md](product-roadmap-v2.md) | **Superseded** by [product/roadmap.md](product/roadmap.md) |

---

## Repository layout (top level)

```
OpenMultiTrack/
├── app/                 Android app — Compose DAW UI, services, remote wiring
├── domain/              Pure Kotlin models and interfaces
├── usb-audio/           USB enumeration, permissions, audio backend routing
├── audio-engine/        JNI + C++ (Oboe + UAC2/libusb)
├── mixer-behringer/     Behringer/Midas OSC drivers, scribble import
├── session-io/          Session directories, WAV I/O, waveform cache
├── remote-server/       LAN discovery, WebSocket host/client
├── docs/                This documentation tree
├── scripts/             Build, semver, F-Droid, test runners
├── third_party/oboe/    Git submodule (required)
├── third_party/libusb/  Vendored libusb for UAC2 path
├── fdroid/              Binary repo metadata (CI-maintained)
├── fastlane/            Store listing strings
└── keystore/            Pinned debug signing key (committed)
```

---

## Keeping docs current

When you change behavior that affects architecture, protocols, or user-visible product:

1. Update the relevant doc in this tree (not only `PROJECT_STATUS.md`).
2. Prefer describing **concepts and file/package names** over line numbers or fragile identifiers.
3. Flag new **UNVERIFIED** hardware assumptions in [hardware-assumptions.md](hardware-assumptions.md).
4. AI agents: follow the checklist in [AGENTS.md](AGENTS.md#when-finishing-a-task).
