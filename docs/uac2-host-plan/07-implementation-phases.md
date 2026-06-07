# Implementation Phases

## Phase 0 — Planning ✅

- This document set
- Merge debug logging + USB fixes to `main`

## Phase 1 — Descriptor parse + probe (current sprint)

**Deliverables:**

- `uac2_descriptor.cpp` — walk config descriptor, enumerate AS alt settings
- `NativeUac2Probe` JNI + Kotlin data classes
- `UsbAudioProbeService` logs UAC2 caps alongside Oboe probe
- Unit tests with **synthetic descriptor blobs** (Flow 8 / XR18 fixtures from `adb` dumps)

**Acceptance:**

- Flow 8 connected: log shows ≥10ch capture alt setting(s)
- XR18 (when available): ≥18ch in descriptor
- No USB claim required (parse-only)

**Estimate:** ~800–1200 LOC C++, ~200 LOC Kotlin

## Phase 2 — Isochronous capture

**Deliverables:**

- Vendor `libusb` in `third_party/libusb` (pinned tag)
- `android_usb_io.cpp` — wrap FD, claim interface, isochronous IN
- `uac2_capture.cpp` — ring buffer, PCM24 → float
- `Uac2SessionRecorder` parallel to `SessionRecorder`
- Feature flag `BuildConfig.USE_UAC2_CAPTURE` (debug default on)

**Acceptance:**

- Record 10ch WAV from Flow 8 on test Pixel
- `droppedFrames` logged; <0.1% drops over 60s

**Risks:** Kernel driver conflict — may need to document "disconnect from Android audio" or claim before AudioFlinger opens.

## Phase 3 — Isochronous playback

**Deliverables:**

- `uac2_playback.cpp` — isochronous OUT
- `Uac2SessionPlayer` integration
- Underrun counter exposed (mirror existing JNI pattern)

**Acceptance:**

- Play 2ch WAV to Flow 8 USB return
- Virtual soundcheck loop: record 10ch → play 2ch

## Phase 4 — Engine router + production hardening

**Deliverables:**

- `AudioEngineRouter` selects backend automatically
- `docs/compatibility.md` device matrix
- Quirk table for known devices
- Oboe fallback when UAC2 claim fails

**Acceptance:**

- XR18 18ch record session on reference tablet
- F-Droid CI green

## Phase 5 — Mixer milestones (unchanged roadmap)

- Multitrack seek, OSC snapshots, web remote — on top of reliable UAC2 I/O

## Immediate next commits (Phase 1)

1. `docs(uac2): add host library development plan`
2. `feat(uac2): add config descriptor parser and JNI probe`
3. `feat(usb): log UAC2 descriptor capabilities during probe`
