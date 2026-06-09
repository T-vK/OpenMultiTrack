# Remote control architecture (OMT Remote Sync)

OpenMultiTrack instances on the same LAN can pair as **Host** (engine) and **Remote** (controller).
The Host owns the USB mixer, audio engine, and session files. The Remote mirrors UI state and
forwards user actions; it never opens a competing USB capture stream.

## Roles

| Role | Device | Responsibility |
|------|--------|----------------|
| **Host** | Tablet at the mixer | USB audio, recording, monitoring, soundcheck playback |
| **Remote** | Second tablet / phone | Transport, strip controls, waveform view, settings UI |

Only one Host per mixer session. Many Remotes may connect (read-only meters; last-writer wins for transport).

## Discovery and transport

```
Remote                         LAN                          Host
  |-- UDP broadcast OMT_DISCOVER (8766) ------------------>|
  |<------------- OMT_ANNOUNCE {ip, port, name} ----------|
  |-- WS connect ws://host:8765/api/v1/ws ---------------->|
  |<------------- snapshot (full state) -------------------|
  |<============= delta (20 Hz meters/transport) =========>|
  |-- command (arm, record, seek, …) --------------------->|
  |<------------- command_ack / error ---------------------|
  |-- waveform_request (channel, window) ----------------->|
  |<------------- waveform_chunk (downsampled) ----------|
```

- **Port 8765** — HTTP status + WebSocket sync (cleartext on LAN; optional bearer token).
- **Port 8766** — UDP discovery request/response.
- **REST** `GET /api/v1/status` — health probe without WebSocket.

## Sync strategy

### Settings and session (low rate)

On connect the Host sends a **snapshot** JSON document:

- Global UI settings (`showVuMeters`, waveform windows, strip display modes, monitor gain).
- Active mixer id and per-mixer `MixerSessionUiState` without heavy binary fields.
- Channel strips (labels, arm/monitor/solo, colors, icons).
- Soundcheck library list and selected session metadata.

Settings changes on either side are propagated as snapshot patches (`settings` delta).

### Transport and meters (high rate, compact)

Every 50 ms the Host pushes a **delta** frame:

- `transportState`, `recordElapsedSec`, `playbackPositionSec`, `isRecording`, `isPlaying`, …
- `captureMeterLevels` / `soundcheckMeterLevels` — map of channel index → float 0..1.
- Only changed keys are included (JSON object diff).

### Waveforms (on demand, bandwidth aware)

**Live recording waveforms**

- Host maintains a generation counter per channel ring buffer.
- Delta may include `liveWaveforms`: `{ ch: { gen, tail: [uint8×N] } }` where peaks are
  quantized to 8-bit (0–255) for the last 64 samples only (~64 bytes/channel/frame).

**Soundcheck waveforms**

- Snapshot carries overview metadata only (`durationSec`, `peaksPerSec`, channel list).
- Remote requests `waveform_request { sessionDir, channel, startSec, windowSec, maxPoints }`.
- Host responds with `waveform_chunk { channel, startSec, peaks: [float] }` downsampled to
  `maxPoints` (default 400) using peak-picking decimation.

This avoids shipping multi-megabyte overview arrays on every tick.

## Command model

Remote → Host commands map 1:1 onto existing `MixerSessionController` / `MainViewModel` APIs:

| Command | Action |
|---------|--------|
| `set_active_mixer` | `MultiMixerSessionManager.setActiveMixer` |
| `set_app_mode` | `setAppMode` |
| `toggle_arm` / `toggle_monitor` / `toggle_solo` | `updateChannelStrip` |
| `start_record` / `stop_record` | recording lifecycle |
| `start_monitor` / `stop_monitor` | monitor lifecycle |
| `toggle_playback` / `stop_playback` / `seek` | soundcheck transport |
| `select_soundcheck` | `selectSoundcheckSession` |
| `set_soundcheck_view` / `set_loop` | view + loop region |
| `set_settings` | partial `AppSettingsStore` update |

The Host executes commands on the service thread and broadcasts the resulting state.

## Module layout

```
domain/remote/          — Role enum, protocol constants
remote-server/          — NanoHTTPD WebSocket host, OkHttp client, discovery, codecs
app/remote/             — RemoteControlManager, command executor, ViewModel bridge
```

## Security

- LAN-trusted by default (same Wi‑Fi as the mixer).
- Optional `remoteAuthToken` in settings; Host rejects WebSocket handshakes without
  `Authorization: Bearer …` when configured.
- Cleartext HTTP permitted only for private LAN via `network_security_config.xml`.

## UI

Settings → **Remote control**:

1. Off / Host / Remote
2. Host: shows local IP + “Broadcasting on port 8765”
3. Remote: scan button, host list, connect/disconnect

When connected as Remote, the main DAW screen renders mirrored `sessionByMixer` from the Host.
A persistent banner shows “Remote → {hostName}”.
