# 07 — Implicit feedback (IFB) playback

The Behringer FLOW 8 UAC2 playback interface uses **implicit feedback** (USB Audio
Class 2.0). Playback OUT isoch on endpoint `0x01` is paced by the capture IN endpoint
`0x81` on a companion interface — the host must keep capture IN streaming while
sending playback OUT, or the mixer USB returns (U01–U04) stay silent.

## Descriptor layout (recording mode)

From the hardware probe fixture (`flow8_recording_mode.bin`):

| Interface | Direction | Endpoint | Role |
| --------- | --------- | -------- | ---- |
| 1 (playback) | OUT | `0x01` | 4ch 32-bit PCM @ 48 kHz → USB returns U01–U04 |
| 2 (capture) | IN | `0x81` | 10ch 32-bit PCM @ 48 kHz ← mixer inputs + Main L/R |

The playback alt setting declares a **Feedback Endpoint** association pointing at
capture `0x81`. The device does not accept a separate explicit feedback EP; timing is
derived from completed capture IN URBs.

## Linux kernel quirk `QUIRK_FLAG_IFB_SILENCE_ON_EMPTY`

Commit [a238120](https://github.com/torvalds/linux/commit/a23812004228d4b041a858b927db787a7ff80f50)
adds this quirk for FLOW 8 in `snd-usb-audio`. It does **not** mean “start capture
before playback” or “keep multitrack capture running”.

What it fixes: during long playback (5–35 minutes), capture IN URBs can occasionally
return with every iso frame errored (`-EXDEV`). In `snd_usb_handle_sync_urb()`, that
looks like `bytes == 0`, which used to skip enqueueing the paired playback OUT URB.
The OUT ring then starves permanently while ALSA still reports RUNNING.

With the quirk, ALSA still enqueues a `packet_info` with zero-length packets so
`prepare_outbound_urb` emits **silence** and the OUT ring keeps moving.

OpenMultiTrack bypasses `snd-usb-audio`, but the same behaviour applies in spirit:
after the playback stream is armed, always keep submitting OUT URBs (silence pads on
underrun). Do not stop the OUT isoch loop when the ring is temporarily empty.

This quirk is **orthogonal** to the IFB feeder: the feeder keeps capture IN alive for
implicit feedback timing; the quirk covers glitchy capture URBs during sustained
playback.

## OpenMultiTrack implementation

### Handoff order (crash avoidance)

FLOW 8 firmware locks up if **application multitrack capture** and playback compete.
Our sequence:

1. Stop multitrack capture (`forceStopAllRecording`) and settle (~120 ms) when needed.
2. Open **playback OUT** first (`NativeUac2Engine.startPlayback`).
3. Start **IFB feeder** on the capture route (`startIfbFeederCapture`) — IN isoch
   runs but PCM is discarded.
4. On pause/stop (suspend), keep the native playback engine and IFB feeder warm for
   instant resume. Full USB teardown only on hard stop (leave soundcheck, disconnect).

Do **not** start the IFB feeder before playback open or tear down playback on every
play press — that was causing firmware crashes and multi-second start latency.

### Playback ring arming

`uac2_playback.cpp` uses a one-shot `stream_armed_` flag: hold silence until the ring
reaches `playbackMinPrimeFrames()` (~50 ms), then send real PCM even if the ring
later runs low (count underruns, pad with zeros for that URB only).

### Backend preference

On Android tablets, libusb is tried before usbdevfs for playback and IFB feeder open
paths — usbdevfs `USBDEVFS_SUBMITURB` often returns `EINVAL` while libusb works.

## Future work

True IFB pacing would submit each playback OUT URB when a capture IN URB completes
(same model as ALSA `snd_usb_handle_sync_urb`), instead of wall-clock libusb
re-submission. The IFB feeder plus silence-on-underrun is the current pragmatic fix.
