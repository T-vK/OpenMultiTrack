# Module: `remote-server`

**Type:** Android library  
**Package:** `org.openmultitrack.remote`

LAN **Host/Remote sync** between two OpenMultiTrack Android instances. This is **not** a browser-hosted web UI.

## Responsibilities

- HTTP health + WebSocket server (`RemoteHostServer` — NanoHTTPD + NanoWSD)
- WebSocket client (`RemoteClient` — OkHttp)
- UDP discovery (`RemoteDiscovery` — ports from `RemoteProtocol`)
- JSON snapshot/delta/command codec (`RemoteJsonCodec`)
- Remote waveform downsampling (`RemoteWaveformUtil`)
- DTOs (`RemoteModels`)

## Protocol summary

| Constant | Value |
|----------|-------|
| HTTP/WS port | 8765 |
| Discovery UDP | 8766 |
| WebSocket path | `/api/v1/ws` |
| Delta interval | 50 ms |
| Protocol version | 1 (`RemoteProtocol.VERSION`) |

Full specification: [../remote-control.md](../remote-control.md).

## App integration

`app/remote/`:

- `RemoteControlManager` — role selection, connection lifecycle
- `RemoteCommandExecutor` — maps commands to `MixerSessionController` / `MainViewModel`
- `RemoteSnapshotMapper` — builds mirror state from `DawUiState`

## Security model

- LAN-trusted cleartext by default (`network_security_config.xml` for private IPs)
- Optional bearer token: Host rejects WebSocket without `Authorization` when `remoteAuthToken` set in settings

## Dependencies

- NanoHTTPD 2.3.1 (+ websocket jar)
- OkHttp 4.12.0
- `org.json` for parsing (module-local)

**Note:** Older docs referenced Ktor for a browser API — that design was superseded. See [../control-api.md](../control-api.md).

## Tests

`RemoteJsonCodecTest`, `RemoteWaveformUtilTest`

E2E: `app/.../e2e/RemoteE2eHostTest`, `RemoteE2eClientTest` via `scripts/run-dual-device-e2e-tests.sh`

## Extension guidelines

- Bump `RemoteProtocol.VERSION` for breaking wire changes; support one previous version if needed.
- Keep commands thin — execute on Host service thread, broadcast resulting state.
- Large data (waveforms) uses on-demand `waveform_request` / `waveform_chunk`, not every delta.

## Related

- [../remote-control.md](../remote-control.md)
- [../modules/app.md](app.md)
