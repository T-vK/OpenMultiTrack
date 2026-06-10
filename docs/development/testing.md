# Testing

OpenMultiTrack uses layered tests: JVM unit tests (fast, CI), host-side native tests, Android instrumented tests (device/emulator), and optional hardware/E2E scripts.

## CI (every PR)

From `.github/workflows/ci.yml`:

```bash
./scripts/run-uac2-native-tests.sh
./gradlew :domain:test :mixer-behringer:test :session-io:test \
  :usb-audio:testDebugUnitTest :remote-server:test \
  :audio-engine:assembleDebugAndroidTest \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

Instrumented tests are **assembled** but not executed in CI (no USB hardware in runners).

## Unit tests (local, no device)

```bash
./gradlew :domain:test :mixer-behringer:test :session-io:test \
  :usb-audio:testDebugUnitTest :remote-server:test

# App JVM tests (waveform math, layout helpers)
./gradlew :app:testDebugUnitTest
```

| Module | Example tests |
|--------|----------------|
| `domain` | `RecordingChannelsTest`, `OscPathTest` |
| `session-io` | `WavRoundTripTest`, `PerChannelWavReaderTest`, `SessionWaveformExtractorTest` |
| `mixer-behringer` | `OscUdpClientTest`, `Xr18ScribbleImporterTest`, `Flow8StateDecoderTest` |
| `usb-audio` | `BehringerUsbIdentifiersTest` |
| `remote-server` | `RemoteJsonCodecTest`, `RemoteWaveformUtilTest` |
| `app` | `MonitorMixerTest`, `LiveWaveformRingTest`, `SoundcheckWaveformTest` |

## Native host tests (UAC2 descriptors)

```bash
./scripts/run-uac2-native-tests.sh
```

Builds and runs `audio-engine/src/test/cpp/uac2_descriptor_test.cpp` on the development host (no Android device).

## Android instrumented tests

Requires connected device or emulator with `adb`.

```bash
# All app instrumented tests (long; many need USB hardware)
./gradlew :app:connectedDebugAndroidTest

# UAC2 fixture tests
./scripts/run-uac2-instrumented-tests.sh
```

### Hardware-tagged tests

Many tests use custom rules (`UsbDeviceRule`, `RequiresUsbDevice`) and expect specific gear:

| Script / test area | Hardware |
|--------------------|----------|
| `scripts/run-xr18-hardware-tests.sh` | Behringer XR18 over USB |
| `scripts/run-flow8-hardware-tests.sh` | Flow 8 + emulator passthrough |
| `Xr18HardwareInstrumentedTest` | Physical XR18 |
| `Flow8VirtualSoundcheckInstrumentedTest` | Flow 8 |
| `UsbAudioRecordingInstrumentedTest` | Generic USB audio device |

Grant USB permission helpers: `scripts/grant-usb-permission.sh`, `scripts/grant-emulator-usb-host.sh`

## Dual-device E2E (LAN remote)

Two Android devices on the same LAN (or TCP bridge if AP isolation blocks tablet-to-tablet):

```bash
./scripts/run-dual-device-e2e-tests.sh
```

Tests in `app/src/androidTest/.../e2e/`:

| Test | Scenario |
|------|----------|
| `RemoteE2eHostTest` / `RemoteE2eClientTest` | Host/Remote pairing and sync |
| `HostLocalE2eTest` | Host-only flows |
| `InterruptedRecordingResumeE2eTest` | USB dropout / resume |
| `HostZoomE2eTest` | Soundcheck zoom UI |

If Wi‑Fi AP isolation prevents discovery, use `scripts/tcp-bridge.py` to relay ports 8765/8766.

## E2E harness building blocks

| File | Role |
|------|------|
| `E2eRemoteHarness.kt` | Remote role setup |
| `E2eMixerHarness.kt` | Mixer/session shortcuts |
| `E2eAppRule.kt`, `E2eConfig.kt` | JUnit rules and config |
| `E2eLanSync.kt` | Wait for sync conditions |

## What is not covered well yet

- Full native Oboe record/playback gtest harness on device
- Automated XR18/X32 OSC snapshot round-trip
- Soak tests for multi-hour sessions
- CI execution of instrumented/E2E suites

Prefer adding tests at the **lowest** layer that can express the invariant.

## Related

- [getting-started.md](getting-started.md) — build prerequisites
- [../hardware-assumptions.md](../hardware-assumptions.md) — what to verify on real gear
