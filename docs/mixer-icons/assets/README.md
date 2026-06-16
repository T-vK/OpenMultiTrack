# Scribble icon assets (documentation only)

| Directory | Source | License |
| --------- | ------ | ------- |
| `mixing-station/` | [behringer-icons](https://github.com/mamarguerat/behringer-icons) SVG pack (ids 1–74) | See upstream repo |
| `flow8/` | `Flowmix_v1.9.apk` drawables (`input_icon_NNN`, xxhdpi → 64×64 PNG) | Behringer app assets — doc/research use |

Regenerate:

```bash
cd docs/flow8-reverse-engineering/tools
python3 extract_icon_assets.py
```

Not bundled in the OpenMultiTrack APK.
