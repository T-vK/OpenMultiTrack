# Architectural decisions

Key decisions already made in this codebase. Format is lightweight ADR-style: context, decision, consequences. Dates are approximate from git history and docs — not maintenance-critical.

---

## ADR-001: MVVM + Jetpack Compose for UI

**Context:** Need a maintainable tablet UI with complex channel-strip state and remote mirroring.

**Decision:** Single-activity Compose app; `MainViewModel` holds `DawUiState`; per-mixer logic in `MixerSessionController` behind `AudioSessionService`.

**Consequences:** No XML layouts for main DAW; state flows one direction; large ViewModel is a known concentration point.

---

## ADR-002: Native C++17 + Oboe for real-time audio

**Context:** Android audio callbacks require predictable latency; Kotlin GC is unsuitable on the hot path.

**Decision:** `audio-engine` module with Oboe for the default path; JNI facades in Kotlin.

**Consequences:** NDK r26d required; Oboe git submodule mandatory; native changes need device testing.

---

## ADR-003: Dual audio backend — Oboe and UAC2/libusb

**Context:** Some devices report fewer channels via AAudio/Oboe than the mixer exposes via UAC2 descriptors (notably high channel counts).

**Decision:** `AudioEngineRouter` in `usb-audio` selects `AudioBackend.OBOE` or `AudioBackend.UAC2` based on probe results and requested channel count.

**Consequences:** Vendored libusb; larger native surface; more test matrix (see `uac2-host-plan/`).

---

## ADR-004: Per-channel WAV files + session.json

**Context:** Virtual soundcheck needs independent channel seek/mute; interleaved-only files complicate partial I/O.

**Decision:** One 24-bit PCM WAV per channel under a timestamped session directory; `session.json` holds metadata, labels, timeline frame count.

**Consequences:** More files per session; simpler seek per channel; no RF64 yet for single huge files.

---

## ADR-005: `Mixer` interface + per-model OSC drivers

**Context:** X32 and XR18 share USB audio but differ in OSC ports and some addresses.

**Decision:** `domain/mixer/Mixer.kt` abstraction; `mixer-behringer` implements `X32Mixer`, `Xr18Mixer`.

**Consequences:** Snapshot apply/capture still stubbed; routing command lists live in docs until hardware-validated.

---

## ADR-006: Android-to-Android LAN remote (not browser SPA)

**Context:** Need a second tablet for FOH control without duplicating USB wiring.

**Decision:** `remote-server` module with NanoHTTPD WebSocket + OkHttp client + UDP discovery. Remote runs the same app in Remote role.

**Consequences:** No embedded HTML/JS control UI; [control-api.md](../control-api.md) browser/Ktor draft is obsolete.

---

## ADR-007: GPLv3-or-later application license

**Context:** F-Droid distribution; copyleft alignment with user freedom.

**Decision:** GPLv3 for app code; Apache-2.0 Oboe linked in same APK.

**Consequences:** Contributions are GPL; proprietary SDKs excluded.

---

## ADR-008: Pinned debug keystore for all published APKs

**Context:** F-Droid self-hosted repo and GitHub Releases should install over each other.

**Decision:** Committed `keystore/debug.keystore`; CI builds only `assembleDebug`.

**Consequences:** Users on pre-pin builds must uninstall once; release signing deferred — see [../ci-and-releases.md](../ci-and-releases.md).

---

## ADR-009: Conventional Commits → semver automation

**Context:** Frequent main-branch publishes to F-Droid repo.

**Decision:** `scripts/compute-semver.sh` on `main`; version code `major*10000 + minor*100 + patch`.

**Consequences:** Commit message format matters; git hooks recommended (`scripts/install-git-hooks.sh`).

---

## ADR-010: Scribble strip read-only

**Context:** Channel naming on mixer hardware is operator-owned.

**Decision:** Import labels/colors via OSC or BLE; cache locally; never write back to mixer.

**Consequences:** Flow 8 BLE is manual/single-client; XR18 uses LAN OSC discovery.

---

## Revisit triggers

| Decision | Revisit when… |
|----------|----------------|
| GPLv3 vs AGPLv3 | Shipping a separately hosted control server without APK distribution |
| Debug-only signing | F-Droid official inclusion requires release/reproducible proof |
| NanoHTTPD remote | Need browser-based remote or TLS requirements beyond LAN-trust |
| Per-channel WAV only | Sessions routinely exceed 4 GB per file and need RF64/W64 |
