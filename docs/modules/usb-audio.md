# Module: `usb-audio`

**Type:** Android library  
**Package:** `org.openmultitrack.usb`

USB device layer: enumeration, permissions, probe orchestration, and **Oboe vs UAC2 routing**.

## Responsibilities

- List USB audio devices and correlate with Android `AudioDeviceInfo` (Oboe `deviceId`)
- Request/grant USB permission (`UsbPermissionHelper`)
- Run full probe (`UsbAudioProbeService`): Oboe channel counts + UAC2 descriptor parse
- Select capture/playback backend (`AudioEngineRouter`)
- Exclusive capture ownership (`NativeAudioCaptureRegistry`)
- Behringer/Midas heuristics (`BehringerUsbIdentifiers`)

## Audio backends

| Backend | When selected |
|---------|----------------|
| `OBOE` | AAudio reports sufficient channels; default path |
| `UAC2` | libusb isoch path when Oboe under-reports vs mixer descriptor |

Router types: `CaptureRoute`, `PlaybackRoute` with optional `UsbAudioStreamHandle` for FD passing to native code.

## Key flows

1. `UsbAudioEnumerator` discovers devices → UI list
2. User grants permission → open `UsbAudioStreamHandle` if UAC2 needed
3. `UsbAudioProbeService.probe()` → `FullUsbProbeResult`
4. `AudioEngineRouter.resolveCaptureRoute()` → backend for `CaptureSessionEngine`

## Extension guidelines

- New VID/PID: update `BehringerUsbIdentifiers` and [../hardware-assumptions.md](../hardware-assumptions.md).
- Do not open Oboe streams here — delegate to `audio-engine` after route resolution.
- Detach/attach: emit events to app service for dropout handling.

## Tests

`BehringerUsbIdentifiersTest` — identifier matching.

Hardware: `UsbAudioRecordingInstrumentedTest`, probe flows on device.

## Related

- [../modules/audio-engine.md](audio-engine.md)
- [../uac2-host-plan/README.md](../uac2-host-plan/README.md)
