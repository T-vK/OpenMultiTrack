# Module: `mixer-behringer`

**Type:** Pure Kotlin JVM library  
**Package:** `org.openmultitrack.mixer.behringer`

Behringer/Midas **OSC drivers** and **scribble strip** import helpers.

## Responsibilities

- Implement `Mixer` for X32 and XR18 (`X32Mixer`, `Xr18Mixer`)
- UDP OSC encode/send (`OscUdpClient`)
- OSC address constants and decode (`OscPath`, `OscMessageDecoder`)
- XR18/X-Air scribble import (`Xr18ScribbleImporter`, `ScribbleStripLabel`)
- Flow 8 USB/BLE state → labels/icons (`Flow8StateDecoder`, `Flow8UsbScribbleMapper`, `MixingStationIcons`)

## OSC ports

| Model | UDP port |
|-------|----------|
| X32 | 10023 |
| XR18 / X-Air | 10024 |

## Implementation status

| Feature | Status |
|---------|--------|
| `connect()` sends `/info` | Implemented |
| `OscUdpClient` encode | Implemented |
| `applySnapshot` / `captureSnapshot` | **Stub** — throws or no-op |
| `feedback()` parser | Partial |
| Scribble import | Implemented for XR18 and Flow 8 paths |

Routing command sequences are **designed** in [../mixer-drivers.md](../mixer-drivers.md) — validate on hardware before enabling snapshots.

## Extension guidelines

- Add OSC addresses to `OscPath`; unit-test encoding.
- New console family: new `Mixer` implementation in this module, not in `app`.
- No proprietary OSC libraries — keep encoder/decoder in-module (FOSS).

## Tests

`OscUdpClientTest`, `OscMessageDecoderTest`, `Xr18ScribbleImporterTest`, `Flow8StateDecoderTest`, `Flow8UsbScribbleMapperTest`, and others.

## Research docs

- [../flow8-reverse-engineering/README.md](../flow8-reverse-engineering/README.md)
- [../xr18-scribble-strip/README.md](../xr18-scribble-strip/README.md)

## Related

- [../mixer-drivers.md](../mixer-drivers.md)
- [../hardware-assumptions.md](../hardware-assumptions.md)
