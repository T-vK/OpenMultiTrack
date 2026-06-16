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
python3 make_contact_sheet.py       # ../mixer-icons/generated/*.png
```

Contact sheets (gitignored under `generated/`):

| File | Contents |
| ---- | -------- |
| `icon_contact_sheet_ms.png` | Mixing Station ids 1–74 (X32 · M32 · X-Air/XR · WING · FLOW resolved ids) |
| `icon_contact_sheet_flow8.png` | FLOW 8 picker — 100 drawables by input type |
| `icon_contact_sheet_all.png` | Both sets in one tall sheet (174 cells) |

Each PNG has a `*_manifest.json` with pixel coordinates for vision / img2img tooling.

Not bundled in the OpenMultiTrack APK.
