# Mixer scribble icon reference

Channel scribble icons on Behringer X32 / X-Air consoles, **Mixing Station**, and
**FLOW 8** share a common numeric id space (**1–74**). OpenMultiTrack resolves FLOW 8
BLE/USB state to those ids before rendering strip glyphs.

> **Assets:** Mixing Station / X32 artwork is from the community
> [behringer-icons](https://github.com/mamarguerat/behringer-icons) SVG pack (same
> numbering as the desk). FLOW 8 picker PNGs are extracted from `Flowmix_v1.9.apk`
> (`res/drawable-*/input_icon_NNN`). Regenerate with the commands below.

## Regenerating

```bash
cd docs/flow8-reverse-engineering/tools
python3 extract_icon_assets.py      # download SVGs + extract Flow PNGs
python3 export_icon_tables.py doc   # rewrite this file
```

To refresh FLOW UI labels from a device with Flow Mix installed, extend and run
`Flow8IconTableExtractor` (see `tools/Flow8IconTableExtractor.java`).

---

## Mixing Station / X32 icons (ids 1–74)

Labels are the original names from the [behringer-icons](https://github.com/mamarguerat/behringer-icons)
pack. Slug ids (`kick-back`, …) match OSC / `MixingStationIcons.kt`.

| Display label | Slug | ID | Icon |
| ------------- | ---- | -- | ---- |
| No icon | `blank` | 1 | <img src="mixer-icons/assets/mixing-station/1.svg" alt="No icon" width="32" /> |
| Kick Back | `kick-back` | 2 | <img src="mixer-icons/assets/mixing-station/2.svg" alt="Kick Back" width="32" /> |
| Kick Front | `kick-front` | 3 | <img src="mixer-icons/assets/mixing-station/3.svg" alt="Kick Front" width="32" /> |
| Snare Top | `snare-top` | 4 | <img src="mixer-icons/assets/mixing-station/4.svg" alt="Snare Top" width="32" /> |
| Snare Bottom | `snare-bottom` | 5 | <img src="mixer-icons/assets/mixing-station/5.svg" alt="Snare Bottom" width="32" /> |
| High Tom | `tom-high` | 6 | <img src="mixer-icons/assets/mixing-station/6.svg" alt="High Tom" width="32" /> |
| Mid Tom | `tom-medium` | 7 | <img src="mixer-icons/assets/mixing-station/7.svg" alt="Mid Tom" width="32" /> |
| Floor Tom | `floor-tom` | 8 | <img src="mixer-icons/assets/mixing-station/8.svg" alt="Floor Tom" width="32" /> |
| Hi-Hat | `hi-hat` | 9 | <img src="mixer-icons/assets/mixing-station/9.svg" alt="Hi-Hat" width="32" /> |
| Ride | `crash` | 10 | <img src="mixer-icons/assets/mixing-station/10.svg" alt="Ride" width="32" /> |
| Drum Kit | `drum-kit` | 11 | <img src="mixer-icons/assets/mixing-station/11.svg" alt="Drum Kit" width="32" /> |
| Cowbell | `cowbell` | 12 | <img src="mixer-icons/assets/mixing-station/12.svg" alt="Cowbell" width="32" /> |
| Bongos | `bongos` | 13 | <img src="mixer-icons/assets/mixing-station/13.svg" alt="Bongos" width="32" /> |
| Congas | `congas` | 14 | <img src="mixer-icons/assets/mixing-station/14.svg" alt="Congas" width="32" /> |
| Tambourine | `tambourine` | 15 | <img src="mixer-icons/assets/mixing-station/15.svg" alt="Tambourine" width="32" /> |
| Vibraphone | `vibraphone` | 16 | <img src="mixer-icons/assets/mixing-station/16.svg" alt="Vibraphone" width="32" /> |
| Electric Bass | `electric-bass` | 17 | <img src="mixer-icons/assets/mixing-station/17.svg" alt="Electric Bass" width="32" /> |
| Acoustic Bass | `acoustic-bass` | 18 | <img src="mixer-icons/assets/mixing-station/18.svg" alt="Acoustic Bass" width="32" /> |
| Contrabass | `contrabass` | 19 | <img src="mixer-icons/assets/mixing-station/19.svg" alt="Contrabass" width="32" /> |
| Les Paul Guitar | `les-paul` | 20 | <img src="mixer-icons/assets/mixing-station/20.svg" alt="Les Paul Guitar" width="32" /> |
| Ibanez Guitar | `ibanez` | 21 | <img src="mixer-icons/assets/mixing-station/21.svg" alt="Ibanez Guitar" width="32" /> |
| Washburn Guitar | `washburn` | 22 | <img src="mixer-icons/assets/mixing-station/22.svg" alt="Washburn Guitar" width="32" /> |
| Acoustic Guitar | `acoustic-guitar` | 23 | <img src="mixer-icons/assets/mixing-station/23.svg" alt="Acoustic Guitar" width="32" /> |
| Bass Amp | `bass-amp` | 24 | <img src="mixer-icons/assets/mixing-station/24.svg" alt="Bass Amp" width="32" /> |
| Guitar Amp | `guitar-amp` | 25 | <img src="mixer-icons/assets/mixing-station/25.svg" alt="Guitar Amp" width="32" /> |
| Amp Cabinet | `amp-cabinet` | 26 | <img src="mixer-icons/assets/mixing-station/26.svg" alt="Amp Cabinet" width="32" /> |
| Piano | `piano` | 27 | <img src="mixer-icons/assets/mixing-station/27.svg" alt="Piano" width="32" /> |
| Organ | `organ` | 28 | <img src="mixer-icons/assets/mixing-station/28.svg" alt="Organ" width="32" /> |
| Harpsichord | `harpsichord` | 29 | <img src="mixer-icons/assets/mixing-station/29.svg" alt="Harpsichord" width="32" /> |
| Keyboard | `keyboard` | 30 | <img src="mixer-icons/assets/mixing-station/30.svg" alt="Keyboard" width="32" /> |
| Synthesizer 1 | `synthesizer-1` | 31 | <img src="mixer-icons/assets/mixing-station/31.svg" alt="Synthesizer 1" width="32" /> |
| Synthesizer 2 | `synthesizer-2` | 32 | <img src="mixer-icons/assets/mixing-station/32.svg" alt="Synthesizer 2" width="32" /> |
| Synthesizer 3 | `synthesizer-3` | 33 | <img src="mixer-icons/assets/mixing-station/33.svg" alt="Synthesizer 3" width="32" /> |
| Keytar | `keytar` | 34 | <img src="mixer-icons/assets/mixing-station/34.svg" alt="Keytar" width="32" /> |
| Trumpet | `trumpet` | 35 | <img src="mixer-icons/assets/mixing-station/35.svg" alt="Trumpet" width="32" /> |
| Trombone | `trombone` | 36 | <img src="mixer-icons/assets/mixing-station/36.svg" alt="Trombone" width="32" /> |
| Saxophone | `saxophone` | 37 | <img src="mixer-icons/assets/mixing-station/37.svg" alt="Saxophone" width="32" /> |
| Clarinet | `clarinet` | 38 | <img src="mixer-icons/assets/mixing-station/38.svg" alt="Clarinet" width="32" /> |
| Violin | `violin` | 39 | <img src="mixer-icons/assets/mixing-station/39.svg" alt="Violin" width="32" /> |
| Cello | `cello` | 40 | <img src="mixer-icons/assets/mixing-station/40.svg" alt="Cello" width="32" /> |
| Male Vocal | `male-vocal` | 41 | <img src="mixer-icons/assets/mixing-station/41.svg" alt="Male Vocal" width="32" /> |
| Female Vocal | `female-vocal` | 42 | <img src="mixer-icons/assets/mixing-station/42.svg" alt="Female Vocal" width="32" /> |
| Choir | `choir` | 43 | <img src="mixer-icons/assets/mixing-station/43.svg" alt="Choir" width="32" /> |
| Hand Sign | `hand-sign` | 44 | <img src="mixer-icons/assets/mixing-station/44.svg" alt="Hand Sign" width="32" /> |
| Talk A | `talk-a` | 45 | <img src="mixer-icons/assets/mixing-station/45.svg" alt="Talk A" width="32" /> |
| Talk B | `talk-b` | 46 | <img src="mixer-icons/assets/mixing-station/46.svg" alt="Talk B" width="32" /> |
| Large Diaphragm Mic | `large-diaphragm-mic` | 47 | <img src="mixer-icons/assets/mixing-station/47.svg" alt="Large Diaphragm Mic" width="32" /> |
| Condenser Mic Left | `condenser-mic-left` | 48 | <img src="mixer-icons/assets/mixing-station/48.svg" alt="Condenser Mic Left" width="32" /> |
| Condenser Mic Right | `condenser-mic-right` | 49 | <img src="mixer-icons/assets/mixing-station/49.svg" alt="Condenser Mic Right" width="32" /> |
| Handheld Mic | `handheld-mic` | 50 | <img src="mixer-icons/assets/mixing-station/50.svg" alt="Handheld Mic" width="32" /> |
| Wireless Mic | `wireless-mic` | 51 | <img src="mixer-icons/assets/mixing-station/51.svg" alt="Wireless Mic" width="32" /> |
| Podium Mic | `podium-mic` | 52 | <img src="mixer-icons/assets/mixing-station/52.svg" alt="Podium Mic" width="32" /> |
| Headset Mic | `headset-mic` | 53 | <img src="mixer-icons/assets/mixing-station/53.svg" alt="Headset Mic" width="32" /> |
| XLR Jack | `xlr` | 54 | <img src="mixer-icons/assets/mixing-station/54.svg" alt="XLR Jack" width="32" /> |
| TRS Plug | `trs` | 55 | <img src="mixer-icons/assets/mixing-station/55.svg" alt="TRS Plug" width="32" /> |
| TRS Plug Left | `trs-left` | 56 | <img src="mixer-icons/assets/mixing-station/56.svg" alt="TRS Plug Left" width="32" /> |
| TRS Plug Right | `trs-right` | 57 | <img src="mixer-icons/assets/mixing-station/57.svg" alt="TRS Plug Right" width="32" /> |
| RCA Plug Left | `rca-left` | 58 | <img src="mixer-icons/assets/mixing-station/58.svg" alt="RCA Plug Left" width="32" /> |
| RCA Plug Right | `rca-right` | 59 | <img src="mixer-icons/assets/mixing-station/59.svg" alt="RCA Plug Right" width="32" /> |
| Reel to Reel | `tape` | 60 | <img src="mixer-icons/assets/mixing-station/60.svg" alt="Reel to Reel" width="32" /> |
| FX | `fx` | 61 | <img src="mixer-icons/assets/mixing-station/61.svg" alt="FX" width="32" /> |
| Computer | `computer` | 62 | <img src="mixer-icons/assets/mixing-station/62.svg" alt="Computer" width="32" /> |
| Monitor Wedge | `wedge` | 63 | <img src="mixer-icons/assets/mixing-station/63.svg" alt="Monitor Wedge" width="32" /> |
| Left Speaker | `speaker-right` | 64 | <img src="mixer-icons/assets/mixing-station/64.svg" alt="Left Speaker" width="32" /> |
| Right Speaker | `speaker-left` | 65 | <img src="mixer-icons/assets/mixing-station/65.svg" alt="Right Speaker" width="32" /> |
| Speaker Array | `speaker-array` | 66 | <img src="mixer-icons/assets/mixing-station/66.svg" alt="Speaker Array" width="32" /> |
| Speaker on a Pole | `speaker-on-pole` | 67 | <img src="mixer-icons/assets/mixing-station/67.svg" alt="Speaker on a Pole" width="32" /> |
| Amp Rack | `amp-rack` | 68 | <img src="mixer-icons/assets/mixing-station/68.svg" alt="Amp Rack" width="32" /> |
| Controls | `controls` | 69 | <img src="mixer-icons/assets/mixing-station/69.svg" alt="Controls" width="32" /> |
| Fader | `fader` | 70 | <img src="mixer-icons/assets/mixing-station/70.svg" alt="Fader" width="32" /> |
| MixBus | `mix-bus` | 71 | <img src="mixer-icons/assets/mixing-station/71.svg" alt="MixBus" width="32" /> |
| Matrix | `matrix` | 72 | <img src="mixer-icons/assets/mixing-station/72.svg" alt="Matrix" width="32" /> |
| Routing | `routing` | 73 | <img src="mixer-icons/assets/mixing-station/73.svg" alt="Routing" width="32" /> |
| Smiley | `smiley` | 74 | <img src="mixer-icons/assets/mixing-station/74.svg" alt="Smiley" width="32" /> |

---

## FLOW 8 picker icons

FLOW 8 does not send Mixing Station ids directly. The official app stores an
**input type** (0–5) and **preset index**; native code maps that pair to an MS id.
Drawable assets are named `input_icon_{type×100+preset:03d}`.

**Labels:** rows marked *(validated)* were read from the Flow Mix UI on hardware
(firmware v11749). Other labels are inferred from the resolved MS id and the
behringer-icons display name until a full native label dump is available.

| Flow label | Input type | Preset | Drawable | MS ID | MS slug | Icon |
| ---------- | ---------- | ------ | -------- | ----- | ------- | ---- |
| No icon | 0 (Dynamic mic) | 0 | `input_icon_000` | 1 | `blank` | <img src="mixer-icons/assets/flow8/input_icon_000.png" alt="No icon" width="32" /> |
| Large Diaphragm Mic | 0 (Dynamic mic) | 1 | `input_icon_001` | 47 | `large-diaphragm-mic` | <img src="mixer-icons/assets/flow8/input_icon_001.png" alt="Large Diaphragm Mic" width="32" /> |
| Condenser Mic Left | 0 (Dynamic mic) | 2 | `input_icon_002` | 48 | `condenser-mic-left` | <img src="mixer-icons/assets/flow8/input_icon_002.png" alt="Condenser Mic Left" width="32" /> |
| Condenser Mic Right | 0 (Dynamic mic) | 3 | `input_icon_003` | 49 | `condenser-mic-right` | <img src="mixer-icons/assets/flow8/input_icon_003.png" alt="Condenser Mic Right" width="32" /> |
| Wired Mic *(validated)* | 0 (Dynamic mic) | 4 | `input_icon_004` | 50 | `handheld-mic` | <img src="mixer-icons/assets/flow8/input_icon_004.png" alt="Wired Mic" width="32" /> |
| Handheld Mic | 0 (Dynamic mic) | 5 | `input_icon_005` | 50 | `handheld-mic` | <img src="mixer-icons/assets/flow8/input_icon_005.png" alt="Handheld Mic" width="32" /> |
| Wireless Mic | 0 (Dynamic mic) | 6 | `input_icon_006` | 51 | `wireless-mic` | <img src="mixer-icons/assets/flow8/input_icon_006.png" alt="Wireless Mic" width="32" /> |
| Wired Mic *(validated)* | 0 (Dynamic mic) | 7 | `input_icon_007` | 50 | `handheld-mic` | <img src="mixer-icons/assets/flow8/input_icon_007.png" alt="Wired Mic" width="32" /> |
| Headset Mic | 0 (Dynamic mic) | 8 | `input_icon_008` | 53 | `headset-mic` | <img src="mixer-icons/assets/flow8/input_icon_008.png" alt="Headset Mic" width="32" /> |
| XLR Jack | 0 (Dynamic mic) | 9 | `input_icon_009` | 54 | `xlr` | <img src="mixer-icons/assets/flow8/input_icon_009.png" alt="XLR Jack" width="32" /> |
| TRS Plug | 0 (Dynamic mic) | 10 | `input_icon_010` | 55 | `trs` | <img src="mixer-icons/assets/flow8/input_icon_010.png" alt="TRS Plug" width="32" /> |
| TRS Plug Left | 0 (Dynamic mic) | 11 | `input_icon_011` | 56 | `trs-left` | <img src="mixer-icons/assets/flow8/input_icon_011.png" alt="TRS Plug Left" width="32" /> |
| TRS Plug Right | 0 (Dynamic mic) | 12 | `input_icon_012` | 57 | `trs-right` | <img src="mixer-icons/assets/flow8/input_icon_012.png" alt="TRS Plug Right" width="32" /> |
| RCA Plug Left | 0 (Dynamic mic) | 13 | `input_icon_013` | 58 | `rca-left` | <img src="mixer-icons/assets/flow8/input_icon_013.png" alt="RCA Plug Left" width="32" /> |
| RCA Plug Right | 0 (Dynamic mic) | 14 | `input_icon_014` | 59 | `rca-right` | <img src="mixer-icons/assets/flow8/input_icon_014.png" alt="RCA Plug Right" width="32" /> |
| Large Diaphragm Mic | 1 (Condenser mic) | 0 | `input_icon_100` | 47 | `large-diaphragm-mic` | <img src="mixer-icons/assets/flow8/input_icon_100.png" alt="Large Diaphragm Mic" width="32" /> |
| Condenser Mic Left | 1 (Condenser mic) | 1 | `input_icon_101` | 48 | `condenser-mic-left` | <img src="mixer-icons/assets/flow8/input_icon_101.png" alt="Condenser Mic Left" width="32" /> |
| Condenser Mic Right | 1 (Condenser mic) | 2 | `input_icon_102` | 49 | `condenser-mic-right` | <img src="mixer-icons/assets/flow8/input_icon_102.png" alt="Condenser Mic Right" width="32" /> |
| Condenser Mic Left | 1 (Condenser mic) | 3 | `input_icon_103` | 48 | `condenser-mic-left` | <img src="mixer-icons/assets/flow8/input_icon_103.png" alt="Condenser Mic Left" width="32" /> |
| Condenser Mic Right | 1 (Condenser mic) | 4 | `input_icon_104` | 49 | `condenser-mic-right` | <img src="mixer-icons/assets/flow8/input_icon_104.png" alt="Condenser Mic Right" width="32" /> |
| Headset Mic | 1 (Condenser mic) | 5 | `input_icon_105` | 53 | `headset-mic` | <img src="mixer-icons/assets/flow8/input_icon_105.png" alt="Headset Mic" width="32" /> |
| XLR Jack | 1 (Condenser mic) | 6 | `input_icon_106` | 54 | `xlr` | <img src="mixer-icons/assets/flow8/input_icon_106.png" alt="XLR Jack" width="32" /> |
| TRS Plug | 1 (Condenser mic) | 7 | `input_icon_107` | 55 | `trs` | <img src="mixer-icons/assets/flow8/input_icon_107.png" alt="TRS Plug" width="32" /> |
| TRS Plug Left | 1 (Condenser mic) | 8 | `input_icon_108` | 56 | `trs-left` | <img src="mixer-icons/assets/flow8/input_icon_108.png" alt="TRS Plug Left" width="32" /> |
| TRS Plug Right | 1 (Condenser mic) | 9 | `input_icon_109` | 57 | `trs-right` | <img src="mixer-icons/assets/flow8/input_icon_109.png" alt="TRS Plug Right" width="32" /> |
| RCA Plug Left | 1 (Condenser mic) | 10 | `input_icon_110` | 58 | `rca-left` | <img src="mixer-icons/assets/flow8/input_icon_110.png" alt="RCA Plug Left" width="32" /> |
| Electric Bass | 2 (Guitar / bass) | 0 | `input_icon_200` | 17 | `electric-bass` | <img src="mixer-icons/assets/flow8/input_icon_200.png" alt="Electric Bass" width="32" /> |
| Acoustic Bass | 2 (Guitar / bass) | 1 | `input_icon_201` | 18 | `acoustic-bass` | <img src="mixer-icons/assets/flow8/input_icon_201.png" alt="Acoustic Bass" width="32" /> |
| Acoustic Guitar *(validated)* | 2 (Guitar / bass) | 2 | `input_icon_202` | 23 | `acoustic-guitar` | <img src="mixer-icons/assets/flow8/input_icon_202.png" alt="Acoustic Guitar" width="32" /> |
| Les Paul Guitar | 2 (Guitar / bass) | 3 | `input_icon_203` | 20 | `les-paul` | <img src="mixer-icons/assets/flow8/input_icon_203.png" alt="Les Paul Guitar" width="32" /> |
| Ibanez Guitar | 2 (Guitar / bass) | 4 | `input_icon_204` | 21 | `ibanez` | <img src="mixer-icons/assets/flow8/input_icon_204.png" alt="Ibanez Guitar" width="32" /> |
| Washburn Guitar | 2 (Guitar / bass) | 5 | `input_icon_205` | 22 | `washburn` | <img src="mixer-icons/assets/flow8/input_icon_205.png" alt="Washburn Guitar" width="32" /> |
| Acoustic Guitar | 2 (Guitar / bass) | 6 | `input_icon_206` | 23 | `acoustic-guitar` | <img src="mixer-icons/assets/flow8/input_icon_206.png" alt="Acoustic Guitar" width="32" /> |
| Bass Amp | 2 (Guitar / bass) | 7 | `input_icon_207` | 24 | `bass-amp` | <img src="mixer-icons/assets/flow8/input_icon_207.png" alt="Bass Amp" width="32" /> |
| Guitar Amp | 2 (Guitar / bass) | 8 | `input_icon_208` | 25 | `guitar-amp` | <img src="mixer-icons/assets/flow8/input_icon_208.png" alt="Guitar Amp" width="32" /> |
| Amp Cabinet | 2 (Guitar / bass) | 9 | `input_icon_209` | 26 | `amp-cabinet` | <img src="mixer-icons/assets/flow8/input_icon_209.png" alt="Amp Cabinet" width="32" /> |
| Electric Bass | 2 (Guitar / bass) | 10 | `input_icon_210` | 17 | `electric-bass` | <img src="mixer-icons/assets/flow8/input_icon_210.png" alt="Electric Bass" width="32" /> |
| Acoustic Bass | 2 (Guitar / bass) | 11 | `input_icon_211` | 18 | `acoustic-bass` | <img src="mixer-icons/assets/flow8/input_icon_211.png" alt="Acoustic Bass" width="32" /> |
| Contrabass | 2 (Guitar / bass) | 12 | `input_icon_212` | 19 | `contrabass` | <img src="mixer-icons/assets/flow8/input_icon_212.png" alt="Contrabass" width="32" /> |
| Les Paul Guitar | 2 (Guitar / bass) | 13 | `input_icon_213` | 20 | `les-paul` | <img src="mixer-icons/assets/flow8/input_icon_213.png" alt="Les Paul Guitar" width="32" /> |
| Ibanez Guitar | 2 (Guitar / bass) | 14 | `input_icon_214` | 21 | `ibanez` | <img src="mixer-icons/assets/flow8/input_icon_214.png" alt="Ibanez Guitar" width="32" /> |
| Washburn Guitar | 2 (Guitar / bass) | 15 | `input_icon_215` | 22 | `washburn` | <img src="mixer-icons/assets/flow8/input_icon_215.png" alt="Washburn Guitar" width="32" /> |
| Acoustic Guitar | 2 (Guitar / bass) | 16 | `input_icon_216` | 23 | `acoustic-guitar` | <img src="mixer-icons/assets/flow8/input_icon_216.png" alt="Acoustic Guitar" width="32" /> |
| Amp Cabinet | 2 (Guitar / bass) | 17 | `input_icon_217` | 26 | `amp-cabinet` | <img src="mixer-icons/assets/flow8/input_icon_217.png" alt="Amp Cabinet" width="32" /> |
| Piano | 3 (Line instrument) | 0 | `input_icon_300` | 27 | `piano` | <img src="mixer-icons/assets/flow8/input_icon_300.png" alt="Piano" width="32" /> |
| Organ | 3 (Line instrument) | 1 | `input_icon_301` | 28 | `organ` | <img src="mixer-icons/assets/flow8/input_icon_301.png" alt="Organ" width="32" /> |
| Harpsichord | 3 (Line instrument) | 2 | `input_icon_302` | 29 | `harpsichord` | <img src="mixer-icons/assets/flow8/input_icon_302.png" alt="Harpsichord" width="32" /> |
| Keyboard | 3 (Line instrument) | 3 | `input_icon_303` | 30 | `keyboard` | <img src="mixer-icons/assets/flow8/input_icon_303.png" alt="Keyboard" width="32" /> |
| Violine *(validated)* | 3 (Line instrument) | 4 | `input_icon_304` | 39 | `violin` | <img src="mixer-icons/assets/flow8/input_icon_304.png" alt="Violine" width="32" /> |
| Trumpet | 3 (Line instrument) | 5 | `input_icon_305` | 35 | `trumpet` | <img src="mixer-icons/assets/flow8/input_icon_305.png" alt="Trumpet" width="32" /> |
| Trombone | 3 (Line instrument) | 6 | `input_icon_306` | 36 | `trombone` | <img src="mixer-icons/assets/flow8/input_icon_306.png" alt="Trombone" width="32" /> |
| Saxophone | 3 (Line instrument) | 7 | `input_icon_307` | 37 | `saxophone` | <img src="mixer-icons/assets/flow8/input_icon_307.png" alt="Saxophone" width="32" /> |
| Clarinet | 3 (Line instrument) | 8 | `input_icon_308` | 38 | `clarinet` | <img src="mixer-icons/assets/flow8/input_icon_308.png" alt="Clarinet" width="32" /> |
| Cello | 3 (Line instrument) | 9 | `input_icon_309` | 40 | `cello` | <img src="mixer-icons/assets/flow8/input_icon_309.png" alt="Cello" width="32" /> |
| Tambourine | 3 (Line instrument) | 10 | `input_icon_310` | 15 | `tambourine` | <img src="mixer-icons/assets/flow8/input_icon_310.png" alt="Tambourine" width="32" /> |
| Vibraphone | 3 (Line instrument) | 11 | `input_icon_311` | 16 | `vibraphone` | <img src="mixer-icons/assets/flow8/input_icon_311.png" alt="Vibraphone" width="32" /> |
| Bongos | 3 (Line instrument) | 12 | `input_icon_312` | 13 | `bongos` | <img src="mixer-icons/assets/flow8/input_icon_312.png" alt="Bongos" width="32" /> |
| Congas | 3 (Line instrument) | 13 | `input_icon_313` | 14 | `congas` | <img src="mixer-icons/assets/flow8/input_icon_313.png" alt="Congas" width="32" /> |
| Synthesizer 1 | 3 (Line instrument) | 14 | `input_icon_314` | 31 | `synthesizer-1` | <img src="mixer-icons/assets/flow8/input_icon_314.png" alt="Synthesizer 1" width="32" /> |
| Synthesizer 2 | 3 (Line instrument) | 15 | `input_icon_315` | 32 | `synthesizer-2` | <img src="mixer-icons/assets/flow8/input_icon_315.png" alt="Synthesizer 2" width="32" /> |
| Synthesizer 3 | 3 (Line instrument) | 16 | `input_icon_316` | 33 | `synthesizer-3` | <img src="mixer-icons/assets/flow8/input_icon_316.png" alt="Synthesizer 3" width="32" /> |
| Keytar | 3 (Line instrument) | 17 | `input_icon_317` | 34 | `keytar` | <img src="mixer-icons/assets/flow8/input_icon_317.png" alt="Keytar" width="32" /> |
| Les Paul Guitar | 4 (Guitar page (extended)) | 0 | `input_icon_400` | 20 | `les-paul` | <img src="mixer-icons/assets/flow8/input_icon_400.png" alt="Les Paul Guitar" width="32" /> |
| Ibanez Guitar | 4 (Guitar page (extended)) | 1 | `input_icon_401` | 21 | `ibanez` | <img src="mixer-icons/assets/flow8/input_icon_401.png" alt="Ibanez Guitar" width="32" /> |
| Acoustic Guitar *(validated)* | 4 (Guitar page (extended)) | 2 | `input_icon_402` | 23 | `acoustic-guitar` | <img src="mixer-icons/assets/flow8/input_icon_402.png" alt="Acoustic Guitar" width="32" /> |
| Bass Amp | 4 (Guitar page (extended)) | 3 | `input_icon_403` | 24 | `bass-amp` | <img src="mixer-icons/assets/flow8/input_icon_403.png" alt="Bass Amp" width="32" /> |
| Guitar Amp | 4 (Guitar page (extended)) | 4 | `input_icon_404` | 25 | `guitar-amp` | <img src="mixer-icons/assets/flow8/input_icon_404.png" alt="Guitar Amp" width="32" /> |
| Amp Cabinet | 4 (Guitar page (extended)) | 5 | `input_icon_405` | 26 | `amp-cabinet` | <img src="mixer-icons/assets/flow8/input_icon_405.png" alt="Amp Cabinet" width="32" /> |
| Electric Bass | 4 (Guitar page (extended)) | 6 | `input_icon_406` | 17 | `electric-bass` | <img src="mixer-icons/assets/flow8/input_icon_406.png" alt="Electric Bass" width="32" /> |
| Acoustic Bass | 4 (Guitar page (extended)) | 7 | `input_icon_407` | 18 | `acoustic-bass` | <img src="mixer-icons/assets/flow8/input_icon_407.png" alt="Acoustic Bass" width="32" /> |
| Reel to Reel | 5 (Playback / source) | 0 | `input_icon_500` | 60 | `tape` | <img src="mixer-icons/assets/flow8/input_icon_500.png" alt="Reel to Reel" width="32" /> |
| FX | 5 (Playback / source) | 1 | `input_icon_501` | 61 | `fx` | <img src="mixer-icons/assets/flow8/input_icon_501.png" alt="FX" width="32" /> |
| Computer | 5 (Playback / source) | 2 | `input_icon_502` | 62 | `computer` | <img src="mixer-icons/assets/flow8/input_icon_502.png" alt="Computer" width="32" /> |
| Monitor Wedge | 5 (Playback / source) | 3 | `input_icon_503` | 63 | `wedge` | <img src="mixer-icons/assets/flow8/input_icon_503.png" alt="Monitor Wedge" width="32" /> |
| Left Speaker | 5 (Playback / source) | 4 | `input_icon_504` | 64 | `speaker-right` | <img src="mixer-icons/assets/flow8/input_icon_504.png" alt="Left Speaker" width="32" /> |
| Right Speaker | 5 (Playback / source) | 5 | `input_icon_505` | 65 | `speaker-left` | <img src="mixer-icons/assets/flow8/input_icon_505.png" alt="Right Speaker" width="32" /> |
| Speaker Array | 5 (Playback / source) | 6 | `input_icon_506` | 66 | `speaker-array` | <img src="mixer-icons/assets/flow8/input_icon_506.png" alt="Speaker Array" width="32" /> |
| Record player *(validated)* | 5 (Playback / source) | 7 | `input_icon_507` | 60 | `tape` | <img src="mixer-icons/assets/flow8/input_icon_507.png" alt="Record player" width="32" /> |
| XLR Jack | 5 (Playback / source) | 8 | `input_icon_508` | 54 | `xlr` | <img src="mixer-icons/assets/flow8/input_icon_508.png" alt="XLR Jack" width="32" /> |
| TRS Plug | 5 (Playback / source) | 9 | `input_icon_509` | 55 | `trs` | <img src="mixer-icons/assets/flow8/input_icon_509.png" alt="TRS Plug" width="32" /> |
| Computer | 5 (Playback / source) | 10 | `input_icon_510` | 62 | `computer` | <img src="mixer-icons/assets/flow8/input_icon_510.png" alt="Computer" width="32" /> |
| Reel to Reel | 5 (Playback / source) | 11 | `input_icon_511` | 60 | `tape` | <img src="mixer-icons/assets/flow8/input_icon_511.png" alt="Reel to Reel" width="32" /> |

---

## Combined reference (by Mixing Station id)

One MS id may appear in several FLOW picker slots (e.g. multiple mic presets →
Handheld Mic). FLOW columns list every `(drawable → Flow label)` pair that
resolves to the id.

| MS display label | MS slug | MS ID | MS icon | FLOW drawables | FLOW icons |
| ---------------- | ------- | ----- | ------- | -------------- | ---------- |
| No icon | `blank` | 1 | <img src="mixer-icons/assets/mixing-station/1.svg" alt="No icon" width="32" /> | `input_icon_000` | <img src="mixer-icons/assets/flow8/input_icon_000.png" alt="No icon" width="32" /> |
| Kick Back | `kick-back` | 2 | <img src="mixer-icons/assets/mixing-station/2.svg" alt="Kick Back" width="32" /> | — | — |
| Kick Front | `kick-front` | 3 | <img src="mixer-icons/assets/mixing-station/3.svg" alt="Kick Front" width="32" /> | — | — |
| Snare Top | `snare-top` | 4 | <img src="mixer-icons/assets/mixing-station/4.svg" alt="Snare Top" width="32" /> | — | — |
| Snare Bottom | `snare-bottom` | 5 | <img src="mixer-icons/assets/mixing-station/5.svg" alt="Snare Bottom" width="32" /> | — | — |
| High Tom | `tom-high` | 6 | <img src="mixer-icons/assets/mixing-station/6.svg" alt="High Tom" width="32" /> | — | — |
| Mid Tom | `tom-medium` | 7 | <img src="mixer-icons/assets/mixing-station/7.svg" alt="Mid Tom" width="32" /> | — | — |
| Floor Tom | `floor-tom` | 8 | <img src="mixer-icons/assets/mixing-station/8.svg" alt="Floor Tom" width="32" /> | — | — |
| Hi-Hat | `hi-hat` | 9 | <img src="mixer-icons/assets/mixing-station/9.svg" alt="Hi-Hat" width="32" /> | — | — |
| Ride | `crash` | 10 | <img src="mixer-icons/assets/mixing-station/10.svg" alt="Ride" width="32" /> | — | — |
| Drum Kit | `drum-kit` | 11 | <img src="mixer-icons/assets/mixing-station/11.svg" alt="Drum Kit" width="32" /> | — | — |
| Cowbell | `cowbell` | 12 | <img src="mixer-icons/assets/mixing-station/12.svg" alt="Cowbell" width="32" /> | — | — |
| Bongos | `bongos` | 13 | <img src="mixer-icons/assets/mixing-station/13.svg" alt="Bongos" width="32" /> | `input_icon_312` | <img src="mixer-icons/assets/flow8/input_icon_312.png" alt="Bongos" width="32" /> |
| Congas | `congas` | 14 | <img src="mixer-icons/assets/mixing-station/14.svg" alt="Congas" width="32" /> | `input_icon_313` | <img src="mixer-icons/assets/flow8/input_icon_313.png" alt="Congas" width="32" /> |
| Tambourine | `tambourine` | 15 | <img src="mixer-icons/assets/mixing-station/15.svg" alt="Tambourine" width="32" /> | `input_icon_310` | <img src="mixer-icons/assets/flow8/input_icon_310.png" alt="Tambourine" width="32" /> |
| Vibraphone | `vibraphone` | 16 | <img src="mixer-icons/assets/mixing-station/16.svg" alt="Vibraphone" width="32" /> | `input_icon_311` | <img src="mixer-icons/assets/flow8/input_icon_311.png" alt="Vibraphone" width="32" /> |
| Electric Bass | `electric-bass` | 17 | <img src="mixer-icons/assets/mixing-station/17.svg" alt="Electric Bass" width="32" /> | `input_icon_200`, `input_icon_210`, `input_icon_406` | <img src="mixer-icons/assets/flow8/input_icon_200.png" alt="Electric Bass" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_210.png" alt="Electric Bass" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_406.png" alt="Electric Bass" width="32" /> |
| Acoustic Bass | `acoustic-bass` | 18 | <img src="mixer-icons/assets/mixing-station/18.svg" alt="Acoustic Bass" width="32" /> | `input_icon_201`, `input_icon_211`, `input_icon_407` | <img src="mixer-icons/assets/flow8/input_icon_201.png" alt="Acoustic Bass" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_211.png" alt="Acoustic Bass" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_407.png" alt="Acoustic Bass" width="32" /> |
| Contrabass | `contrabass` | 19 | <img src="mixer-icons/assets/mixing-station/19.svg" alt="Contrabass" width="32" /> | `input_icon_212` | <img src="mixer-icons/assets/flow8/input_icon_212.png" alt="Contrabass" width="32" /> |
| Les Paul Guitar | `les-paul` | 20 | <img src="mixer-icons/assets/mixing-station/20.svg" alt="Les Paul Guitar" width="32" /> | `input_icon_203`, `input_icon_213`, `input_icon_400` | <img src="mixer-icons/assets/flow8/input_icon_203.png" alt="Les Paul Guitar" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_213.png" alt="Les Paul Guitar" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_400.png" alt="Les Paul Guitar" width="32" /> |
| Ibanez Guitar | `ibanez` | 21 | <img src="mixer-icons/assets/mixing-station/21.svg" alt="Ibanez Guitar" width="32" /> | `input_icon_204`, `input_icon_214`, `input_icon_401` | <img src="mixer-icons/assets/flow8/input_icon_204.png" alt="Ibanez Guitar" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_214.png" alt="Ibanez Guitar" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_401.png" alt="Ibanez Guitar" width="32" /> |
| Washburn Guitar | `washburn` | 22 | <img src="mixer-icons/assets/mixing-station/22.svg" alt="Washburn Guitar" width="32" /> | `input_icon_205`, `input_icon_215` | <img src="mixer-icons/assets/flow8/input_icon_205.png" alt="Washburn Guitar" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_215.png" alt="Washburn Guitar" width="32" /> |
| Acoustic Guitar | `acoustic-guitar` | 23 | <img src="mixer-icons/assets/mixing-station/23.svg" alt="Acoustic Guitar" width="32" /> | `input_icon_202`, `input_icon_206`, `input_icon_216`, `input_icon_402` | <img src="mixer-icons/assets/flow8/input_icon_202.png" alt="Acoustic Guitar" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_206.png" alt="Acoustic Guitar" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_216.png" alt="Acoustic Guitar" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_402.png" alt="Acoustic Guitar" width="32" /> |
| Bass Amp | `bass-amp` | 24 | <img src="mixer-icons/assets/mixing-station/24.svg" alt="Bass Amp" width="32" /> | `input_icon_207`, `input_icon_403` | <img src="mixer-icons/assets/flow8/input_icon_207.png" alt="Bass Amp" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_403.png" alt="Bass Amp" width="32" /> |
| Guitar Amp | `guitar-amp` | 25 | <img src="mixer-icons/assets/mixing-station/25.svg" alt="Guitar Amp" width="32" /> | `input_icon_208`, `input_icon_404` | <img src="mixer-icons/assets/flow8/input_icon_208.png" alt="Guitar Amp" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_404.png" alt="Guitar Amp" width="32" /> |
| Amp Cabinet | `amp-cabinet` | 26 | <img src="mixer-icons/assets/mixing-station/26.svg" alt="Amp Cabinet" width="32" /> | `input_icon_209`, `input_icon_217`, `input_icon_405` | <img src="mixer-icons/assets/flow8/input_icon_209.png" alt="Amp Cabinet" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_217.png" alt="Amp Cabinet" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_405.png" alt="Amp Cabinet" width="32" /> |
| Piano | `piano` | 27 | <img src="mixer-icons/assets/mixing-station/27.svg" alt="Piano" width="32" /> | `input_icon_300` | <img src="mixer-icons/assets/flow8/input_icon_300.png" alt="Piano" width="32" /> |
| Organ | `organ` | 28 | <img src="mixer-icons/assets/mixing-station/28.svg" alt="Organ" width="32" /> | `input_icon_301` | <img src="mixer-icons/assets/flow8/input_icon_301.png" alt="Organ" width="32" /> |
| Harpsichord | `harpsichord` | 29 | <img src="mixer-icons/assets/mixing-station/29.svg" alt="Harpsichord" width="32" /> | `input_icon_302` | <img src="mixer-icons/assets/flow8/input_icon_302.png" alt="Harpsichord" width="32" /> |
| Keyboard | `keyboard` | 30 | <img src="mixer-icons/assets/mixing-station/30.svg" alt="Keyboard" width="32" /> | `input_icon_303` | <img src="mixer-icons/assets/flow8/input_icon_303.png" alt="Keyboard" width="32" /> |
| Synthesizer 1 | `synthesizer-1` | 31 | <img src="mixer-icons/assets/mixing-station/31.svg" alt="Synthesizer 1" width="32" /> | `input_icon_314` | <img src="mixer-icons/assets/flow8/input_icon_314.png" alt="Synthesizer 1" width="32" /> |
| Synthesizer 2 | `synthesizer-2` | 32 | <img src="mixer-icons/assets/mixing-station/32.svg" alt="Synthesizer 2" width="32" /> | `input_icon_315` | <img src="mixer-icons/assets/flow8/input_icon_315.png" alt="Synthesizer 2" width="32" /> |
| Synthesizer 3 | `synthesizer-3` | 33 | <img src="mixer-icons/assets/mixing-station/33.svg" alt="Synthesizer 3" width="32" /> | `input_icon_316` | <img src="mixer-icons/assets/flow8/input_icon_316.png" alt="Synthesizer 3" width="32" /> |
| Keytar | `keytar` | 34 | <img src="mixer-icons/assets/mixing-station/34.svg" alt="Keytar" width="32" /> | `input_icon_317` | <img src="mixer-icons/assets/flow8/input_icon_317.png" alt="Keytar" width="32" /> |
| Trumpet | `trumpet` | 35 | <img src="mixer-icons/assets/mixing-station/35.svg" alt="Trumpet" width="32" /> | `input_icon_305` | <img src="mixer-icons/assets/flow8/input_icon_305.png" alt="Trumpet" width="32" /> |
| Trombone | `trombone` | 36 | <img src="mixer-icons/assets/mixing-station/36.svg" alt="Trombone" width="32" /> | `input_icon_306` | <img src="mixer-icons/assets/flow8/input_icon_306.png" alt="Trombone" width="32" /> |
| Saxophone | `saxophone` | 37 | <img src="mixer-icons/assets/mixing-station/37.svg" alt="Saxophone" width="32" /> | `input_icon_307` | <img src="mixer-icons/assets/flow8/input_icon_307.png" alt="Saxophone" width="32" /> |
| Clarinet | `clarinet` | 38 | <img src="mixer-icons/assets/mixing-station/38.svg" alt="Clarinet" width="32" /> | `input_icon_308` | <img src="mixer-icons/assets/flow8/input_icon_308.png" alt="Clarinet" width="32" /> |
| Violin | `violin` | 39 | <img src="mixer-icons/assets/mixing-station/39.svg" alt="Violin" width="32" /> | `input_icon_304` | <img src="mixer-icons/assets/flow8/input_icon_304.png" alt="Violine" width="32" /> |
| Cello | `cello` | 40 | <img src="mixer-icons/assets/mixing-station/40.svg" alt="Cello" width="32" /> | `input_icon_309` | <img src="mixer-icons/assets/flow8/input_icon_309.png" alt="Cello" width="32" /> |
| Male Vocal | `male-vocal` | 41 | <img src="mixer-icons/assets/mixing-station/41.svg" alt="Male Vocal" width="32" /> | — | — |
| Female Vocal | `female-vocal` | 42 | <img src="mixer-icons/assets/mixing-station/42.svg" alt="Female Vocal" width="32" /> | — | — |
| Choir | `choir` | 43 | <img src="mixer-icons/assets/mixing-station/43.svg" alt="Choir" width="32" /> | — | — |
| Hand Sign | `hand-sign` | 44 | <img src="mixer-icons/assets/mixing-station/44.svg" alt="Hand Sign" width="32" /> | — | — |
| Talk A | `talk-a` | 45 | <img src="mixer-icons/assets/mixing-station/45.svg" alt="Talk A" width="32" /> | — | — |
| Talk B | `talk-b` | 46 | <img src="mixer-icons/assets/mixing-station/46.svg" alt="Talk B" width="32" /> | — | — |
| Large Diaphragm Mic | `large-diaphragm-mic` | 47 | <img src="mixer-icons/assets/mixing-station/47.svg" alt="Large Diaphragm Mic" width="32" /> | `input_icon_001`, `input_icon_100` | <img src="mixer-icons/assets/flow8/input_icon_001.png" alt="Large Diaphragm Mic" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_100.png" alt="Large Diaphragm Mic" width="32" /> |
| Condenser Mic Left | `condenser-mic-left` | 48 | <img src="mixer-icons/assets/mixing-station/48.svg" alt="Condenser Mic Left" width="32" /> | `input_icon_002`, `input_icon_101`, `input_icon_103` | <img src="mixer-icons/assets/flow8/input_icon_002.png" alt="Condenser Mic Left" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_101.png" alt="Condenser Mic Left" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_103.png" alt="Condenser Mic Left" width="32" /> |
| Condenser Mic Right | `condenser-mic-right` | 49 | <img src="mixer-icons/assets/mixing-station/49.svg" alt="Condenser Mic Right" width="32" /> | `input_icon_003`, `input_icon_102`, `input_icon_104` | <img src="mixer-icons/assets/flow8/input_icon_003.png" alt="Condenser Mic Right" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_102.png" alt="Condenser Mic Right" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_104.png" alt="Condenser Mic Right" width="32" /> |
| Handheld Mic | `handheld-mic` | 50 | <img src="mixer-icons/assets/mixing-station/50.svg" alt="Handheld Mic" width="32" /> | `input_icon_004`, `input_icon_005`, `input_icon_007` | <img src="mixer-icons/assets/flow8/input_icon_004.png" alt="Wired Mic" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_005.png" alt="Handheld Mic" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_007.png" alt="Wired Mic" width="32" /> |
| Wireless Mic | `wireless-mic` | 51 | <img src="mixer-icons/assets/mixing-station/51.svg" alt="Wireless Mic" width="32" /> | `input_icon_006` | <img src="mixer-icons/assets/flow8/input_icon_006.png" alt="Wireless Mic" width="32" /> |
| Podium Mic | `podium-mic` | 52 | <img src="mixer-icons/assets/mixing-station/52.svg" alt="Podium Mic" width="32" /> | — | — |
| Headset Mic | `headset-mic` | 53 | <img src="mixer-icons/assets/mixing-station/53.svg" alt="Headset Mic" width="32" /> | `input_icon_008`, `input_icon_105` | <img src="mixer-icons/assets/flow8/input_icon_008.png" alt="Headset Mic" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_105.png" alt="Headset Mic" width="32" /> |
| XLR Jack | `xlr` | 54 | <img src="mixer-icons/assets/mixing-station/54.svg" alt="XLR Jack" width="32" /> | `input_icon_009`, `input_icon_106`, `input_icon_508` | <img src="mixer-icons/assets/flow8/input_icon_009.png" alt="XLR Jack" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_106.png" alt="XLR Jack" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_508.png" alt="XLR Jack" width="32" /> |
| TRS Plug | `trs` | 55 | <img src="mixer-icons/assets/mixing-station/55.svg" alt="TRS Plug" width="32" /> | `input_icon_010`, `input_icon_107`, `input_icon_509` | <img src="mixer-icons/assets/flow8/input_icon_010.png" alt="TRS Plug" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_107.png" alt="TRS Plug" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_509.png" alt="TRS Plug" width="32" /> |
| TRS Plug Left | `trs-left` | 56 | <img src="mixer-icons/assets/mixing-station/56.svg" alt="TRS Plug Left" width="32" /> | `input_icon_011`, `input_icon_108` | <img src="mixer-icons/assets/flow8/input_icon_011.png" alt="TRS Plug Left" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_108.png" alt="TRS Plug Left" width="32" /> |
| TRS Plug Right | `trs-right` | 57 | <img src="mixer-icons/assets/mixing-station/57.svg" alt="TRS Plug Right" width="32" /> | `input_icon_012`, `input_icon_109` | <img src="mixer-icons/assets/flow8/input_icon_012.png" alt="TRS Plug Right" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_109.png" alt="TRS Plug Right" width="32" /> |
| RCA Plug Left | `rca-left` | 58 | <img src="mixer-icons/assets/mixing-station/58.svg" alt="RCA Plug Left" width="32" /> | `input_icon_013`, `input_icon_110` | <img src="mixer-icons/assets/flow8/input_icon_013.png" alt="RCA Plug Left" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_110.png" alt="RCA Plug Left" width="32" /> |
| RCA Plug Right | `rca-right` | 59 | <img src="mixer-icons/assets/mixing-station/59.svg" alt="RCA Plug Right" width="32" /> | `input_icon_014` | <img src="mixer-icons/assets/flow8/input_icon_014.png" alt="RCA Plug Right" width="32" /> |
| Reel to Reel | `tape` | 60 | <img src="mixer-icons/assets/mixing-station/60.svg" alt="Reel to Reel" width="32" /> | `input_icon_500`, `input_icon_507`, `input_icon_511` | <img src="mixer-icons/assets/flow8/input_icon_500.png" alt="Reel to Reel" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_507.png" alt="Record player" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_511.png" alt="Reel to Reel" width="32" /> |
| FX | `fx` | 61 | <img src="mixer-icons/assets/mixing-station/61.svg" alt="FX" width="32" /> | `input_icon_501` | <img src="mixer-icons/assets/flow8/input_icon_501.png" alt="FX" width="32" /> |
| Computer | `computer` | 62 | <img src="mixer-icons/assets/mixing-station/62.svg" alt="Computer" width="32" /> | `input_icon_502`, `input_icon_510` | <img src="mixer-icons/assets/flow8/input_icon_502.png" alt="Computer" width="32" /> <img src="mixer-icons/assets/flow8/input_icon_510.png" alt="Computer" width="32" /> |
| Monitor Wedge | `wedge` | 63 | <img src="mixer-icons/assets/mixing-station/63.svg" alt="Monitor Wedge" width="32" /> | `input_icon_503` | <img src="mixer-icons/assets/flow8/input_icon_503.png" alt="Monitor Wedge" width="32" /> |
| Left Speaker | `speaker-right` | 64 | <img src="mixer-icons/assets/mixing-station/64.svg" alt="Left Speaker" width="32" /> | `input_icon_504` | <img src="mixer-icons/assets/flow8/input_icon_504.png" alt="Left Speaker" width="32" /> |
| Right Speaker | `speaker-left` | 65 | <img src="mixer-icons/assets/mixing-station/65.svg" alt="Right Speaker" width="32" /> | `input_icon_505` | <img src="mixer-icons/assets/flow8/input_icon_505.png" alt="Right Speaker" width="32" /> |
| Speaker Array | `speaker-array` | 66 | <img src="mixer-icons/assets/mixing-station/66.svg" alt="Speaker Array" width="32" /> | `input_icon_506` | <img src="mixer-icons/assets/flow8/input_icon_506.png" alt="Speaker Array" width="32" /> |
| Speaker on a Pole | `speaker-on-pole` | 67 | <img src="mixer-icons/assets/mixing-station/67.svg" alt="Speaker on a Pole" width="32" /> | — | — |
| Amp Rack | `amp-rack` | 68 | <img src="mixer-icons/assets/mixing-station/68.svg" alt="Amp Rack" width="32" /> | — | — |
| Controls | `controls` | 69 | <img src="mixer-icons/assets/mixing-station/69.svg" alt="Controls" width="32" /> | — | — |
| Fader | `fader` | 70 | <img src="mixer-icons/assets/mixing-station/70.svg" alt="Fader" width="32" /> | — | — |
| MixBus | `mix-bus` | 71 | <img src="mixer-icons/assets/mixing-station/71.svg" alt="MixBus" width="32" /> | — | — |
| Matrix | `matrix` | 72 | <img src="mixer-icons/assets/mixing-station/72.svg" alt="Matrix" width="32" /> | — | — |
| Routing | `routing` | 73 | <img src="mixer-icons/assets/mixing-station/73.svg" alt="Routing" width="32" /> | — | — |
| Smiley | `smiley` | 74 | <img src="mixer-icons/assets/mixing-station/74.svg" alt="Smiley" width="32" /> | — | — |

---

## Related

- [flow8-reverse-engineering/06-channel-icons-and-stereo-link.md](flow8-reverse-engineering/06-channel-icons-and-stereo-link.md) — BLE/USB decode
- `mixer-behringer/.../MixingStationIcons.kt` — strip glyph rendering
- `mixer-behringer/.../Flow8IconPresets.kt` — `(input_type, preset)` tables
