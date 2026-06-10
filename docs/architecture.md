# Architecture (index)

> **This document has been split** into focused pages under [architecture/](architecture/).  
> Start with [architecture/overview.md](architecture/overview.md).

## Quick links

| Topic | Doc |
|-------|-----|
| System purpose and diagram | [architecture/overview.md](architecture/overview.md) |
| Gradle modules and boundaries | [architecture/modules.md](architecture/modules.md) |
| Record, playback, remote, OSC flows | [architecture/data-flows.md](architecture/data-flows.md) |
| Threads, rings, backpressure | [architecture/threading.md](architecture/threading.md) |
| ADR-style decisions | [architecture/decisions.md](architecture/decisions.md) |
| Per-module reference | [modules/](modules/) |
| Full documentation index | [README.md](README.md) |

## One-paragraph summary

OpenMultiTrack is a layered Android app: **Compose + MVVM** (`app`) over **pure Kotlin domain** types, with **USB routing** (`usb-audio`), a **native Oboe/UAC2 engine** (`audio-engine`), **per-channel session I/O** (`session-io`), **OSC mixer drivers** (`mixer-behringer`), and **LAN Android remote sync** (`remote-server`). Real-time audio stays in C++; disk and UI use separate threads. See [architecture/overview.md](architecture/overview.md) for the full diagram.
