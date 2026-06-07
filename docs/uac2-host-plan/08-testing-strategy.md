# Testing Strategy

## Unit tests (host JVM / native)

| Test | Location | Method |
|------|----------|--------|
| Descriptor parse | `audio-engine/src/test/cpp/uac2_descriptor_test.cpp` | Flow 8 fixture + synthetic XR18 |
| Alt selection | same | Assert best alt for 10ch@48k / 18ch playback |
| PCM conversion | `uac2_format_test.cpp` (Phase 2) | Round-trip 24-bit LE |

Run host parser tests:

```bash
./scripts/run-uac2-native-tests.sh
```

Capture real descriptors:

```bash
adb shell dumpsys usb
# Or save UsbDeviceConnection.getRawDescriptors() from debug build to fixtures/
```

Store fixtures in `audio-engine/src/test/resources/uac2/`:

- `flow8_recording_mode.bin` (captured from host `sysfs` / `UsbDeviceConnection.getRawDescriptors()`)
- XR18: synthetic builder in tests until hardware is available

## Instrumented tests (device / emulator)

| Test | Module | Needs USB |
|------|--------|-----------|
| `Uac2FixtureInstrumentedTest` | audio-engine | No (assets) |
| `Xr18VirtualSoundcheckInstrumentedTest` | app | No (synthetic) |
| `Flow8HardwareInstrumentedTest` | audio-engine | Yes (Flow 8) |
| `UsbAudioRecordingInstrumentedTest` | app | Yes (Flow 8) |

Emulator + Flow 8 passthrough (Linux):

```bash
./scripts/run-emulator-with-flow8.sh
# Grant USB permission in the app UI, then:
./scripts/run-uac2-instrumented-tests.sh emulator-5554 hardware
```

Hardware tests skip automatically when Flow 8 is not attached (`@RequiresUsbDevice`).

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
