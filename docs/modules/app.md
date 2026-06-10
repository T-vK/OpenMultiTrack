# Module: `app`

**Type:** Android application  
**Package:** `org.openmultitrack.app`

The composition root: Compose UI, Android lifecycle, foreground service, and wiring between all libraries.

## Responsibilities

- Single-activity DAW (`MainActivity` + `DawMainScreen`)
- `MainViewModel` — central `DawUiState`, permission flows, remote role
- `AudioSessionService` — keeps audio alive in background
- `MultiMixerSessionManager` + `MixerSessionController` — per-mixer record/monitor/playback
- Persistence: settings, mixer list, scribble cache, storage paths
- Remote: `RemoteControlManager` bridges UI to `remote-server`

## Key packages

| Package | Contents |
|---------|----------|
| `ui.daw` | Main DAW composables |
| `ui.settings` | Settings screen |
| `service` | Foreground service, session controllers |
| `audio` | `CaptureSessionEngine`, `SessionRecorder`, `SessionPlayer`, monitor/VU |
| `remote` | Command execution, snapshot mapping |
| `data` | `SharedPreferences` stores |
| `scribble` | Flow 8 BLE, OSC LAN discovery |
| `device` | OTG/BT prerequisite UI |
| `root` | Emulator loopback helpers (dev/test) |

## Dependencies

```
app → domain, usb-audio, audio-engine, mixer-behringer, session-io, remote-server
```

## Extension guidelines

- New screens: add under `ui/`; keep business logic in ViewModel or `MixerSessionController`.
- Long-running audio: route through `AudioSessionService`, not Activity lifecycle alone.
- Do not import native JNI directly — use `usb-audio` / `audio-engine` facades.

## Tests

- JVM: `app/src/test/` — waveform math, monitor mixer, layout density
- Instrumented: `app/src/androidTest/` — USB, hardware, E2E remote

See [../development/testing.md](../development/testing.md).

## Related

- [../product/ui-daw.md](../product/ui-daw.md)
- [../architecture/data-flows.md](../architecture/data-flows.md)
