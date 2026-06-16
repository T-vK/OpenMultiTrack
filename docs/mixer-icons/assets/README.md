# Scribble icon assets (documentation only)

| Directory | Source | Files |
| --------- | ------ | ----- |
| `mixing-station/` | X32 BMP originals ([behringer-icons](https://github.com/mamarguerat/behringer-icons)) → PNG `{id}.png` | ids 1–74 |
| `flow8/` | `Flowmix_v1.9.apk` drawables `input_icon_NNN` (xxhdpi → 64×64 PNG) | 100 picker slots |

Regenerate:

```bash
cd docs/flow8-reverse-engineering/tools
python3 extract_icon_assets.py
python3 serve_flow8_mapper.py   # browser UI — assign, skip-to-end, fix mistakes
python3 export_icon_tables.py all   # patches doc 06 + mixer-icons.md
python3 make_contact_sheet.py       # ../mixer-icons/generated/icon_sprite_sheet.png
```

Sprite sheet (gitignored under `generated/`):

| File | Contents |
| ---- | -------- |
| `icon_sprite_sheet.png` | 1:1 grid — MS ids 1–74, then FLOW 8 picker (174 icons), label under each icon |

`icon_sprite_sheet_manifest.json` lists cell coordinates for vision / img2img tooling.

Not bundled in the OpenMultiTrack APK.
