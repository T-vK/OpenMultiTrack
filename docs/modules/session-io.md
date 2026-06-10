# Module: `session-io`

**Type:** Pure Kotlin JVM library  
**Package:** `org.openmultitrack.sessionio`

On-disk session layout, WAV I/O, waveform peaks, cues/trackmarks.

## Responsibilities

- Create session directories (`SessionDirectory`)
- Read/write `session.json` (`SessionMetadata`)
- Per-channel 24-bit WAV (`PerChannelWavWriter`, `PerChannelWavReader`)
- Legacy interleaved WAV (`WavWriter`, `WavReader`) for tests/compatibility
- Waveform extraction and multi-level cache (`SessionWaveformExtractor`, `SessionWaveformCache`)
- Session library scanning (`SessionLibrary`)
- Cues and trackmarks (`SessionCueFile`, `SessionTrackmark`)
- Channel file naming (`ChannelFileNaming`)

## Session layout

Documented in [../product/session-format.md](../product/session-format.md).

## Threading

All disk I/O runs on **background threads** invoked from `app` (`SessionRecorder`, waveform jobs). Never call writers from Oboe callbacks.

## Timeline model

`timelineFramesWritten` in metadata includes **silence gaps** inserted during USB dropout so soundcheck timeline stays aligned with wall-clock performance length.

## Extension guidelines

- New formats (FLAC, RF64): add parallel writer/reader types; keep `SessionFormat` enum in `domain` updated.
- JSON parsing stays lightweight — avoid pulling heavy JSON libs if possible.
- Waveform levels: keep compatible with remote `waveform_chunk` downsampling.

## Tests

Strong coverage: `WavRoundTripTest`, `PerChannelWavReaderTest`, `SessionWaveformExtractorTest`, `SessionCueFileTest`, `ChannelFileNamingTest`, and others.

## Related

- [../product/session-format.md](../product/session-format.md)
- [../architecture/data-flows.md](../architecture/data-flows.md)
