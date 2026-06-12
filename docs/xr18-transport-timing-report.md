# XR18 transport timing report

Hardware timing captured on **2026-06-11** from a full app-path e2e run on the wireless tablet with XR18 attached.

## Test setup

| Item | Value |
|------|--------|
| Device | `192.168.3.42:46003` (SM-P610 / gta4xlwifi, wireless adb) |
| Mixer | XR18 on LAN (`osc_host` configured in app) |
| Test | `Xr18RoutingAppE2eTest.recordAndPlay_routingHooksWithUsbCaptureActive` |
| Post-record behavior | `AUTO_SOUNDCHECK` |
| Armed routable channels | 18 |
| Recording duration in test | ~13 s (18 channels) |
| Test duration | 75.6 s (pass) |

### How to reproduce

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb -s 192.168.3.42:46003 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.3.42:46003 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s 192.168.3.42:46003 logcat -c
adb -s 192.168.3.42:46003 shell am instrument -w \
  -e class org.openmultitrack.app.e2e.Xr18RoutingAppE2eTest \
  org.openmultitrack.test/androidx.test.runner.AndroidJUnitRunner
adb -s 192.168.3.42:46003 logcat -d | grep OpenMultiTrack/TransportTrace
```

**Note:** Log tag is `OpenMultiTrack/TransportTrace` (not plain `TransportTrace`). OSC detail also appears as `osc …` lines folded into the active transport trace.

### Trace format

```
[RECORD-START] #17 +7380ms (Δ2067ms) osc read 16 ch 48 paths → 48 replies in 2063ms
```

| Field | Meaning |
|-------|---------|
| `#17` | Step number within this user action |
| `+7380ms` | Elapsed since button press (trace origin) |
| `Δ2067ms` | Time since previous step (**use this to find slow steps**) |
| Remainder | What happened |

Each flow ends with `FINISH +Nms …`.

---

## 1. Record → recording active

**Trace label:** `RECORD-START`  
**Total:** **7863 ms** (`FINISH +7863ms recording active`)

| Step | Δ | Cumulative | Phase |
|------|---|------------|-------|
| ViewModel → session handoff | 12 ms | 12 ms | `promoteForeground`, `session.startRecording` |
| captureMutex | 1 ms | 13 ms | No lock contention |
| **quiesceUsb** | **4976 ms** | **4992 ms** | Stop monitor / playback / capture before routing |
| ↳ concurrent OSC | 2105 ms | — | `query 82 paths` (during quiesce window) |
| ↳ concurrent OSC | 2772 ms | — | second `query 82 paths` |
| **routing.beforeRecord** | **2609 ms** | **7601 ms** | XR18 OSC session (probe + read + apply) |
| ↳ read 16 ch | 2067 ms | — | 48 OSC paths |
| ↳ apply 3/16 ch | 172 ms | — | Only 3 strips needed routing changes |
| ensureCapture (USB) | 127 ms | 7728 ms | Native USB capture start |
| captureEngine.startRecording | 64 ms | 7813 ms | Session dir + WAV writers |
| UI `isRecording=true` | 50 ms | **7863 ms** | Timer visible in UI |

### Observations

- **~5 s** spent in `quiesceUsb`. The e2e test had **monitoring on** before Record; stopping monitor/capture dominated this window.
- Two **82-path OSC queries** (~2.6 s each) ran concurrently during quiesce — not part of the batched 48-path routing read. Source still to be isolated (see [latency reduction plan](xr18-transport-latency-reduction.md)).
- Routing apply itself was relatively fast once reads completed: **172 ms** to change 3 channels.
- USB + file setup (**~200 ms**) is already near a “fast” target.

---

## 2. Stop → auto soundcheck session loaded

**Trace label:** `RECORD-STOP→SOUNDCHECK`  
**Total:** **6531 ms** (`FINISH +6531ms soundcheck session loaded`)

| Step | Δ | Cumulative | Phase |
|------|---|------------|-------|
| **captureEngine.stopRecording** | **5028 ms** | **5031 ms** | Flush/close 18-channel WAV (~13 s recording) |
| routing.afterRecordRestore | 464 ms | 5495 ms | Read 48 paths (182 ms) + restore 3 ch (190 ms) |
| UI + AUTO_SOUNDCHECK trigger | 20 ms | 5515 ms | `isRecording=false`, mode switch |
| refreshSoundcheckLibrary | 874 ms | ~6390 ms | Scan 14 completed sessions on disk |
| selectSoundcheckSession | ~120 ms | 6515 ms | Metadata + UI prep |
| warmPlaybackRoute | 16 ms | **6531 ms** | USB playback route ready |

### Observations

- **Stop feels slow** mainly because **finalizing 18 WAV files takes ~5 s** — expected for multitrack flush, not routing.
- Routing restore is **< 500 ms**.
- Library scan adds **~0.9 s** before session UI is ready.
- Waveform peaks load **asynchronously** after `FINISH` (cache hit **456 ms** for 18 ch in this run — does not block the FINISH line).

---

## 3. Play in soundcheck

**Trace label:** `SOUNDCHECK-PLAY`  
**Total:** **3380 ms** (`FINISH +3380ms playback started`)

| Step | Δ | Cumulative | Phase |
|------|---|------------|-------|
| **routing before USB** | **2839 ms** | **2868 ms** | Full OSC apply via AUTO `peekApply` before USB route |
| ↳ read 16 ch | 2086 ms | — | 48 paths |
| ↳ apply 13/16 ch | 727 ms | — | USB-return targets; 2 verify attempts |
| ensurePlayback (USB) | 4 ms | 2897 ms | Stream already open |
| routing after USB (reapply) | 312 ms | 3219 ms | All 16 already matched — skipped writes |
| player.playSession | 82 ms | 3306 ms | Native playback engine |
| UI cursor advance | 5 ms | **3311 ms** | Audible/visible playback |

### Observations

- **Play is slow almost entirely because routing is fully applied before USB opens** (~2.8 s), then a mostly no-op reapply runs after USB (~0.3 s).
- Native playback start (**82 ms**) is already fast.
- Soundcheck was intended to **capture baseline before USB** and **apply once after USB**; AUTO `peekApply` still performs a full apply on the pre-USB hook (bug / design gap).

---

## Summary table

| User action | Total to “ready” | Dominant cost | Fast path today |
|-------------|------------------|---------------|-----------------|
| **Record** | 7.9 s | quiesceUsb ~5 s + routing read ~2 s | USB + WAV ~200 ms |
| **Stop → soundcheck** | 6.5 s | WAV flush ~5 s | Routing restore ~0.5 s |
| **Play** | 3.4 s | Pre-USB routing apply ~2.8 s | playSession ~80 ms |

---

## Instrumentation (code)

| Component | Role |
|-----------|------|
| `TransportTrace` / `TransportTraceHub` | Step timing with `+total` and `Δdelta` per user action |
| `Xr18RoutingLog` | OSC probe/read/apply timing; folded into active trace via `TransportTraceHub.markActive` |
| `RoutingAutomationHooksImpl` | Routing hook boundaries |
| `MixerSessionController` | USB quiesce, capture, playback, soundcheck load |
| `MainViewModel` | Record/Stop/post-record soundcheck handoff |

Filter while testing on device:

```bash
adb logcat -s OpenMultiTrack/TransportTrace:I OpenMultiTrack/Xr18Routing:I
```

---

## Related docs

- [XR18 routing automation](xr18-routing-automation.md) — feature design and OSC model
- [XR18 transport latency reduction](xr18-transport-latency-reduction.md) — proposals to reach ~200 ms start times
