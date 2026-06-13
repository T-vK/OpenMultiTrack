# Flow 8 recording known-good baseline

Use this reference when recording timeline, waveforms, and disk duration regress.

## Version pin

| Field | Value |
|-------|--------|
| Git commit | `064003b` |
| Describe | `v0.9.0-380-g064003b` |
| Release tag (nearby) | `v0.53.19` |

## What works at this baseline

- Recording UI timer tracks wall clock (±2.5s over ~12s on hardware tablet)
- Controller `recordElapsedSec` and on-disk session duration agree with wall clock
- Live waveforms visible during Flow 8 native PCM recording
- Soundcheck playback position tracks wall clock after record (E2E: `Flow8RecordingTimelineE2eTest`)

## Key commits (oldest → newest)

1. `b6cf8af` — anchor recording timeline when transport becomes active
2. `50c7898` — restore live waveforms during native PCM recording (mirror ingest + fanout meter tap)
3. `181dd8f` — move capture meter/waveform polling off main thread
4. `064003b` — keep log viewer updates off main thread

## E2E verification

```bash
./scripts/run-flow8-recording-timeline-e2e.sh --serial <tablet-serial>
```

## Restore checkout

```bash
git checkout 064003b
```

Or cherry-pick the commits above onto your branch if you only need the recording fixes.
