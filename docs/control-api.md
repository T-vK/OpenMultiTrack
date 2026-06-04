# Control API (draft)

Embedded Ktor server (milestone 4). All endpoints on `http://<device-ip>:8765/` — no CDN, no third-party assets.

## REST

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/status` | Engine, USB, disk, mixer connection |
| GET | `/api/v1/session` | Active session metadata |
| POST | `/api/v1/session` | Create session `{ name, sampleRate, channelCount }` |
| POST | `/api/v1/transport/play` | Start playback |
| POST | `/api/v1/transport/pause` | Pause |
| POST | `/api/v1/transport/stop` | Stop + flush |
| POST | `/api/v1/transport/seek` | `{ frame: Long }` sample position |
| POST | `/api/v1/record/start` | Arm record |
| POST | `/api/v1/record/stop` | Finalize files |
| GET | `/api/v1/snapshots` | List stored mixer snapshots |
| POST | `/api/v1/snapshots/{id}/recall` | Apply snapshot |

## WebSocket `/api/v1/ws`

Server → client events (JSON):

```json
{ "type": "transport", "frame": 12345678, "state": "playing" }
{ "type": "meter", "channels": [{ "peakDb": -12.3 }] }
{ "type": "record", "state": "recording", "diskFreeBytes": 9000000000 }
```

Client → server:

```json
{ "type": "seek", "frame": 0 }
{ "type": "subscribe", "meters": true }
```

## Auth (milestone 4+)

Optional `Authorization: Bearer <token>` set in app preferences. Default: LAN-trusted (document risk).

## Shared backend

`ControlService` interface implemented once; injected into ViewModels and Ktor routes.
