# XR18 Routing Automation — Implementation Plan

## Goal

Automatically switch XR18 channel input routing over OSC when **recording starts/stops** or **soundcheck playback starts/stops** — not when merely selecting record or soundcheck **mode** in the UI.

Flow 8 and mixers without LAN OSC are skipped; USB capture/playback continues unaffected.

## XR18 OSC Model (X-Air family)

Per channel `01`–`16` (verified against [Behringer World X-Air OSC wiki](https://behringer.world/wiki/doku.php?id=x-air_osc)):

| Path | Type | Values | Meaning |
|------|------|--------|---------|
| `/ch/{nn}/config/insrc` | int | 0=OFF, 1–16=IN01–IN16 | Physical input source |
| `/ch/{nn}/config/rtnsrc` | int | 0–17=U01–U18 | USB return source when USB path active |
| `/ch/{nn}/preamp/rtnsw` | int | 0=OFF, 1=ON | Preamp (A/D) vs USB return switch |

**Record override** (armed channels only):

- `preamp/rtnsw` → `0` (preamp / A-D)
- `config/insrc` → channel number (`ch03` → `3` = IN03)

**Soundcheck override** (channels with a session track only):

- `preamp/rtnsw` → `1` (USB return)
- `config/rtnsrc` → `channelIndex` (`ch03` → `2` = U03)

**Snapshot recall** (advanced):

- `/-snap/load ,i {slot}` where slot is 1–64

USB matrix `/routing/usb/NN/src` is **not** modified by default automation (read-only for the input-sources viewer).

## Safety Principles

1. **Never block audio** — OSC failures are logged and skipped; recording/playback proceeds.
2. **Persist before write** — baseline captured and written to disk before any mixer change.
3. **Scoped channels** — record affects **armed** strips only; soundcheck affects **channels with WAV data** in the loaded session only.
4. **Restore is explicit** — on record stop or soundcheck stop/pause/end; not on mode switch alone.
5. **Crash recovery** — `PendingRoutingRestore` survives app death; prompt on next launch after recording fully stopped (not during crash-resumed recording).
6. **Conflict detection** — if engineer changes routing mid-override, restore respects policy (`STRICT`, `RESPECT_LIVE`, `ASK`).

## Automation Levels (per mixer, stored in `AppSettingsStore`)

| Level | Behavior |
|-------|----------|
| `OFF` | No OSC routing changes |
| `PROMPT` | Confirm before apply and before restore (default) |
| `AUTO` | Apply/restore silently per policy |

## Methods

| Method | Apply | Restore |
|--------|-------|---------|
| `PER_CHANNEL` | Set `insrc` / `rtnsw` / `rtnsrc` per scoped channel | Write saved baseline values |
| `SNAPSHOT_SLOT` | `/-snap/load` record/soundcheck slot | Restore per-channel baseline captured **before** snapshot load |

App-captured `MixerSnapshot` command lists are deferred to a future version.

## Trigger Points

| Event | Action |
|-------|--------|
| `startRecording()` | After capture engine ready; before UI shows REC — apply record routing |
| `stopRecording()` | After capture stopped — restore (prompt if needed) |
| `startSoundcheckPlaybackLocked()` | Before native playback — apply soundcheck routing |
| `finishPlaybackTransport()` / pause | After transport stops — restore soundcheck routing |
| App cold start | If `PendingRoutingRestore` and not crash-resumed recording — prompt restore |
| Mode switch only | **No routing change** |

## Architecture

```
MainViewModel (prompts, settings, menu)
    ↓
RoutingOverrideCoordinator (app) — prompt gating, per-mixer settings
    ↓
Xr18RoutingService (mixer-behringer) — OSC read/write, snapshot load
    ↓
OscUdpClient
```

`RoutingBaselineStore` (DataStore) holds `PendingRoutingRestore` JSON.

`MixerRoutingPort` interface enables unit tests without UDP.

## UI

1. **Settings → Mixer routing automation** — level, method, restore policy, snapshot slots, “ignore conflicts” expert toggle.
2. **Prompt dialogs** — before apply (record/play) and before restore (stop).
3. **Burger menu → Input sources** — read-only table of current XR18 routing (requires `oscHost`).

## Tests

- `XAirInputSourceLabels` — index → label mapping
- `Xr18RoutingService` — command generation (record/soundcheck/restore) with fake port
- `RoutingOverrideCoordinator` — transaction lifecycle, crash pending, conflict policy
- `RoutingBaselineStore` — round-trip persistence
- `OscPath` — new path helpers

## Out of Scope (v1)

- Flow 8 / BLE routing
- App-captured routing presets (`MixerSnapshot` capture UI)
- Live `/xremote` subscription (poll on demand only)
- Modifying `/routing/usb/NN/src`

## Implementation Order

1. OSC path helpers + `Xr18RoutingService` read/write
2. `RoutingBaselineStore` + coordinator logic + tests
3. Hook `MixerSessionController` record/play transport
4. Settings + prompts + input sources screen
5. Manual hardware validation checklist (not automated in CI)
