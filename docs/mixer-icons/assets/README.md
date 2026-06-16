# Scribble icon assets (documentation only)

| Directory | Source | Files |
| --------- | ------ | ----- |
| `mixing-station/` | X32 BMP originals ([behringer-icons](https://github.com/mamarguerat/behringer-icons)) → PNG `{id}.png` | ids 1–74 (desk reference) |
| `mixing-station-wing/` | Mixing Station APK atlas `wing_ch_TTNN` | 130 channel icons (X32-family + WING picker) |
| `mixing-station-brands/` | Mixing Station APK atlas (`mt_x32`, `ah_sq`, `b_wing`, …) | 28 mixer branding images |
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
| `icon_sprite_sheet.png` | 1:1 grid — **reserved slot block per Mixing Station mixer line** (28 lines), then FLOW 8 picker (100) |

Each MS mixer line gets its own contiguous block:

| Fill | Mixers | Slots | Artwork |
| ---- | ------ | ----- | ------- |
| `x32` | Behringer X32/M32, X-Air/XR | 74 | MS `wing_ch_*` atlas (ids 2–74); slot 1 may show `mt_x32` / `mt_xair` branding |
| `wing` | Behringer Wing | 130 | MS `wing_ch_*` picker layout |
| `brand` | All other MS-supported desks | 74 | Empty reserved slots; slot 1 shows MS mixer branding (`ah_sq`, `sc_vi`, …) |

Use `--no-brand-fallback` to leave brand-mixer first slots empty as well.

`icon_sprite_sheet_manifest.json` lists cell coordinates, `mixer_key`, `slot_index`, and `present` for vision / img2img tooling.

Not bundled in the OpenMultiTrack APK.
