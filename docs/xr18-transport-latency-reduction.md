# XR18 transport latency reduction

Proposals to reduce time from **button press** to **active transport** to roughly **200 ms** for Record and Play.

Based on the [hardware timing report](xr18-transport-timing-report.md) (2026-06-11, wireless tablet + XR18).

## Target vs today

| Action | Today (hot path) | Target | Already fast enough |
|--------|------------------|--------|---------------------|
| **Record** | ~7.9 s | ~200 ms | USB capture ~127 ms, WAV open ~64 ms |
| **Play** | ~3.4 s | ~200 ms | `playSession` ~82 ms |

**Conclusion:** 200 ms is realistic only if **routing and USB teardown are not on the button-press path**. Record/Play must become “start transport only”; mixer prep moves earlier.

---

## Design principle: prepare early, start fast

```
Today:     [Press] → quiesce → OSC read/apply/verify → USB → start
Target:    [Arm / select session] → OSC + USB prep (background)
           [Press] → optional spot-check → start transport (~100 ms)
```

User-visible contract:

- **Arm channels** → “Preparing mixer…” (routing applied here)
- **Select soundcheck session** → “Preparing playback…” (routing + USB warmup here)
- **Record / Play** → instant when prep succeeded; show error if not ready

Cold path (mixer unreachable, first arm, routing never applied) may still take 1–3 s — but that work happens **before** the user hits Record/Play.

---

## Record (~200 ms)

### 1. Pre-apply routing on arm changes (highest impact)

**When:** Strip armed/unarmed (debounced ~300 ms), routing automation enabled, mixer reachable.

**What:** Run capture + apply for record targets in background; set `routingReadyForRecord = true` when verified.

**On Record press:**

- If `routingReadyForRecord` and same armed set → **skip** `routingBeforeRecordLocked()` entirely
- Optional: spot-check 1–3 paths for armed channels that were changed externally

**Saves:** ~2.6 s (full OSC session on press).

### 2. Skip `quiesceUsb` when safe

**When quiesce is required today:** USB playback active, monitor on, or capture active while not recording — XR18 rejects or fails OSC verify during streaming.

**On Record press, skip quiesce if:**

- Not playing soundcheck
- Monitor off (or routing already applied while monitoring — see risk below)
- Capture already active for this mixer

**Saves:** up to **~5 s** (monitor was on in the timing report).

**Risk:** Applying OSC while USB streaming is active may fail on hardware. Mitigation: only skip quiesce when routing is **already** at record targets (pre-applied on arm).

### 3. Keep USB capture hot in record mode

Avoid `ensureCapture` + USB open on every Record if capture stayed active between sessions in `MULTITRACK_RECORD`.

**Saves:** ~100–150 ms on warm path.

### 4. Investigate 82-path OSC during quiesce

The timing report showed two **~2.6 s queries of 82 paths** during `quiesceUsb`, separate from the 48-path routing read. Likely concurrent work (monitor teardown, duplicate `readAll`, VU/routing poll). **Find and cancel** on the transport path.

**Saves:** potentially seconds when monitor was active.

### 5. Narrow reads on any remaining hot-path OSC

Never `readAllChannelInputs` (48+ paths) on button press. Use:

- Cached live snapshot from last successful apply
- Scoped read: armed channels only (3 paths × N)
- Or zero reads if `routingReady` flag is fresh (< few seconds)

### 6. Optional: “Fast routing” setting

Expert toggle: **fire writes without blocking verify** on arm (~50–100 ms), verify async or on stop. Default remains verify-before-proceed.

---

## Play (~200 ms)

### 1. Fix soundcheck pre-USB full apply (quick win)

**Bug:** AUTO `peekApply` calls `applyInternal` immediately, so `beforeSoundcheckApply` returns `Applied` before USB opens. That caused **~2.8 s** apply on Play in the report, plus a **~0.3 s** no-op reapply after USB.

**Fix:**

- `peekApply` for soundcheck must **not** apply OSC — only return `ReadyToApply` or run capture-only
- Full apply runs **once** in `afterSoundcheckPlaybackStarted` after `ensurePlaybackLocked`

**Saves:** ~2.8 s on Play immediately (drops ~3.4 s → ~0.5 s without other work).

### 2. Pre-apply soundcheck routing when session loads

**When:** `selectSoundcheckSession` + `warmPlaybackRoute` (already run on AUTO_SOUNDCHECK after stop).

**What:**

1. Open USB playback route
2. Apply USB-return routing for session track channels
3. Set `routingReadyForSoundcheck = true` (session id + channel set)

**On Play press:**

- If ready and USB route still open → **skip** `routingBeforeSoundcheckLocked` and `afterSoundcheckPlaybackStarted`
- Call `player.playSession` only

**Saves:** remaining ~300 ms routing on press; total Play **~80–120 ms**.

### 3. Skip redundant reapply

If routing was applied during session warmup and `pending.override` still matches live, do not call `reapplyOverrideOnly` on every Play.

### 4. Defer waveform extraction

Already async after session load — keep it off the Play path (confirmed in report).

---

## Implementation order

| Priority | Item | Effort | Impact (est.) |
|----------|------|--------|----------------|
| P0 | Fix soundcheck `peekApply` / capture-only before USB | Small | Play −2.8 s |
| P0 | Apply soundcheck routing in `warmPlaybackRoute` / session select | Medium | Play −0.3 s; enables skip on press |
| P1 | Pre-apply record routing on arm (debounced) | Medium | Record −2.6 s |
| P1 | Skip quiesce when monitor off + routing ready | Small | Record −0–5 s |
| P1 | Track down 82-path OSC during quiesce | Small–medium | Record −0–5 s |
| P2 | Keep capture hot between record sessions | Medium | Record −0.1 s |
| P2 | `routingReady` flags + optional spot-check on press | Small | Safety without full read |
| P3 | Fast routing mode (no blocking verify) | Medium | Arm −1–2 s; user choice |

---

## What “200 ms” requires (checklist)

- [ ] Routing applied **before** Record/Play (arm or session select)
- [ ] USB stream already open (capture or playback route warmed)
- [ ] No monitor/playback quiesce on press
- [ ] At most a **tiny** verify (0–3 OSC paths) on press, or trust `routingReady`
- [ ] No `readAllChannelInputs` on press
- [ ] No duplicate soundcheck apply before and after USB

---

## Risks and trade-offs

| Change | Risk | Mitigation |
|--------|------|------------|
| Pre-apply on arm | User arms then changes routing on mixer | Re-verify on arm change; spot-check on Record; conflict detection on restore |
| Skip quiesce | OSC fails while USB streaming | Only skip when routing already confirmed; fall back to quiesce + apply on failure |
| Skip verify on press | Silent routing drift | `routingReady` TTL; full verify on arm; optional strict mode |
| Apply while monitoring | XR18 may ignore OSC | Test on hardware; apply after monitor stop if needed, but do it on arm not on Record |

---

## Success metrics

Re-run `Xr18RoutingAppE2eTest` (or manual logcat) and expect:

| Trace | Target `FINISH` |
|-------|-----------------|
| `RECORD-START` | < 250 ms (warm: capture active, routing pre-applied, monitor off) |
| `SOUNDCHECK-PLAY` | < 250 ms (session pre-loaded, routing pre-applied, USB open) |

Log filter:

```bash
adb logcat -s OpenMultiTrack/TransportTrace:I OpenMultiTrack/Xr18Routing:I
```

---

## Related docs

- [XR18 transport timing report](xr18-transport-timing-report.md) — measured breakdown
- [XR18 routing automation](xr18-routing-automation.md) — OSC paths and hook points
