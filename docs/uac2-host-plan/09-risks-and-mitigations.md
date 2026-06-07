# Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Kernel holds AS interface; claim fails | High | UAC2 unusable | Oboe fallback; probe UI explains; doc powered hub + replug |
| Android host isochronous bugs | Medium | Drops/glitches | Buffer sizing; quirk table; test matrix |
| libusb + NDK build complexity | Medium | CI break | Pin libusb tag; minimal Android backend only |
| Double audio path (Oboe + UAC2) | Medium | Crash/conflict | Router ensures one backend active |
| Flow 8 firmware mode wrong | Low | Wrong channel count | UI note; descriptor shows available alts |
| Huawei / UNISOC devices | High | Broken USB | Document unsupported; detect chipset |
| GPL linking libusb (LGPL) | Low | License issue | Dynamic link libusb (LGPL allows); document in NOTICE |
| Scope creep (MIDI, SRC) | Medium | Delay | Strict phase gates in 07-implementation-phases.md |

## Fallback hierarchy

```
1. UAC2 capture/playback (full channels)
2. Oboe capture/playback (whatever Android exposes)
3. Stereo record only + user warning
4. Block record with actionable error message
```

## When to add a quirk entry

Add to `uac2_quirks.cpp` when:

- Descriptor says N ch but device needs specific alt order
- Requires `wMaxPacketSize` padding
- Needs 1-packet-per-transfer (eXtream pref for some devices)
- Must open playback before capture

Do **not** add per-mixer code paths unless quirk table insufficient.
