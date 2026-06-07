# UAC2 Host Library — Development Plan

OpenMultiTrack needs multichannel USB record/playback on Android for class-compliant mixers (Flow 8, XR18, X32). The stock Oboe/AAudio path often negotiates stereo-only or fails to start capture streams. This plan defines **omt-uac2**, a userspace USB Audio Class 2.0 host library integrated into `audio-engine`, with Oboe retained as a fallback.

## Documents

| # | Document | Purpose |
|---|----------|---------|
| 1 | [01-goals-and-constraints.md](01-goals-and-constraints.md) | Success criteria, FOSS/F-Droid constraints, non-goals |
| 2 | [02-architecture.md](02-architecture.md) | Module layout, data flow, integration with existing engine |
| 3 | [03-linux-kernel-reference.md](03-linux-kernel-reference.md) | What to reuse from `snd-usb-audio` vs what to reimplement |
| 4 | [04-oss-evaluation.md](04-oss-evaluation.md) | libuac, Flick, libmaru, eXtream — adopt vs build |
| 5 | [05-android-usb-access.md](05-android-usb-access.md) | FD passing, interface claim, kernel driver conflict |
| 6 | [06-api-design.md](06-api-design.md) | Public C++/JNI/Kotlin API |
| 7 | [07-implementation-phases.md](07-implementation-phases.md) | Phased delivery with acceptance criteria |
| 8 | [08-testing-strategy.md](08-testing-strategy.md) | Unit, on-device, hardware matrix |
| 9 | [09-risks-and-mitigations.md](09-risks-and-mitigations.md) | Technical risks and fallbacks |

## Decision summary

**Build in-tree** (`audio-engine/src/main/cpp/uac2/`), inspired by Linux `sound/usb/` and structurally similar to [libuac](https://github.com/nExtCamera/libuac), extended to **UAC2**, **capture + playback**, and **multichannel**. Do **not** vendor libuac as a submodule (UAC1-only, libusb dependency, capture-only). Do **not** depend on Termux/QEMU/CUSE paths.

## Current status

| Phase | Status |
|-------|--------|
| Phase 0 — Plan | Complete (this directory) |
| Phase 1 — Descriptor parse + probe | In progress |
| Phase 2 — Isochronous capture | Pending |
| Phase 3 — Isochronous playback | Pending |
| Phase 4 — Engine integration + Oboe fallback | Pending |
