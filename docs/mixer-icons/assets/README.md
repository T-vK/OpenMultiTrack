# Scribble icon assets (documentation only)

| Directory | Source | Files |
| --------- | ------ | ----- |
| `mixing-station/` | X32 BMP originals ([behringer-icons](https://github.com/mamarguerat/behringer-icons)) → PNG `{id}.png` | ids 1–74 — **MS channel icons on sprite sheet** |
| `mixing-station-wing/` | Mixing Station APK atlas `wing_ch_TTNN` | 130 WING picker icons (not used on sprite sheet) |
| `mixing-station-brands/` | Mixing Station APK atlas (`mt_x32`, `ah_sq`, `b_wing`, …) | 28 mixer branding images; `bfl` uses manual `Blackmagic Fairlight Live.png` |
| `flow8/` | `Flowmix_v1.9.apk` drawables `input_icon_NNN` (xxhdpi → 64×64 PNG) | 100 picker slots |

Regenerate:

```bash
cd docs/flow8-reverse-engineering/tools
python3 extract_icon_assets.py
python3 extract_ms_wing_icons.py      # needs Mixing Station APK (see script --help)
python3 extract_ms_brand_icons.py     # mixer picker branding from same APK
python3 serve_flow8_mapper.py         # browser UI — assign, skip-to-end, fix mistakes
python3 export_icon_tables.py all     # patches doc 06 + mixer-icons.md
python3 make_contact_sheet.py         # ../mixer-icons/generated/icon_sprite_sheet.png
```

Sprite sheet (gitignored under `generated/`):

| File | Contents |
| ---- | -------- |
| `icon_sprite_sheet.png` | 1:1 grid — **202 cells**: MS channel icons from `mixing-station/` (74), FLOW 8 picker (100), one MS mixer brand per supported line (28) |

`icon_sprite_sheet_manifest.json` lists cell coordinates for vision / img2img tooling.

Not bundled in the OpenMultiTrack APK.
