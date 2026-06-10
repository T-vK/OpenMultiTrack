# Threading and buffering

OpenMultiTrack separates **real-time audio threads** from **I/O and UI threads**. Violating this boundary (allocation, locks, or blocking I/O on the Oboe callback) causes glitches and XRUNs.

## Thread roles

| Thread / executor | Work | Must not |
|-------------------|------|----------|
| **Oboe / UAC2 callback** (high priority) | Copy samples in/out of lock-free SPSC rings; meter taps | Allocate, lock, disk I/O, JNI heavy work |
| **Disk writer** | Drain record rings; `PerChannelWavWriter` flush | Block audio callback |
| **Disk reader** | Fill playback rings; handle seek | Block audio callback |
| **Service / session** (`AudioSessionService`) | Lifecycle, mixer session coordination | Long blocking work without coroutines |
| **Main / Compose** | UI; collects `StateFlow` from ViewModels | Audio processing |
| **OSC / network** | UDP send/receive; WebSocket host/client | Audio threads |
| **Remote delta loop** | ~50 ms JSON diffs to connected remotes | Large binary transfers (use on-demand waveform chunks) |

## Ring buffers

Native code uses **single-producer single-consumer (SPSC)** ring buffers (`spsc_ring_buffer.h`):

- Producer: Oboe input callback (record) or disk reader thread (playback)
- Consumer: disk writer thread (record) or Oboe output callback (playback)

**Sizing heuristic:** capacity of at least several times the USB burst size, often on the order of ~100 ms of audio at the session sample rate. Tune on target hardware if overruns appear.

## Backpressure

| Condition | Behavior |
|-----------|----------|
| Record ring full | Drop frames; increment native drop counter (surfaced selectively to UI) |
| Playback ring underrun | Output silence or hold last frame; underrun counter |
| Disk slow | Writer falls behind → drops or eventual record stop (disk space monitoring planned) |

## Shared USB capture

`NativeAudioCaptureRegistry` ensures only one logical owner drives the USB capture stream at a time. When switching active mixer or handoff between monitor and record, coordination happens on the service thread before native start/stop.

## Kotlin coroutines

`MainViewModel` and `MixerSessionController` use coroutines for:

- USB probe and permission flows
- OSC discovery and scribble import
- Remote sync loops
- Waveform extraction (off main thread)

Audio start/stop and seek commands are dispatched to the service/native layer; results propagate via `StateFlow` / callbacks.

## UI refresh rates

| Data | Typical rate |
|------|----------------|
| VU meters (local) | Driven by capture tap, throttled for Compose |
| Remote delta | `RemoteProtocol.DELTA_INTERVAL_MS` (50 ms) |
| Live waveform tail | Bundled in delta when changed |
| Transport position (soundcheck) | Included in delta and local state |

## Related

- [data-flows.md](data-flows.md) — where data moves between layers
- [../modules/audio-engine.md](../modules/audio-engine.md) — native entry points
- [../technical-risks.md](../technical-risks.md) — latency and XRUN risks
