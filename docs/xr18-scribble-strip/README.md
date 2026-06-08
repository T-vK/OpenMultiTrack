# Behringer XR18 — Scribble Strip Access

Notes on reading **channel scribble-strip labels and colors** from the
**Behringer XR18** (X-Air family).

The scribble strip is the per-channel name and color shown on the mixer UI and
remote apps. Values are ordinary OSC parameters — not exposed via plain MIDI CC.

> **Status:** Verified on a locally connected XR18 (firmware 1.18, LAN
> `192.168.3.63`). Network OSC is the recommended path; USB MIDI OSC works when
> pass-thru is disabled.

## Device identity

| Field            | Value                              |
| ---------------- | ---------------------------------- |
| USB Vendor ID    | `0x1397` (Music Tribe / Behringer) |
| USB Product ID   | `0x00D4`                           |
| USB Product name | `X18/XR18`                         |
| OSC UDP port     | **10024** (X32 family uses 10023)  |
| Input strips     | 16 (`/ch/01`–`/ch/16`)             |
| USB audio I/O    | 18×18                              |

## Quick start

```bash
python3 docs/xr18-scribble-strip/tools/xr18_scribble_osc.py
```

Auto-discovers the mixer with `/xinfo`, then prints name and color for each of
the **18 USB audio channels** (resolving buses, aux playback, main LR, etc. via
`/routing/usb/NN/src`).

Pin a known IP:

```bash
python3 docs/xr18-scribble-strip/tools/xr18_scribble_osc.py --ip 192.168.3.63
```

No dependencies beyond Python 3.

## Document index

| File | Contents |
| ---- | -------- |
| [`01-scribble-strip-access.md`](./01-scribble-strip-access.md) | OSC paths, USB routing, network vs USB MIDI, discovery, color map |
| [`tools/xr18_scribble_osc.py`](./tools/xr18_scribble_osc.py) | Discover mixer; print USB 1–18 names/colors via routing |

## How the pieces fit together

```
  Recommended                         Optional (USB MIDI)
  ───────────                         ───────────────────

  Host ──UDP 10024──▶ XR18             Host ──USB MIDI SysEx──▶ XR18
         /xinfo  (discover)                    F0 00 20 32 32 /ch/01/config/name F7
         /routing/usb/NN/src  (what USB channel carries)
         /ch/NN/config, /bus/N/config, /rtn/aux/config, …
              │
              ▼
         USB 1–18 → name + color
```

Network OSC does **not** depend on USB-DIN Pass Thru or Mixing Station — any app
on the same LAN can query the desk.

## Sources

- [X-Air OSC command list](https://behringer.world/wiki/doku.php?id=x-air_osc)
  (Behringer World Wiki)
- [X-Air MIDI table](https://behringer.world/wiki/doku.php?id=x-air_midi) — OSC
  over SysEx wrapper `F0 00 20 32 32 … F7`
- Live verification on XR18 firmware 1.18 (June 2026)
