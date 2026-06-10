# Module: `domain`

**Type:** Pure Kotlin JVM library  
**Package:** `org.openmultitrack.domain`

Shared types and interfaces with **no Android dependencies**. Used by `app`, `session-io`, `mixer-behringer`, `remote-server`, and tests.

## Responsibilities

- Session and transport enums/models
- Mixer abstraction (`Mixer` interface)
- Channel strip state
- Remote protocol constants (`RemoteProtocol`)
- Audio layout helpers (`RecordingChannels`, `AudioConstants`)

## Key types

| Package | Types |
|---------|-------|
| `audio` | `AudioConstants` (max 64 channels, default 48 kHz), `RecordingChannels`, probe descriptors |
| `session` | `AppMode`, `RecordingSession`, `TransportState`, `SessionFormat` |
| `mixer` | `Mixer`, `MixerProfile`, `MixerSnapshot`, `MixerRoutingConfig`, `OscArg` |
| `channel` | `ChannelStripState`, `ChannelColors` |
| `remote` | `RemoteRole`, `RemoteProtocol` (ports, paths, delta interval) |

## `Mixer` interface

Console-agnostic control plane:

- `connect` / `disconnect`
- `feedback(): Flow<MixerFeedback>`
- `applySnapshot` / `captureSnapshot` (implemented in drivers; still stubbed for routing)
- `sendOsc` for low-level access

Drivers live in `mixer-behringer`; persistence of snapshots is app-layer (future).

## Extension guidelines

- Add fields here when both UI and remote sync need the same shape.
- Keep JSON-serializable remote models aligned with `RemoteJsonCodec` in `remote-server`.
- No coroutine Android dispatchers — plain Kotlin + `Flow` only.

## Tests

`domain/src/test/` — `RecordingChannelsTest`, `OscPathTest`, semver script tests.

## Related

- [../mixer-drivers.md](../mixer-drivers.md)
- [../remote-control.md](../remote-control.md)
