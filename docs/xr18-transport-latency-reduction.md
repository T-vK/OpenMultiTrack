# XR18 transport latency reduction

Proposals to reduce time from **button press** to **active transport** to roughly **200 ms** for Record and Play.

Based on the [hardware timing report](xr18-transport-timing-report.md) (2026-06-11, wireless tablet + XR18).

## Target vs today

| Action | Today (hot path) | Target | Already fast enough |
|--------|------------------|--------|---------------------|
| **Record** | ~2–8 s (routing on press; was worse with quiesce) | ~200 ms | USB capture ~127 ms, WAV open ~64 ms |
| **Play** | ~3.4 s | ~200 ms | `playSession` ~82 ms |

**Conclusion:** 200 ms is realistic when **routing is pre-applied** and **USB is not torn down** before OSC. Record/Play should become “start transport only”; mixer prep moves to arm / session select.

---

## Policy: no USB quiesce before OSC

**Removed:** `quiesceUsbBeforeRoutingLocked()` stopped monitor, VU, capture, and playback before every OSC apply. Field timing attributed **~5 s** to this step when monitor was active — that was **teardown cost**, not an OSC requirement. X-Air desks accept routing commands while USB audio streams.

**Keep:** Flow 8 `prepareFlow8UsbForPlaybackLocked` (release capture interface before playback claim) — that is UAC2 interface contention on Flow 8 hardware, unrelated to OSC.

**Do not reintroduce** “release USB for mixer routing” on the transport button path.

---

## Design principle: prepare early, start fast

```
Old (removed):  [Press] → quiesce USB → OSC read/apply → USB reopen → start
Today:          [Press] → OSC read/apply (while USB may stream) → start
Target:         [Arm / select session] → OSC + USB prep (background)
                [Press] → optional spot-check → start transport (~100 ms)
```

User-visible contract:

- **Arm channels** → “Preparing mixer…” (routing applied here)
- **Select soundcheck session** → “Preparing playback…” (routing + USB warmup here)
- **Record / Play** → instant when prep succeeded; show error if not ready

Cold path (mixer unreachable, first arm) may still take 1–3 s — but that work happens **before** the user hits Record/Play.

---

## Record (~200 ms)

### 1. Pre-apply routing on arm changes (highest impact)

**When:** Strip armed/unarmed (debounced ~300 ms), routing automation enabled, mixer reachable.

**What:** Run capture + apply for record targets in background; set `routingReadyForRecord = true` when verified.

**On Record press:** If `routingReadyForRecord` and same armed set → **skip** `routingBeforeRecordLocked()` entirely.

**Saves:** ~2.6 s (full OSC session on press).

### 2. Keep USB capture hot in record mode

Avoid `ensureCapture` + USB open on every Record if capture stayed active between sessions in `MULTITRACK_RECORD`.

**Saves:** ~100–150 ms on warm path.

### 3. Cancel stray OSC on the transport path

The timing report showed **82-path OSC queries** concurrent with an old “quiesce” window — likely monitor teardown + duplicate `readAll` / VU poll. **Find and cancel** duplicate reads on the transport path (not by stopping USB).

### 4. Narrow reads on any remaining hot-path OSC

Never `readAllChannelInputs` (48+ paths) on button press. Use cached snapshot, scoped read for armed channels only, or trust `routingReady`.

### 5. Optional: “Fast routing” setting

Expert toggle: fire writes without blocking verify on arm. Default remains verify-before-proceed.

---

## Play (~200 ms)

### 1. Fix soundcheck pre-USB full apply — **done**

Implemented: `beforeSoundcheckApply` uses `captureOverrideOnly` (no OSC); `afterSoundcheckPlaybackStarted` calls `reapplyOverrideOnly` after USB playback opens.

### 2. Pre-apply soundcheck routing when session loads — **partial**

**When:** `selectSoundcheckSession` + `warmPlaybackRoute`.

**On Play press:** If ready and USB route open → skip routing hooks; call `player.playSession` only.

**Saves:** remaining ~300 ms routing on press; total Play **~80–120 ms**.

### 3. Skip redundant reapply

If routing was applied during session warmup and `pending.override` still matches live, do not call `reapplyOverrideOnly` on every Play.

### 4. Defer waveform extraction

Keep async after session load — off the Play path.

---

## Implementation order

| Priority | Item | Effort | Impact (est.) |
|----------|------|--------|----------------|
| P0 | **Removed USB quiesce before OSC** | Done | Record −0–5 s |
| P0 | Fix soundcheck capture-only before USB | **Done** | Play −2.8 s |
| P0 | Apply soundcheck routing in `warmPlaybackRoute` / session select | Partial | USB warmup only; OSC still on Play press |
| P1 | Pre-apply record routing on arm (debounced) | Medium | Record −2.6 s |
| P1 | Cancel duplicate 82-path OSC on transport path | Small–medium | Record −0–2.6 s |
| P2 | Keep capture hot between record sessions | Medium | Record −0.1 s |
| P2 | `routingReady` flags + optional spot-check on press | Small | Safety without full read |
| P3 | Fast routing mode (no blocking verify) | Medium | Arm −1–2 s; user choice |

---

## What “200 ms” requires (checklist)

- [ ] Routing applied **before** Record/Play (arm or session select)
- [ ] USB stream already open (capture or playback route warmed)
- [x] **No** USB quiesce / monitor teardown before OSC on press
- [x] No duplicate soundcheck **OSC** apply before USB (capture-only + post-playback reapply)
- [ ] At most a **tiny** verify (0–3 OSC paths) on press, or trust `routingReady`

---

## Risks and trade-offs

| Change | Risk | Mitigation |
|--------|------|------------|
| Pre-apply on arm | User arms then changes routing on mixer | Re-verify on arm change; spot-check on Record |
| Skip verify on press | Silent routing drift | `routingReady` TTL; full verify on arm |
| Apply while USB streaming | Theoretical desk firmware edge cases | Hardware e2e; no quiesce unless a specific desk proves otherwise |
| Concurrent OSC + monitor | Duplicate reads slow transport | Cancel VU/routing poll during apply; do not stop USB |

---

## Success metrics

Re-run `Xr18RoutingAppE2eTest` (or manual logcat) and expect:

| Trace | Target `FINISH` |
|-------|-----------------|
| `RECORD-START` | < 250 ms (warm: capture active, routing pre-applied) |
| `SOUNDCHECK-PLAY` | < 250 ms (session pre-loaded, routing pre-applied, USB open) |

Log filter:

```bash
adb logcat -s OpenMultiTrack/TransportTrace:I OpenMultiTrack/Xr18Routing:I
```

---

## Related docs

- [XR18 transport timing report](xr18-transport-timing-report.md) — measured breakdown (quiesce line is historical)
- [XR18 routing automation](xr18-routing-automation.md) — OSC paths and hook points
- [Mixer connectivity plan](mixer-connectivity-and-session-ux-plan.md) — health UI and obsolete quiesce policy
