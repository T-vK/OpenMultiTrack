# Session on-disk format

Recording sessions are directories of **per-channel WAV files** plus a **JSON metadata** file. Implementation lives in `session-io` (`SessionDirectory`, `SessionMetadata`, `PerChannelWavWriter`).

## Directory layout

```
{storageRoot}/
  {MixerFolderName}/           # e.g. "XR18-A1B2C3"
    {yyyy-MM-dd-HH-mm-ss}/     # session timestamp (local time)
      session.json
      channel01.wav
      channel02 - Vocals.wav     # optional display name in filename
      channel03.wav
      .waveforms/                # optional peak cache
        channel01/
          level0.peaks           # overview
          level1.peaks           # zoom levels
      cues.json                  # optional cue points
      trackmarks.json            # optional in/out markers
```

`MixerFolderName` is derived from mixer profile (display name + serial/id).  
`storageRoot` is resolved by `RecordingStorageResolver` (default: app external files dir).

## session.json

Written at session start; updated on stop. Key fields:

| Field | Meaning |
|-------|---------|
| `mixerId` | Stable mixer profile id |
| `mixerFolderName` | Parent folder name |
| `customTitle` | Optional user title |
| `sampleRate` | Hz (typically 48000) |
| `format` | `SessionFormat` enum name (per-channel PCM) |
| `startedAtEpochMs` | Session start time |
| `incomplete` | `true` until recording finalized cleanly |
| `timelineFramesWritten` | Logical timeline length including silence gaps |
| `channels` | Array of `{ index, fileName, displayName, colorArgb }` |

**Incomplete sessions** can be resumed after USB dropout or crash recovery (see interrupted recording E2E tests).

Parsing is intentionally simple (regex-based) — not a full JSON library dependency in `session-io`.

## WAV files

| Property | Value |
|----------|-------|
| Format | PCM |
| Bit depth | 24-bit |
| Channels per file | 1 (mono per logical input) |
| Sample rate | From USB probe / session metadata |

Legacy/interleaved multichannel WAV (`WavWriter` / `WavReader`) remains in the module for tests and migration paths but **new recordings use per-channel files**.

## Waveform cache (`.waveforms/`)

Generated asynchronously after or during recording:

| Level | Approx. resolution | Use |
|-------|-------------------|-----|
| 0 | ~10 s per peak | Full-file overview |
| 1 | ~1 s per peak | Default soundcheck window |
| 2 | ~100 ms per peak | Zoomed timeline |
| 3 | ~10 ms per peak | Live record tail (short window) |

`SessionWaveformExtractor` builds peaks; `SessionWaveformCache` reads them for UI and remote `waveform_chunk` responses.

## Cues and trackmarks

| File | Purpose |
|------|---------|
| `cues.json` | Named cue points on timeline |
| `trackmarks.json` | In/out markers for loop regions |

Managed by `SessionCueFile` and related types in `session-io`.

## Session library

`SessionLibrary` scans mixer folders and lists sessions for the soundcheck picker — sorted by timestamp, filtering incomplete if configured.

## Future format notes

Not yet implemented in production paths:

- RF64 / W64 for >4 GB single files
- FLAC export
- BWF `codingHistory` broadcast metadata

See [roadmap.md](roadmap.md) and [../PROJECT_STATUS.md](../PROJECT_STATUS.md).

## Related

- [../architecture/data-flows.md](../architecture/data-flows.md) — write path
- [../modules/session-io.md](../modules/session-io.md) — API types
