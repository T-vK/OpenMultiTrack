# Codebase map

Where to look when working on a feature. Paths are relative to the repository root; package roots are `org.openmultitrack.*`.

## Top-level layout

```
app/              Android application
domain/           Pure Kotlin domain
usb-audio/        USB + audio routing
audio-engine/     JNI + C++ native audio
mixer-behringer/  OSC mixer drivers
session-io/       Session files and WAV I/O
remote-server/    LAN remote protocol
docs/             Documentation (this tree)
scripts/          Build, test, F-Droid automation
third_party/oboe/ Git submodule
third_party/libusb/ Vendored sources
```

## `app` — `org.openmultitrack.app`

| Area | Path | Key types |
|------|------|-----------|
| Entry | `app/src/main/kotlin/.../MainActivity.kt` | Activity, permissions |
| UI state | `.../MainViewModel.kt` | `DawUiState`, orchestration |
| DAW UI | `.../ui/daw/` | `DawMainScreen`, `ChannelStripUi`, `SoundcheckPanel`, `DawTopBar` |
| Settings | `.../ui/settings/SettingsScreen.kt` | Global preferences |
| Foreground service | `.../service/AudioSessionService.kt` | Long-running audio lifecycle |
| Per-mixer engine | `.../service/MixerSessionController.kt` | Record, monitor, playback per profile |
| Multi-mixer | `.../service/MultiMixerSessionManager.kt` | Active mixer, session map |
| Capture / play | `.../audio/CaptureSessionEngine.kt`, `SessionRecorder.kt`, `SessionPlayer.kt` | Shared USB capture, WAV I/O wiring |
| Monitor / VU | `.../audio/MonitorMixer.kt`, `LiveWaveformRing.kt` | Solo, meters, live peaks |
| Remote wiring | `.../remote/RemoteControlManager.kt`, `RemoteCommandExecutor.kt` | Host/Remote roles |
| Persistence | `.../data/AppSettingsStore.kt`, `MixerDeviceStore.kt`, `RecordingStorageResolver.kt` | Preferences, storage paths |
| Scribble | `.../scribble/` | Flow 8 BLE, OSC LAN discovery |
| Manifest filters | `app/src/main/res/xml/usb_device_filter.xml` | USB attach intent |

## `domain` — `org.openmultitrack.domain`

| Package | Key types |
|---------|-----------|
| `audio` | `AudioConstants`, `RecordingChannels`, `UsbAudioDeviceDescriptor` |
| `session` | `RecordingSession`, `TransportState`, `AppMode`, `SessionFormat` |
| `mixer` | `Mixer`, `MixerProfile`, `MixerSnapshot`, `MixerRoutingConfig` |
| `channel` | `ChannelStripState`, `ChannelColors` |
| `remote` | `RemoteProtocol`, `RemoteRole`, `RemoteConnectionState` |

No Android imports — safe for JVM unit tests.

## `usb-audio` — `org.openmultitrack.usb`

| Type | Role |
|------|------|
| `UsbAudioEnumerator` | List devices, map to Oboe `deviceId` |
| `UsbAudioProbeService` | Full probe: Oboe + UAC2 caps |
| `UsbPermissionHelper` | Permission `PendingIntent` flow |
| `BehringerUsbIdentifiers` | VID/PID/name heuristics |
| `AudioEngineRouter` | Oboe vs UAC2 selection |
| `UsbAudioStreamHandle` | USB FD for libusb path |
| `NativeAudioCaptureRegistry` | Exclusive capture ownership |

## `audio-engine`

**Kotlin JNI** — `audio-engine/src/main/java/org/openmultitrack/audio/`:

| Object | Role |
|--------|------|
| `NativeAudioProbe` | Channel count probe |
| `NativeAudioEngine` | Oboe record/playback |
| `NativeAudioMonitor` | Monitor output |
| `NativeUac2Probe` / `NativeUac2Engine` | UAC2 isoch path |

**C++** — `audio-engine/src/main/cpp/`:

| Path | Role |
|------|------|
| `jni_bridge.cpp` | JNI entry (Oboe) |
| `audio_recorder.cpp`, `audio_player.cpp`, `audio_monitor.cpp` | Stream callbacks |
| `audio_probe.cpp` | Probe streams |
| `spsc_ring_buffer.h` | Lock-free rings |
| `uac2/` | Descriptor parse, isoch capture/playback, libusb session |

## `session-io` — `org.openmultitrack.sessionio`

| Package | Key types |
|---------|-----------|
| `session` | `SessionDirectory`, `SessionMetadata`, `SessionLibrary`, `SessionCueFile` |
| `wav` | `PerChannelWavWriter`, `PerChannelWavReader`, `WavWriter`, `WavReader` |
| `wav` (cache) | `SessionWaveformExtractor`, `SessionWaveformCache` |

## `mixer-behringer` — `org.openmultitrack.mixer.behringer`

| Type | Role |
|------|------|
| `X32Mixer`, `Xr18Mixer` | `Mixer` implementations |
| `OscUdpClient` | UDP encode/send |
| `OscPath`, `OscMessageDecoder` | Address constants, parsing |
| `Xr18ScribbleImporter` | XR18 strip labels via OSC |
| `Flow8StateDecoder`, `Flow8UsbScribbleMapper` | Flow 8 USB state |

## `remote-server` — `org.openmultitrack.remote`

| Type | Role |
|------|------|
| `RemoteHostServer` | NanoHTTPD + WebSocket |
| `RemoteClient` | OkHttp WebSocket client |
| `RemoteDiscovery` | UDP broadcast |
| `RemoteJsonCodec` | Snapshot/delta/command JSON |

## Tests (by category)

| Category | Location |
|----------|----------|
| JVM unit | `*/src/test/kotlin/` in each module |
| Android instrumented | `app/src/androidTest/`, `audio-engine/src/androidTest/` |
| E2E remote | `app/src/androidTest/.../e2e/` |
| Native host (UAC2) | `audio-engine/src/test/cpp/`, `scripts/run-uac2-native-tests.sh` |

See [testing.md](testing.md) for how to run them.

## Build configuration

| File | Purpose |
|------|---------|
| `settings.gradle.kts` | Module includes |
| `gradle/libs.versions.toml` | Version catalog |
| `gradle/version.properties` | App version |
| `app/build.gradle.kts` | App deps, signing |
| `audio-engine/build.gradle.kts` | NDK, CMake, ABIs |

## Related

- [../modules/](../modules/) — per-module deep dives
- [../architecture/data-flows.md](../architecture/data-flows.md) — runtime paths
