# Architecture

## Layer diagram

```
┌─────────────────────────────────────────────────────────────┐
│  app (Compose, SessionRecorder/Player, MainViewModel)        │
├─────────────────────────────────────────────────────────────┤
│  usb-audio (enumeration, permission, probe orchestration)    │
├─────────────────────────────────────────────────────────────┤
│  audio-engine (Kotlin JNI facade)                            │
│    AudioEngineRouter  ← selects UAC2 vs Oboe per device      │
│    NativeAudioEngine  (Oboe — existing)                      │
│    NativeUac2Engine   (new)                                  │
├─────────────────────────────────────────────────────────────┤
│  omt-uac2 (C++17, audio-engine/src/main/cpp/uac2/)          │
│    Uac2Descriptor   — config descriptor parse                │
│    Uac2StreamConfig — alt setting, format, endpoints       │
│    AndroidUsbIo     — JNI: fd, claim, isochronous I/O      │
│    Uac2Capture      — IN isochronous → ring buffer           │
│    Uac2Playback     — ring buffer → OUT isochronous          │
│    Uac2Quirks       — device-specific overrides              │
├─────────────────────────────────────────────────────────────┤
│  Android USB Host API (UsbDeviceConnection file descriptor)  │
├─────────────────────────────────────────────────────────────┤
│  Linux kernel usbcore + snd-usb-audio (may hold interfaces)  │
└─────────────────────────────────────────────────────────────┘
```

## Module placement

All native UAC2 code lives under **`audio-engine/src/main/cpp/uac2/`** and links into existing `openmultitrack_audio` shared library. No new Gradle module initially — keeps JNI surface in one `.so`.

Kotlin API additions in **`org.openmultitrack.audio`**:

- `Uac2DescriptorProbe` — parse-only, no streaming
- `Uac2Engine` — capture/playback (phases 2–3)
- `AudioBackend` enum — `OBOE`, `UAC2`

## AudioEngineRouter selection logic

```
if (uac2Probe.maxInputChannels > oboeProbe.inputChannels
    && uac2CanClaimInterface(usbFd)) {
    use UAC2 for record
} else {
    use Oboe (existing)
}
```

Playback may independently choose UAC2 when Oboe output is stereo-limited.

## Threading model (matches existing engine)

| Thread | Responsibility |
|--------|----------------|
| USB isochronous callback (dedicated `std::thread` or Android async I/O) | Fill/drain ring buffers only |
| Existing writer/reader coroutines (`SessionRecorder`) | Drain UAC2 ring → WAV |
| Main | UI, no audio |

Reuse existing `SpscRingBuffer` from Oboe recorder/player where possible, or extract to shared `ring_buffer.h`.

## Data formats

- **Wire format**: PCM Type I/III per descriptor (Flow 8/XR18: typically 24-bit in 3 bytes, little-endian).
- **Internal format**: `float` interleaved in ring buffers (consistent with Oboe path and `WavWriter`).
- **Conversion**: `pcm24le_to_float()` in `uac2_format.cpp` — hot path, SIMD optional later.

## File layout (target)

```
audio-engine/src/main/cpp/uac2/
  uac2_types.h
  uac2_descriptor.h / .cpp
  uac2_stream_config.h / .cpp
  uac2_format.h / .cpp
  android_usb_io.h / .cpp
  uac2_capture.h / .cpp
  uac2_playback.h / .cpp
  uac2_quirks.h / .cpp
  uac2_jni.cpp
```
