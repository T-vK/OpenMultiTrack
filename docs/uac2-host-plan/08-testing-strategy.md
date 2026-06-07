# Testing Strategy

## Unit tests (host JVM / native)

| Test | Location | Method |
|------|----------|--------|
| Descriptor parse | `audio-engine/src/test/cpp/uac2_descriptor_test.cpp` or GoogleTest in CI | Synthetic byte arrays |
| Alt selection | same | Assert best alt for 10ch@48k |
| PCM conversion | `uac2_format_test.cpp` | Round-trip 24-bit LE |

Capture real descriptors:

```bash
adb shell dumpsys usb
# Or save UsbDeviceConnection.getRawDescriptors() from debug build to fixtures/
```

Store fixtures in `audio-engine/src/test/resources/uac2/`:

- `flow8_recording_mode.bin`
- `xr18_18ch.bin`
- `stereo_only.bin` (negative case)

## Instrumented tests (device)

- `Uac2ProbeInstrumentedTest` — device connected, parse descriptors, assert channel count > 2 for Flow 8
- Skip in CI without hardware (`@RequiresDevice`)

## Manual hardware matrix

| Device | Flow 8 | XR18 | X32 |
|--------|--------|------|-----|
| Pixel 4 (reference) | Phase 1–2 | — | — |
| Samsung Tab (USB host) | Phase 4 | Phase 4 | Phase 4 |

Record per device:

- Oboe probe result
- UAC2 descriptor max channels
- UAC2 capture actual channels
- Drop/underrun counts

## CI

- Phase 1: native parser tests in GitHub Actions (add CMake test target)
- Existing `./gradlew :domain:test` unchanged
- No USB hardware in CI — fixtures only

## Regression signals

- `OMT_LOG` / `OpenMultiTrack/Uac2` tag filters
- Compare `framesWritten` vs `droppedFrames` ratio in stop message
- WAV channel count in header vs requested
