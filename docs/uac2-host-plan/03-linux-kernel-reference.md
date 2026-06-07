# Linux Kernel Reference

## What Linux does for our mixers

On desktop Linux, Flow 8, XR18, and X32 use **`snd-usb-audio`** (`sound/usb/`). No vendor driver. The kernel:

1. Parses USB configuration descriptor
2. Identifies Audio Control (AC) and Audio Streaming (AS) interfaces
3. Selects alternate setting with sufficient `wMaxPacketSize` for requested channels
4. Submits **isochronous URBs** on IN/OUT endpoints
5. Applies **quirks** when devices violate assumptions

## Files to study (not copy verbatim)

| Linux path | Relevant logic |
|------------|----------------|
| `sound/usb/card.c` | Device probe, interface binding |
| `sound/usb/endpoint.c` | Endpoint setup, packet size |
| `sound/usb/stream.c` | Altsetting, format negotiation |
| `sound/usb/format.c` | PCM format parsing |
| `sound/usb/clock.c` | UAC2 clock entities |
| `sound/usb/quirks.c` + `quirks-table.h` | Vendor workarounds |

## What we can reuse directly

- **Descriptor parsing algorithms** — walk config descriptor, find `USB_DT_CS_INTERFACE` for UAC2 class-specific descriptors
- **Terminal/route concepts** — Input Terminal → Output Terminal paths (libuac models this well)
- **Quirk IDs** — e.g. alignments, invalid dB ranges, fixed channel maps
- **Alt-setting selection heuristic** — pick lowest alt setting that fits channel count + sample rate within `wMaxPacketSize`

## What we cannot reuse

- **Kernel USB stack** (`usb_submit_urb`, DMA, IRQ)
- **ALSA PCM layer** (`snd_pcm`, `snd_usb_pcm`)
- **Module loading / sysfs**

## Mapping Linux concepts → omt-uac2

| Linux | omt-uac2 |
|-------|----------|
| `snd_usb_audio` probe | `Uac2Descriptor::parse(configBytes)` |
| `snd_usb_endpoint` | `Uac2Endpoint` struct |
| `snd_usb_pcm_prepare` | `Uac2Stream::open(config)` |
| `snd_usb_pcm_trigger` | `Uac2Capture::start()` / `stop()` |
| `pcm_buffer` | `SpscRingBuffer` |
| `quirks-table.h` entry | `Uac2Quirks::lookup(vid, pid)` |

## UAC2-specific descriptors (implement in Phase 1)

From USB Audio Class 2.0 spec:

- `UAC2_CLOCK_SOURCE` (0x24)
- `UAC2_INPUT_TERMINAL` (0x02) / `UAC2_OUTPUT_TERMINAL` (0x03)
- `UAC2_FEATURE_UNIT` (0x06)
- `UAC2_FORMAT_TYPE_I` (0x01) in AS interface
- `UAC2_AS_GENERAL` (0x01)

Support **Type I PCM** first (covers Behringer mixers). Defer Type II/III.

## Behringer-specific notes (descriptor level, not custom driver)

| Device | USB mode | Expected streams |
|--------|----------|------------------|
| Flow 8 | Recording | 10-in / 2-out (firmware may offer 4-out on newer FW) |
| XR18 | 18ch class-compliant | 18-in / 18-out |
| X32 | USB card dependent | up to 32×32 |

Firmware mode changes **which alt settings exist**, not a different driver model.
