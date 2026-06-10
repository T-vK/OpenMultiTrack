# Product overview

OpenMultiTrack targets **live bands and sound engineers** using Behringer/Midas digital mixers with Android tablets. It records multichannel performances and supports **virtual soundcheck** — rehearsing with yesterday's show through the real mixer USB returns.

## Design principles

| Principle | Implementation |
|-----------|----------------|
| **FOSS / F-Droid friendly** | No Google proprietary stack; GPLv3 app |
| **Reliability over features** | USB dropout → silence gaps, not crash; resume to same session |
| **Tablet-first DAW** | Vertical channel strips, touch transport, waveform overview |
| **Mixer stays authoritative** | Audio on USB; OSC for routing (when implemented); scribble read-only |
| **Offline capable** | No cloud; LAN remote optional |

## Supported hardware (target)

| Device | Audio | Control / labels | Status |
|--------|-------|------------------|--------|
| **XR18** | USB UAC2 | OSC :10024, scribble via LAN | Primary test target |
| **X32** | USB UAC2 | OSC :10023 | Supported in drivers |
| **Flow 8** | USB UAC2 | BLE/USB scribble, limited channels | Active development |
| **Other UAC2 mixers** | Generic probe | No OSC driver | Best-effort |

Channel counts and routing must be validated on real hardware — [../hardware-assumptions.md](../hardware-assumptions.md).

## App modes

Controlled by `AppMode` in `domain`:

| Mode | User label | Purpose |
|------|------------|---------|
| `MULTITRACK_RECORD` | Recording Mode | Arm channels, record per-channel WAV, monitor |
| `VIRTUAL_SOUNDCHECK` | Virtual Soundcheck | Browse sessions, seek, loop, per-channel playback to USB |
| `SIMPLE_PLAY` | Simple Play Mode | Stereo sum of unmuted channels to USB outputs 1+2 |

Mode switch is in the DAW top bar. Remote devices can request mode changes when paired as Remote.

## Multi-mixer workflow

Users maintain a **curated mixer list** (`MixerDeviceStore`):

1. Add mixer (USB probe or manual entry).
2. Select **active mixer** — drives USB routing and visible strips.
3. Each mixer has its own session folder namespace and strip state.
4. Only one Host USB capture stream; registry prevents conflicting opens.

## Remote control product model

Not a web browser UI — a **second Android device** running OpenMultiTrack in **Remote** role:

- **Host** tablet at the mixer: USB, recording, files.
- **Remote** tablet/phone: mirrored meters, transport, strip controls.

Pairing via Wi‑Fi LAN discovery or QR. See [../remote-control.md](../remote-control.md).

## Storage

Default root: app external files directory, user-overridable in settings (`RecordingStorageResolver`). Sessions grouped by mixer folder name (name + serial).

## Out of scope (current)

- Cloud sync or backup
- Non-destructive DAW editing (plugins, MIDI sequencing)
- Writing scribble labels back to mixer
- iOS or desktop clients
- Dante/AES67 network audio

## Related

- [ui-daw.md](ui-daw.md) — screen layout and interactions
- [session-format.md](session-format.md) — files on disk
- [roadmap.md](roadmap.md) — planned work
- [../PROJECT_STATUS.md](../PROJECT_STATUS.md) — implementation matrix
