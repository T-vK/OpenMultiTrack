# DAW user interface

The main experience is a **single-activity DAW** (`DawMainScreen`) optimized for landscape tablets. State is centralized in `MainViewModel` as `DawUiState` and per-mixer `MixerSessionUiState`.

## Screen regions

```
┌──────────────────────────────────────────────────────────────────┐
│ DawTopBar — mode, transport, mixer picker, remote, settings    │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Channel strips (horizontal scroll)                              │
│  ┌────┐ ┌────┐ ┌────┐ ┌────┐                                     │
│  │Ch1 │ │Ch2 │ │Ch3 │ │... │  arm / monitor / solo / label / VU  │
│  │wave│ │wave│ │wave│ │    │                                     │
│  └────┘ └────┘ └────┘ └────┘                                     │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│ SoundcheckPanel (when in playback modes) — timeline, loop, seek   │
└──────────────────────────────────────────────────────────────────┘
```

## Channel strip (`ChannelStripUi`)

Each strip reflects `ChannelStripState`:

| Control | Record mode | Soundcheck mode |
|---------|-------------|-----------------|
| **Arm** | Include channel in multitrack WAV | Hidden or disabled per settings |
| **Monitor** | Route channel to monitor mix | N/A or simplified |
| **Solo** | Monitor solo | Playback solo |
| **Label / color** | From scribble import or user edit | Same |
| **Waveform** | Live tail while recording | Session overview + zoom window |
| **VU meter** | Capture levels | Playback/output levels |

Strip visibility toggles live in **Settings** (hide arm/monitor/solo, disable waveforms for performance).

## Top bar (`DawTopBar`)

| Element | Behavior |
|---------|----------|
| App mode selector | `MULTITRACK_RECORD` / `VIRTUAL_SOUNDCHECK` / `SIMPLE_PLAY` |
| Record / stop | Starts per-channel session under active mixer folder |
| Monitor toggle | Low-latency monitor path without recording |
| Mixer picker | `MixerPickerSheet` — add, remove, set active mixer |
| Remote indicator | Host broadcasting or Remote connected banner |
| Settings | Storage, strip display, remote role, logs |

Mode labels adapt to width: full → short → abbreviated → icon-only (`AppModeLabelDensity`).

## Soundcheck panel (`SoundcheckPanel`)

Visible in playback modes:

- Session picker (`SoundcheckSessionPickerScreen`) — library per mixer
- Transport: play/pause/stop, position, seek
- Loop region handles (in/out markers)
- Waveform zoom levels (overview vs windowed peaks)

Waveform rendering uses **cached peaks** only — never full PCM decode on the main thread.

## Sheets and secondary screens

| UI | Purpose |
|----|---------|
| `MixerSettingsSheet` | Per-mixer routing notes, scribble refresh |
| `RemoteControlSheet` | Off / Host / Remote, discovery, pairing QR |
| `SettingsScreen` | Global prefs, storage path, log viewer |
| Prerequisite banners | OTG, audio permission, Bluetooth for Flow 8 |

## Remote mirroring

When the app runs as **Remote**:

- `DawMainScreen` renders `sessionByMixer` from Host snapshots/deltas.
- Banner: `Remote → {hostName}`.
- User actions send commands; Host executes on service thread.
- Meters and transport update at ~20 Hz via WebSocket delta.

## State ownership

| State | Owner |
|-------|-------|
| Global UI settings | `AppSettingsStore` |
| Mixer list / active id | `MixerDeviceStore` + `MainViewModel` |
| Per-mixer audio/session | `MixerSessionController` in `AudioSessionService` |
| Remote connection | `RemoteControlManager` |

## UX constraints for designers

- Minimum touch targets for live show use (gloves, dim light).
- Critical actions (record stop) must be hard to trigger accidentally.
- Degraded states need clear copy: USB detached, disk low, remote disconnect.
- No dependency on system WebView for core DAW — pure Compose.

## Related

- [overview.md](overview.md) — modes and hardware
- [../remote-control.md](../remote-control.md) — sync protocol
- [../modules/app.md](../modules/app.md) — implementation packages
