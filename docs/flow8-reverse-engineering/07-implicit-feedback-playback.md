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

## Linux kernel quirk (reference only)

Commit [a238120](https://github.com/torvalds/linux/commit/a23812004228d4b041a858b927db787a7ff80f50)
adds `QUIRK_FLAG_IFB_SILENCE_ON_EMPTY` for FLOW 8 in `snd-usb-audio`: when the
playback buffer is empty, ALSA sends silence on OUT rather than stalling the implicit
feedback clock.

OpenMultiTrack bypasses `snd-usb-audio` and talks to the device through `usbdevfs` /
libusb in userspace. The quirk is not applied automatically, but the behaviour it
documents is still required:

1. Keep capture IN isoch alive during playback (IFB feeder).
2. Prime the playback ring before sending non-silent OUT (initial silence is OK).
3. Do not re-gate to silence after the stream is armed — that advances the host
   playhead while the mixer hears zeros (silent USB returns).

## OpenMultiTrack implementation

### IFB feeder capture

Before starting UAC2 playback on FLOW 8:

1. Stop normal multitrack capture (`forceStopAllRecording`).
2. Short settle delay (`PRE_PLAYBACK_DELAY_MS`).
3. Start **IFB feeder** on the capture route via `NativeUac2Engine.startIfbFeederCapture`:
   submits capture IN URBs and discards PCM (`ifb_feeder_mode_` in `uac2_capture.cpp`).
4. Start playback OUT on interface 1.

The feeder is stopped in `teardownFlow8UsbPlaybackLocked` and when preparing for
capture again.

### Playback ring arming

`uac2_playback.cpp` uses a one-shot `stream_armed_` flag: hold silence until the ring
reaches `playbackMinPrimeFrames()` (~100 ms), then send real PCM even if the ring
later runs low (count underruns, pad with zeros for that URB only).

### Backend preference

On Android tablets, libusb is tried before usbdevfs for both IFB feeder and playback
open paths — usbdevfs `USBDEVFS_SUBMITURB` often returns `EINVAL` while libusb works.

## Future work

True IFB pacing would submit each playback OUT URB when a capture IN URB completes
(same model as ALSA `snd_usb_handle_sync_urb`), instead of wall-clock libusb
re-submission. The IFB feeder is the minimal fix to keep the feedback clock running.
