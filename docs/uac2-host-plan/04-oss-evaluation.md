# Open Source Evaluation

## Verdict: build in-tree, borrow ideas from libuac

| Project | License | UAC2 | Multich | Play | Android | Decision |
|---------|---------|------|---------|------|---------|----------|
| **libuac** (nExtCamera) | Apache-2.0 | No (UAC1 partial) | Limited | No | Yes (`wrap(fd)`) | **Reference API + descriptor walk** |
| **Flick** (moss-apps) | OSS (check repo) | Yes (Rust) | DAC-focused | Yes | Yes | **Reference isochronous engine**; different language |
| **libmaru** | LGPL-2.1 | UAC1 | Yes | Yes | No (needs CUSE) | Skip |
| **usbaudio-android-demo** | — | POC | No | Out only | Yes | Sample only |
| **AndroidUsbAudio** (Moriafly) | GPL-3.0 | WIP | WIP | WIP | Yes | Unfinished |
| **TinyUSB** | MIT | Device-side | — | — | — | Wrong direction (gadget) |
| **eXtream** | Proprietary | Yes | Yes | Yes | Yes | **Not usable** in OpenMultiTrack |

## libuac — what to adopt

**Adopt (patterns, not submodule):**

- `uac_context::wrap(int fd)` for Android
- `query_audio_routes(termIn, termOut)` terminal graph walk
- `uac_stream_if::query_config_uncompressed(channels, rate)`
- `stream_cb_func` for isochronous delivery

**Extend beyond libuac:**

- UAC2 descriptor tags (0x20 class revision)
- Playback (libuac is capture-only)
- Multichannel alt-setting negotiation for 10/18/32ch
- Integration with existing `SpscRingBuffer` and session I/O

## Flick — what to learn

Flick implements a **Rust UAC2 isochronous engine** with:

- Descriptor parsing for AC/AS interfaces
- Hot-plug via USB Host API
- Bit-perfect PCM to external DACs

Study their alt-setting and isochronous scheduling. Port concepts to C++ rather than FFI Rust into NDK (adds build complexity).

## Why not submodule libuac

1. UAC1-only — Behringer mixers are UAC2
2. libusb dependency — Android needs `libusb_wrap_sys_device` + JNI; extra native dep for F-Droid
3. Capture-only — we need playback
4. Small community (8 stars) — we own maintenance either way

## License compatibility

New `uac2/` code: **GPL-3.0** (matches app). If we later extract reusable portions, could dual-license headers as Apache-2.0 for `audio-engine` consumers — not required for v1.

Reference-only reading of Linux kernel (`GPL-2.0`) is fine; do not copy large code blocks without attribution and license compatibility review.
