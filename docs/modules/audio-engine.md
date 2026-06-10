# Module: `audio-engine`

**Type:** Android library + NDK (CMake)  
**Packages:** `org.openmultitrack.audio` (JNI), `src/main/cpp/` (native)

Real-time audio: Oboe streams, UAC2 isoch via vendored libusb, SPSC ring buffers.

## Responsibilities

- Probe input/output channel counts (`NativeAudioProbe`, `audio_probe.cpp`)
- Record: input callback → ring buffer (`NativeAudioEngine`, `audio_recorder.cpp`)
- Playback: ring buffer → output callback (`audio_player.cpp`)
- Monitor output (`NativeAudioMonitor`, `audio_monitor.cpp`)
- UAC2: descriptor parse, alt-setting selection, isoch transfer (`uac2/` tree)
- JNI bridges: `jni_bridge.cpp`, `uac2_engine_jni.cpp`, `uac2_jni.cpp`

## Native library

- Name: `openmultitrack_audio`
- ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- Links: Oboe (submodule), static libusb from `third_party/libusb`

## Kotlin JNI facades

| Class | Purpose |
|-------|---------|
| `NativeAudioProbe` | Open probe streams, return channel counts |
| `NativeAudioEngine` | Start/stop record and playback, read/write rings, drop counters |
| `NativeAudioMonitor` | Monitor path |
| `NativeUac2Probe` | Parse UAC2 descriptors from USB FD |
| `NativeUac2Engine` | UAC2 capture/playback |
| `OmtLog` | Native log lines to logcat |

## Real-time rules

On Oboe/UAC2 callback threads:

- No allocation, locks, or I/O
- Only SPSC ring copy and meter taps
- Backpressure via drop/underrun counters

See [../architecture/threading.md](../architecture/threading.md).

## Tests

| Kind | Location |
|------|----------|
| Host C++ | `src/test/cpp/uac2_descriptor_test.cpp` via `scripts/run-uac2-native-tests.sh` |
| Device | `Flow8HardwareInstrumentedTest`, `Uac2FixtureInstrumentedTest` |

No gtest harness for full Oboe record/playback yet.

## Extension guidelines

- Seek/flush: implement in `audio_player.cpp` + expose via JNI before UI wiring.
- Meter taps: keep work O(channels) per callback with fixed buffers.
- UAC2 changes: run descriptor tests + on-device Flow 8/XR18 validation.

## Related

- [../architecture/data-flows.md](../architecture/data-flows.md)
- [../technical-risks.md](../technical-risks.md)
