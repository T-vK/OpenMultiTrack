# Module architecture

OpenMultiTrack is a **multi-module Gradle project** with seven library/application modules. Dependency direction is strict: `app` depends on everything; `domain` depends on nothing Android-specific.

## Dependency graph

```
app
 ├── domain
 ├── usb-audio ──► audio-engine ──► domain
 ├── mixer-behringer ──► domain
 ├── session-io ──► domain
 └── remote-server ──► domain
```

**Rule:** Never invert dependencies (e.g. `domain` must not import `app` or Android APIs).

## Module summary

| Module | Type | Package root | Responsibility |
|--------|------|--------------|----------------|
| `app` | Android application | `org.openmultitrack.app` | Compose UI, `MainViewModel`, foreground service, session orchestration, remote wiring |
| `domain` | JVM library | `org.openmultitrack.domain` | Shared models: session, mixer, channel strips, remote protocol constants |
| `usb-audio` | Android library | `org.openmultitrack.usb` | USB enumeration, permissions, Oboe/UAC2 routing |
| `audio-engine` | Android library + NDK | `org.openmultitrack.audio` + `src/main/cpp/` | Native real-time audio: Oboe, UAC2 isoch, ring buffers |
| `session-io` | JVM library | `org.openmultitrack.sessionio` | Session directories, WAV I/O, waveform extraction/cache |
| `mixer-behringer` | JVM library | `org.openmultitrack.mixer.behringer` | X32/XR18 OSC, scribble import, Flow 8 decoders |
| `remote-server` | Android library | `org.openmultitrack.remote` | LAN discovery, WebSocket host (NanoHTTPD), client (OkHttp) |

Per-module details: [../modules/](../modules/)

## Boundary justifications

| Boundary | Why it exists |
|----------|----------------|
| **Native `audio-engine`** | Sub-10 ms audio deadlines; no GC or locks on the Oboe callback thread |
| **`domain`** | Testable business rules without JNI or Android; shared by UI, remote sync, and tests |
| **`usb-audio`** | Platform USB permission and device ID mapping isolated from UI and disk I/O |
| **`session-io`** | Disk throughput and WAV chunking are not real-time; fed from engine via queues |
| **`mixer-behringer`** | OSC vocabulary differs per console model; audio path is generic UAC2 |
| **`remote-server`** | Network protocol and JSON codecs separated from Compose and audio threads |
| **`app`** | Composition root: wires modules, owns Android lifecycle and persistence |

## Extension points

| When you need to… | Extend in… |
|-------------------|------------|
| Add a domain type used by UI + remote + tests | `domain` |
| Support a new USB mixer VID/PID or routing rule | `usb-audio` (`BehringerUsbIdentifiers`, probe service) |
| Change latency, seek, meters, or UAC2 isoch behavior | `audio-engine` (C++ + JNI facades) |
| Add session file format, cues, or waveform levels | `session-io` |
| Add OSC commands or a new console family | `mixer-behringer` (implement `Mixer`) |
| Add remote commands or sync fields | `domain/remote`, `remote-server`, `app/remote` |
| Add screens, settings, or service lifecycle | `app` |

## Third-party native dependencies

| Component | Location | License | Role |
|-----------|----------|---------|------|
| Oboe | `third_party/oboe` (git submodule) | Apache-2.0 | AAudio/OpenSL ES abstraction for Oboe path |
| libusb | `third_party/libusb` (vendored) | LGPL-2.1 | USB isochronous transfers for UAC2 path |

Always run `git submodule update --init --recursive` before building.

## FOSS dependency policy

Allowed: Apache-2.0, MIT, BSD, GPL-compatible libraries bundled in the APK.

Forbidden without explicit approval: Play Services, Firebase, GMS, ads, analytics, proprietary USB SDKs, external CDNs in bundled assets.

CI enforces a dependency grep on `:app` — see [../development/conventions.md](../development/conventions.md).
