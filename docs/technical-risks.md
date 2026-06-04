# Technical Risk Assessment

## 1. USB UAC2 multichannel on Android

**Risk**: Many phones/tablets expose only **stereo** for USB class-compliant devices, or cap channel counts (2/6/8) despite the mixer offering 18×18 (XR18) or 32×32 (X32).

**Evidence needed on hardware** (see [`hardware-assumptions.md`](hardware-assumptions.md)):

- `adb shell dumpsys audio` while XR18/X32 connected.
- Oboe `openStream()` with `setDeviceId(usbAudioDeviceId)` and reported `getChannelCount()`.

**Mitigation**:

- Probe at connect; show **achieved vs requested** channels in UI.
- Document tested devices; degrade to fewer channels with explicit user ack.
- Prefer **interleaved single stream** when OS exposes full channel count (one clock domain).
- Target **USB host tablets** (Samsung Tab, etc.) for production use; phones as secondary.

## 2. Latency (playback → mixer returns)

**Risk**: Android USB + buffer sizing adds tens of ms; affects soundcheck feel, not record sync.

**Mitigation**:

- Oboe `PerformanceMode::LowLatency`, minimum stable buffer from `getBurstSize()`.
- Separate **monitor tap** (future) from FOH return path if needed.
- Measure round-trip with loopback cable + log timestamps.

## 3. Storage throughput

**Risk**: 18ch × 48 kHz × 24 bit ≈ **25 MB/s** (~1.5 GB/min). eMMC/SD may stall; long sessions risk dropouts or truncated files.

**Mitigation**:

- Writer thread at elevated priority; large OS buffers; `fdatasync` on orderly stop only.
- Pre-flight **free space** estimate; auto-stop at configurable headroom (default 2 GB).
- Optional **RF64** WAV for >4 GB; per-track files reduce seek rewrite cost.
- FLAC optional later (CPU cost; separate encoder thread).

## 4. Seek on multi-hour sessions

**Risk**: WAV lacks sample index; seeking by division drifts on VBR (N/A for PCM) but **slow** for huge files without chunk index.

**Mitigation**:

- PCM: `offset = frame * blockAlign` (exact).
- Background **sidecar index** (`.omtidx`) every N seconds during record for instant seek.
- Reader prefetch after seek; unified `transportFrame` in engine.
- All tracks seek from same frame number (sample-aligned by construction).

## 5. USB disconnect / power

**Risk**: Cable pull corrupts open files and leaves mixer routing wrong.

**Mitigation**:

- `UsbManager` detach listener → engine `emergencyStop()` → finalize WAV headers (RIFF sizes).
- OSC “safe idle” snapshot optional on disconnect.
- Foreground service + persistent notification during record (milestone 2).

## 6. Device compatibility matrix

**Risk**: Vendor-specific USB audio bugs, exclusive access conflicts.

**Mitigation**:

- No exclusive claim unless required; release Oboe stream on background.
- Clear error codes: `DEVICE_BUSY`, `CHANNEL_COUNT_UNSUPPORTED`, `PERMISSION_DENIED`.
- Community-maintained `docs/compatibility.md` (empty template in milestone 2).

## 7. OSC routing correctness

**Risk**: Wrong address breaks live show routing.

**Mitigation**:

- Snapshots are **user-recorded** from current state, not hardcoded factory routes.
- Confirm via OSC feedback; timeout → revert + error.
- Dry-run “preview” listing OSC commands before first apply (milestone 4).

## 8. F-Droid / reproducible builds

**Risk**: Oboe submodule pin drift; NDK version mismatch.

**Mitigation**:

- Pin Oboe git tag in `.gitmodules`.
- Document NDK r26d in `docs/reproducible-builds.md`.
- `fdroiddata` recipe uses `sudo apt-get` NDK or sdk/ndk from F-Droid buildserver.
- No binary blobs; verify licenses in `NOTICE`.

## 9. Web remote security

**Risk**: Open HTTP on LAN without TLS.

**Mitigation**:

- Bind to Wi‑Fi interface; optional access token (milestone 4).
- No external network calls; document threat model.
- Not a focus for milestone 1.

## Decision log (needs product input)

| Topic | Options | Recommendation |
|-------|---------|----------------|
| Default recording layout | Per-track vs interleaved | **Interleaved + optional split** post-record |
| Min supported Android | 26 vs 29 | **API 26** (AAudio device id); raise if USB bugs on 26–28 |
| FLAC in v1 | yes/no | **Defer** to v1.1 (CPU + validation) |
