# Data flows

How audio, disk, control, and remote sync move through the system. For threading constraints on these paths, see [threading.md](threading.md).

## Record path

```
Mixer ADC
  → USB UAC2 input (mixer is clock master)
  → AudioEngineRouter selects Oboe or UAC2 capture backend
  → Native input callback (real-time thread)
  → SPSC ring buffer
  → Kotlin writer thread(s)
  → PerChannelWavWriter (one file per armed channel)
  → session.json metadata + optional waveform peak cache
```

**Clock:** USB sync — the app does not resample on record. Frame counts on the logical timeline are authoritative (`timelineFramesWritten` in metadata).

**Channel selection:** Only **armed** channels are written. Unarmed channels may still contribute to VU meters and live waveform rings.

**USB dropout:** On detach, the service writes silence at the native sample rate to preserve timeline alignment; debounced reattach resumes append to the same session files. See [../product/roadmap.md](../product/roadmap.md#usb-dropout-behavior).

## Monitor path

```
USB capture (shared CaptureSessionEngine)
  → per-channel VU tap + LiveWaveformRing
  → MonitorMixer (solo/mute/gain per strip)
  → Native monitor or playback stream → USB outputs / built-in audio
```

Monitor can be hot-routed: solo/arm changes without restarting the capture stream when possible. Exclusive capture ownership is coordinated via `NativeAudioCaptureRegistry` when multiple mixer profiles share one USB device.

## Playback / soundcheck path

```
Session library (per-mixer folders)
  → PerChannelWavReader(s) for selected session
  → reader thread(s) prefetch into ring buffers
  → Native output callback
  → USB playback → mixer USB returns
```

**Modes:**

| `AppMode` | Behavior |
|-----------|----------|
| `VIRTUAL_SOUNDCHECK` | Per-channel playback to matching USB outputs |
| `SIMPLE_PLAY` | Stereo mix-down of unmuted channels to USB outputs 1+2 |

**Seek (target behavior):** Transport requests a frame position; engine flushes output, readers seek WAV byte offsets, prefetch ring buffers, then resume. Scrubbing coalesces rapid seek targets. Full seek polish is ongoing — see [../PROJECT_STATUS.md](../PROJECT_STATUS.md).

**Loop regions:** User-defined in/out markers on the soundcheck timeline; playback repeats the section until disabled.

## Waveform data path

Waveforms are **peak caches**, not decoded PCM in the UI thread.

| Context | Source | Resolution |
|---------|--------|------------|
| Live record | `LiveWaveformRing` in `app` | ~30 peaks/sec tail window |
| Soundcheck overview | `SessionWaveformCache` in `session-io` | Multi-level peaks on disk under `.waveforms/` |
| Remote mirror | Quantized tail in delta frames; on-demand `waveform_chunk` | See [../remote-control.md](../remote-control.md) |

## OSC / mixer control path

```
UI action (future: snapshot recall)
  → Mixer.applySnapshot() / sendOsc()
  → OscUdpClient (UDP)
  → mixer firmware
  ← OSC feedback (partially implemented)
```

**Scribble strip (read-only):**

| Source | Mechanism |
|--------|-----------|
| XR18 / X-Air | OSC LAN discovery + `Xr18ScribbleImporter` |
| Flow 8 | BLE GATT or USB state decode → `Flow8BleScribbleImporter` |

Labels are cached in `ScribbleStripCache`; never written back to the mixer.

## LAN remote sync path

```
Remote device                    Host device (at mixer)
     │                                │
     ├── UDP OMT_DISCOVER ───────────►│
     │◄── OMT_ANNOUNCE ───────────────┤
     ├── WebSocket connect ──────────►│ RemoteHostServer
     │◄── snapshot (full state) ─────┤
     │◄══ delta (~20 Hz) ════════════►│
     ├── command (arm, seek, …) ─────►│ RemoteCommandExecutor
     │◄── ack / error ─────────────────┤
```

The Host owns USB, files, and the audio engine. The Remote never opens a competing USB capture stream.

Protocol constants: `domain/remote/RemoteProtocol.kt`  
Full spec: [../remote-control.md](../remote-control.md)

## Session directory flow

```
User starts record
  → SessionDirectory.createSessionDir(storageRoot, mixerFolderName)
  → ChannelFileNaming assigns per-channel WAV filenames
  → SessionMetadata.writeTo() → session.json (incomplete=true)
  → … recording …
  → stop → markComplete(), waveform extraction optional
```

Layout: [../product/session-format.md](../product/session-format.md)
