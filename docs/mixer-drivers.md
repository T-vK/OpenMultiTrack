# Mixer Driver Design

## `Mixer` abstraction (`domain` module)

```kotlin
interface Mixer {
    val model: MixerModel
    val id: String // stable per connection, e.g. serial or IP+model

    suspend fun connect(config: MixerConnectionConfig): Result<Unit>
    suspend fun disconnect()

    /** Subscribe to mixer-initiated OSC (meters, acks). */
    fun feedback(): Flow<MixerFeedback>

    /** Apply a stored routing snapshot (record or soundcheck). */
    suspend fun applySnapshot(snapshot: MixerSnapshot): Result<SnapshotApplyResult>

    /** Capture current USB-relevant routing into a snapshot. */
    suspend fun captureSnapshot(name: String, mode: SnapshotMode): Result<MixerSnapshot>

    /** Low-level OSC for advanced users / tests. */
    suspend fun sendOsc(path: String, args: List<OscArg>): Result<Unit>
}

enum class SnapshotMode { RECORD, SOUNDCHECK }

data class MixerSnapshot(
    val id: String,
    val name: String,
    val mode: SnapshotMode,
    val commands: List<OscCommand>, // ordered apply list
    val verifyPaths: List<String>,  // feedback addresses to confirm
)
```

Drivers are **stateless translators**; persistence lives in `SnapshotRepository` (Room/DataStore, milestone 4).

## OSC transport

- UDP client, single socket per mixer IP.
- Client port: ephemeral; server: **10023 (X32)**, **10024 (XR18)**.
- Timeouts: 500 ms command, 2 s snapshot batch.
- No proprietary libraries — minimal OSC encoder/decoder (Kotlin, Apache-2.0).

## Routing model (conceptual)

### Record mode

For each channel `i` in `1..N`:

- Mixer **channel input source** = physical input or last-selected source (unchanged).
- **USB send / tap** for channel `i` carries post-preamp signal to USB **output** bus `i`.
- USB **returns** from host are muted or not routed to channel strips.

### Soundcheck mode

For each channel `i`:

- Mixer **channel input source** = **USB return `i`** (playback from host).
- USB **sends** to host muted or unused.
- Physical inputs not routed to strips (optional safety).

> **Important**: Exact OSC paths differ between X32 and XR18 firmware. Drivers store **captured** command lists from real hardware rather than shipping destructive defaults.

## X32 driver (`X32Mixer`)

| Function | OSC path (typical) | Args | Notes |
|----------|-------------------|------|-------|
| Ch input source | `/ch/{nn}/in/src` | int source index | nn = 01..32 |
| USB send tap | `/ch/{nn}/mix/01/on` etc. | 0/1 | depends on routing matrix |
| Snapshot store | `/snap/store` | int slot | user slot 1–80 |
| Snapshot load | `/snap/recall/{slot}` | | then verify |
| Scene name | `/snap/name {slot}` | string | metadata |
| Info / keepalive | `/info` | | connection check |
| Batch subscribe | `/xremote` | | renew every 9s |

**Source index mapping** (must be calibrated per firmware — placeholder indices):

| Index | Source (typical X32) |
|-------|----------------------|
| TBD | Local / analog input |
| TBD | USB return / card |

*Verification*: change source in X32 Edit, log OSC with Wireshark.

## XR18 driver (`Xr18Mixer`)

| Function | OSC path (typical) | Args | Notes |
|----------|-------------------|------|-------|
| Ch input | `/ch/{nn}/in/src` | int | nn = 01..18 |
| USB routing | `/routing/usb/*` | varies | XR18 often uses routing screen |
| Snapshot | `/snapshots/{n}` | | firmware-dependent |
| Info | `/info` | | |
| Remote expire | `/xremote` | | |

XR18 shares X32 family protocol but **port 10024** and fewer channels.

## Snapshot apply protocol

```
1. User selects snapshot S (mode = RECORD | SOUNDCHECK)
2. Mixer.applySnapshot(S):
   a. Send /xremote (keep feedback alive)
   b. For cmd in S.commands: sendOsc(cmd)
   c. Await feedback on S.verifyPaths (or timeout)
3. Return Applied | Partial | Failed(list)
```

## Factory driver registration

```kotlin
object MixerDriverRegistry {
    fun detect(model: MixerModel, host: String): Mixer = when (model) {
        MixerModel.X32 -> X32Mixer(host)
        MixerModel.XR18 -> Xr18Mixer(host)
    }
}
```

Auto-detect (milestone 4): query `/info` string for `X32` / `XR18`.

## Test strategy

- Unit: OSC path formatting, argument encoding, snapshot serialization.
- Instrumented: mock UDP server echoes expected acks.
- Hardware: manual checklist in `hardware-assumptions.md`.
