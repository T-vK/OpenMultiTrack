# Mixer scribble icon reference

Channel scribble icons on Behringer X32 / X-Air consoles, **Mixing Station**, and
**FLOW 8** share a common numeric id space (**1–74**).

The full tables (with embedded icons) also live in
[flow8-reverse-engineering/06-channel-icons-and-stereo-link.md](flow8-reverse-engineering/06-channel-icons-and-stereo-link.md)
appendices A–D.

## Regenerating

```bash
cd docs/flow8-reverse-engineering/tools
python3 extract_icon_assets.py
python3 serve_flow8_mapper.py   # browser UI for FLOW → label mapping
python3 export_icon_tables.py all
```

---

## Appendix A: Mixing Station scribble icon IDs (1–74)

Resolved icon values on the wire and in `getChannelIconId` use this
X32 / X-Air / Mixing Station numbering. Icons below are the original X32 BMP
artwork (Patrick-Gilles Maillot / [behringer-icons](https://github.com/mamarguerat/behringer-icons)),
converted to PNG — the same pictures Mixing Station shows for scribble ids.

| Label | Slug | ID | Icon |
| ----- | ---- | -- | ---- |
| No icon | `blank` | 1 | ![No icon](mixer-icons/assets/mixing-station/1.png) |
| Kick Back | `kick-back` | 2 | ![Kick Back](mixer-icons/assets/mixing-station/2.png) |
| Kick Front | `kick-front` | 3 | ![Kick Front](mixer-icons/assets/mixing-station/3.png) |
| Snare Top | `snare-top` | 4 | ![Snare Top](mixer-icons/assets/mixing-station/4.png) |
| Snare Bottom | `snare-bottom` | 5 | ![Snare Bottom](mixer-icons/assets/mixing-station/5.png) |
| High Tom | `tom-high` | 6 | ![High Tom](mixer-icons/assets/mixing-station/6.png) |
| Mid Tom | `tom-medium` | 7 | ![Mid Tom](mixer-icons/assets/mixing-station/7.png) |
| Floor Tom | `floor-tom` | 8 | ![Floor Tom](mixer-icons/assets/mixing-station/8.png) |
| Hi-Hat | `hi-hat` | 9 | ![Hi-Hat](mixer-icons/assets/mixing-station/9.png) |
| Ride | `crash` | 10 | ![Ride](mixer-icons/assets/mixing-station/10.png) |
| Drum Kit | `drum-kit` | 11 | ![Drum Kit](mixer-icons/assets/mixing-station/11.png) |
| Cowbell | `cowbell` | 12 | ![Cowbell](mixer-icons/assets/mixing-station/12.png) |
| Bongos | `bongos` | 13 | ![Bongos](mixer-icons/assets/mixing-station/13.png) |
| Congas | `congas` | 14 | ![Congas](mixer-icons/assets/mixing-station/14.png) |
| Tambourine | `tambourine` | 15 | ![Tambourine](mixer-icons/assets/mixing-station/15.png) |
| Vibraphone | `vibraphone` | 16 | ![Vibraphone](mixer-icons/assets/mixing-station/16.png) |
| Electric Bass | `electric-bass` | 17 | ![Electric Bass](mixer-icons/assets/mixing-station/17.png) |
| Acoustic Bass | `acoustic-bass` | 18 | ![Acoustic Bass](mixer-icons/assets/mixing-station/18.png) |
| Contrabass | `contrabass` | 19 | ![Contrabass](mixer-icons/assets/mixing-station/19.png) |
| Les Paul Guitar | `les-paul` | 20 | ![Les Paul Guitar](mixer-icons/assets/mixing-station/20.png) |
| Ibanez Guitar | `ibanez` | 21 | ![Ibanez Guitar](mixer-icons/assets/mixing-station/21.png) |
| Washburn Guitar | `washburn` | 22 | ![Washburn Guitar](mixer-icons/assets/mixing-station/22.png) |
| Acoustic Guitar | `acoustic-guitar` | 23 | ![Acoustic Guitar](mixer-icons/assets/mixing-station/23.png) |
| Bass Amp | `bass-amp` | 24 | ![Bass Amp](mixer-icons/assets/mixing-station/24.png) |
| Guitar Amp | `guitar-amp` | 25 | ![Guitar Amp](mixer-icons/assets/mixing-station/25.png) |
| Amp Cabinet | `amp-cabinet` | 26 | ![Amp Cabinet](mixer-icons/assets/mixing-station/26.png) |
| Piano | `piano` | 27 | ![Piano](mixer-icons/assets/mixing-station/27.png) |
| Organ | `organ` | 28 | ![Organ](mixer-icons/assets/mixing-station/28.png) |
| Harpsichord | `harpsichord` | 29 | ![Harpsichord](mixer-icons/assets/mixing-station/29.png) |
| Keyboard | `keyboard` | 30 | ![Keyboard](mixer-icons/assets/mixing-station/30.png) |
| Synthesizer 1 | `synthesizer-1` | 31 | ![Synthesizer 1](mixer-icons/assets/mixing-station/31.png) |
| Synthesizer 2 | `synthesizer-2` | 32 | ![Synthesizer 2](mixer-icons/assets/mixing-station/32.png) |
| Synthesizer 3 | `synthesizer-3` | 33 | ![Synthesizer 3](mixer-icons/assets/mixing-station/33.png) |
| Keytar | `keytar` | 34 | ![Keytar](mixer-icons/assets/mixing-station/34.png) |
| Trumpet | `trumpet` | 35 | ![Trumpet](mixer-icons/assets/mixing-station/35.png) |
| Trombone | `trombone` | 36 | ![Trombone](mixer-icons/assets/mixing-station/36.png) |
| Saxophone | `saxophone` | 37 | ![Saxophone](mixer-icons/assets/mixing-station/37.png) |
| Clarinet | `clarinet` | 38 | ![Clarinet](mixer-icons/assets/mixing-station/38.png) |
| Violin | `violin` | 39 | ![Violin](mixer-icons/assets/mixing-station/39.png) |
| Cello | `cello` | 40 | ![Cello](mixer-icons/assets/mixing-station/40.png) |
| Male Vocal | `male-vocal` | 41 | ![Male Vocal](mixer-icons/assets/mixing-station/41.png) |
| Female Vocal | `female-vocal` | 42 | ![Female Vocal](mixer-icons/assets/mixing-station/42.png) |
| Choir | `choir` | 43 | ![Choir](mixer-icons/assets/mixing-station/43.png) |
| Hand Sign | `hand-sign` | 44 | ![Hand Sign](mixer-icons/assets/mixing-station/44.png) |
| Talk A | `talk-a` | 45 | ![Talk A](mixer-icons/assets/mixing-station/45.png) |
| Talk B | `talk-b` | 46 | ![Talk B](mixer-icons/assets/mixing-station/46.png) |
| Large Diaphragm Mic | `large-diaphragm-mic` | 47 | ![Large Diaphragm Mic](mixer-icons/assets/mixing-station/47.png) |
| Condenser Mic Left | `condenser-mic-left` | 48 | ![Condenser Mic Left](mixer-icons/assets/mixing-station/48.png) |
| Condenser Mic Right | `condenser-mic-right` | 49 | ![Condenser Mic Right](mixer-icons/assets/mixing-station/49.png) |
| Handheld Mic | `handheld-mic` | 50 | ![Handheld Mic](mixer-icons/assets/mixing-station/50.png) |
| Wireless Mic | `wireless-mic` | 51 | ![Wireless Mic](mixer-icons/assets/mixing-station/51.png) |
| Podium Mic | `podium-mic` | 52 | ![Podium Mic](mixer-icons/assets/mixing-station/52.png) |
| Headset Mic | `headset-mic` | 53 | ![Headset Mic](mixer-icons/assets/mixing-station/53.png) |
| XLR Jack | `xlr` | 54 | ![XLR Jack](mixer-icons/assets/mixing-station/54.png) |
| TRS Plug | `trs` | 55 | ![TRS Plug](mixer-icons/assets/mixing-station/55.png) |
| TRS Plug Left | `trs-left` | 56 | ![TRS Plug Left](mixer-icons/assets/mixing-station/56.png) |
| TRS Plug Right | `trs-right` | 57 | ![TRS Plug Right](mixer-icons/assets/mixing-station/57.png) |
| RCA Plug Left | `rca-left` | 58 | ![RCA Plug Left](mixer-icons/assets/mixing-station/58.png) |
| RCA Plug Right | `rca-right` | 59 | ![RCA Plug Right](mixer-icons/assets/mixing-station/59.png) |
| Reel to Reel | `tape` | 60 | ![Reel to Reel](mixer-icons/assets/mixing-station/60.png) |
| FX | `fx` | 61 | ![FX](mixer-icons/assets/mixing-station/61.png) |
| Computer | `computer` | 62 | ![Computer](mixer-icons/assets/mixing-station/62.png) |
| Monitor Wedge | `wedge` | 63 | ![Monitor Wedge](mixer-icons/assets/mixing-station/63.png) |
| Left Speaker | `speaker-right` | 64 | ![Left Speaker](mixer-icons/assets/mixing-station/64.png) |
| Right Speaker | `speaker-left` | 65 | ![Right Speaker](mixer-icons/assets/mixing-station/65.png) |
| Speaker Array | `speaker-array` | 66 | ![Speaker Array](mixer-icons/assets/mixing-station/66.png) |
| Speaker on a Pole | `speaker-on-pole` | 67 | ![Speaker on a Pole](mixer-icons/assets/mixing-station/67.png) |
| Amp Rack | `amp-rack` | 68 | ![Amp Rack](mixer-icons/assets/mixing-station/68.png) |
| Controls | `controls` | 69 | ![Controls](mixer-icons/assets/mixing-station/69.png) |
| Fader | `fader` | 70 | ![Fader](mixer-icons/assets/mixing-station/70.png) |
| MixBus | `mix-bus` | 71 | ![MixBus](mixer-icons/assets/mixing-station/71.png) |
| Matrix | `matrix` | 72 | ![Matrix](mixer-icons/assets/mixing-station/72.png) |
| Routing | `routing` | 73 | ![Routing](mixer-icons/assets/mixing-station/73.png) |
| Smiley | `smiley` | 74 | ![Smiley](mixer-icons/assets/mixing-station/74.png) |

## Appendix B: FLOW 8 picker icons

Drawable assets from `Flowmix_v1.9.apk` (`res/drawable-*/input_icon_NNN`).
Labels marked *(validated)* were read from the Flow Mix UI on hardware (firmware v11749);
others come from `flow8_icon_mapping.json` (run `serve_flow8_mapper.py` to edit).
MS ID is set when the label matches Mixing Station ids 1–74; FLOW-only labels
(DCA, clefs, …) have no MS id. Type 6 drawables `input_icon_600`…`617` are the
last 18 picker slots.

| Label | Input type | Preset | Drawable | MS ID | MS slug | Icon |
| ----- | ---------- | ------ | -------- | ----- | ------- | ---- |
| No icon | 0 (Dynamic mic) | 0 | `input_icon_000` | 1 | `blank` | ![No icon](mixer-icons/assets/flow8/input_icon_000.png) |
| DCA | 0 (Dynamic mic) | 1 | `input_icon_001` | — | — | ![DCA](mixer-icons/assets/flow8/input_icon_001.png) |
| FX | 0 (Dynamic mic) | 2 | `input_icon_002` | 61 | `fx` | ![FX](mixer-icons/assets/flow8/input_icon_002.png) |
| Groups | 0 (Dynamic mic) | 3 | `input_icon_003` | — | — | ![Groups](mixer-icons/assets/flow8/input_icon_003.png) |
| Wired Mic *(validated)* | 0 (Dynamic mic) | 4 | `input_icon_004` | — | — | ![Wired Mic](mixer-icons/assets/flow8/input_icon_004.png) |
| XLR Female | 0 (Dynamic mic) | 5 | `input_icon_005` | — | — | ![XLR Female](mixer-icons/assets/flow8/input_icon_005.png) |
| DIN 5-pin MIDI | 0 (Dynamic mic) | 6 | `input_icon_006` | — | — | ![DIN 5-pin MIDI](mixer-icons/assets/flow8/input_icon_006.png) |
| Wired Mic *(validated)* | 0 (Dynamic mic) | 7 | `input_icon_007` | — | — | ![Wired Mic](mixer-icons/assets/flow8/input_icon_007.png) |
| TS Jack Female | 0 (Dynamic mic) | 8 | `input_icon_008` | — | — | ![TS Jack Female](mixer-icons/assets/flow8/input_icon_008.png) |
| Bass clef | 0 (Dynamic mic) | 9 | `input_icon_009` | — | — | ![Bass clef](mixer-icons/assets/flow8/input_icon_009.png) |
| Treble clef | 0 (Dynamic mic) | 10 | `input_icon_010` | — | — | ![Treble clef](mixer-icons/assets/flow8/input_icon_010.png) |
| Matrix | 0 (Dynamic mic) | 11 | `input_icon_011` | 72 | `matrix` | ![Matrix](mixer-icons/assets/flow8/input_icon_011.png) |
| Routing | 0 (Dynamic mic) | 12 | `input_icon_012` | 73 | `routing` | ![Routing](mixer-icons/assets/flow8/input_icon_012.png) |
| Fader | 0 (Dynamic mic) | 13 | `input_icon_013` | 70 | `fader` | ![Fader](mixer-icons/assets/flow8/input_icon_013.png) |
| Smiley | 0 (Dynamic mic) | 14 | `input_icon_014` | 74 | `smiley` | ![Smiley](mixer-icons/assets/flow8/input_icon_014.png) |
| Large Diaphragm Mic | 1 (Condenser mic) | 0 | `input_icon_100` | 47 | `large-diaphragm-mic` | ![Large Diaphragm Mic](mixer-icons/assets/flow8/input_icon_100.png) |
| Condenser Mic Left | 1 (Condenser mic) | 1 | `input_icon_101` | 48 | `condenser-mic-left` | ![Condenser Mic Left](mixer-icons/assets/flow8/input_icon_101.png) |
| Condenser Mic Right | 1 (Condenser mic) | 2 | `input_icon_102` | 49 | `condenser-mic-right` | ![Condenser Mic Right](mixer-icons/assets/flow8/input_icon_102.png) |
| Podium Mic | 1 (Condenser mic) | 3 | `input_icon_103` | 52 | `podium-mic` | ![Podium Mic](mixer-icons/assets/flow8/input_icon_103.png) |
| Turntable | 1 (Condenser mic) | 4 | `input_icon_104` | — | — | ![Turntable](mixer-icons/assets/flow8/input_icon_104.png) |
| Wireless Mic | 1 (Condenser mic) | 5 | `input_icon_105` | 51 | `wireless-mic` | ![Wireless Mic](mixer-icons/assets/flow8/input_icon_105.png) |
| Handheld Mic | 1 (Condenser mic) | 6 | `input_icon_106` | 50 | `handheld-mic` | ![Handheld Mic](mixer-icons/assets/flow8/input_icon_106.png) |
| Headset Mic | 1 (Condenser mic) | 7 | `input_icon_107` | 53 | `headset-mic` | ![Headset Mic](mixer-icons/assets/flow8/input_icon_107.png) |
| Choir | 1 (Condenser mic) | 8 | `input_icon_108` | 43 | `choir` | ![Choir](mixer-icons/assets/flow8/input_icon_108.png) |
| Female Vocal | 1 (Condenser mic) | 9 | `input_icon_109` | 42 | `female-vocal` | ![Female Vocal](mixer-icons/assets/flow8/input_icon_109.png) |
| Male Vocal | 1 (Condenser mic) | 10 | `input_icon_110` | 41 | `male-vocal` | ![Male Vocal](mixer-icons/assets/flow8/input_icon_110.png) |
| Kick left | 2 (Guitar / bass) | 0 | `input_icon_200` | — | — | ![Kick left](mixer-icons/assets/flow8/input_icon_200.png) |
| Kick right | 2 (Guitar / bass) | 1 | `input_icon_201` | — | — | ![Kick right](mixer-icons/assets/flow8/input_icon_201.png) |
| Acoustic Guitar *(validated)* | 2 (Guitar / bass) | 2 | `input_icon_202` | — | — | ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_202.png) |
| Ride | 2 (Guitar / bass) | 3 | `input_icon_203` | 10 | `crash` | ![Ride](mixer-icons/assets/flow8/input_icon_203.png) |
| Snare Top | 2 (Guitar / bass) | 4 | `input_icon_204` | 4 | `snare-top` | ![Snare Top](mixer-icons/assets/flow8/input_icon_204.png) |
| Snare Bottom | 2 (Guitar / bass) | 5 | `input_icon_205` | 5 | `snare-bottom` | ![Snare Bottom](mixer-icons/assets/flow8/input_icon_205.png) |
| Hi-Hat | 2 (Guitar / bass) | 6 | `input_icon_206` | 9 | `hi-hat` | ![Hi-Hat](mixer-icons/assets/flow8/input_icon_206.png) |
| Drum Kit | 2 (Guitar / bass) | 7 | `input_icon_207` | 11 | `drum-kit` | ![Drum Kit](mixer-icons/assets/flow8/input_icon_207.png) |
| Drum kit left | 2 (Guitar / bass) | 8 | `input_icon_208` | — | — | ![Drum kit left](mixer-icons/assets/flow8/input_icon_208.png) |
| Drum kit right | 2 (Guitar / bass) | 9 | `input_icon_209` | — | — | ![Drum kit right](mixer-icons/assets/flow8/input_icon_209.png) |
| High Tom | 2 (Guitar / bass) | 10 | `input_icon_210` | 6 | `tom-high` | ![High Tom](mixer-icons/assets/flow8/input_icon_210.png) |
| Mid Tom | 2 (Guitar / bass) | 11 | `input_icon_211` | 7 | `tom-medium` | ![Mid Tom](mixer-icons/assets/flow8/input_icon_211.png) |
| Floor Tom | 2 (Guitar / bass) | 12 | `input_icon_212` | 8 | `floor-tom` | ![Floor Tom](mixer-icons/assets/flow8/input_icon_212.png) |
| Bongos | 2 (Guitar / bass) | 13 | `input_icon_213` | 13 | `bongos` | ![Bongos](mixer-icons/assets/flow8/input_icon_213.png) |
| Congas | 2 (Guitar / bass) | 14 | `input_icon_214` | 14 | `congas` | ![Congas](mixer-icons/assets/flow8/input_icon_214.png) |
| Cowbell | 2 (Guitar / bass) | 15 | `input_icon_215` | 12 | `cowbell` | ![Cowbell](mixer-icons/assets/flow8/input_icon_215.png) |
| Tambourine | 2 (Guitar / bass) | 16 | `input_icon_216` | 15 | `tambourine` | ![Tambourine](mixer-icons/assets/flow8/input_icon_216.png) |
| Vibraphone | 2 (Guitar / bass) | 17 | `input_icon_217` | 16 | `vibraphone` | ![Vibraphone](mixer-icons/assets/flow8/input_icon_217.png) |
| Washburn Guitar | 3 (Line instrument) | 0 | `input_icon_300` | 22 | `washburn` | ![Washburn Guitar](mixer-icons/assets/flow8/input_icon_300.png) |
| Hollow body electric guitar | 3 (Line instrument) | 1 | `input_icon_301` | — | — | ![Hollow body electric guitar](mixer-icons/assets/flow8/input_icon_301.png) |
| Double bass without bow | 3 (Line instrument) | 2 | `input_icon_302` | — | — | ![Double bass without bow](mixer-icons/assets/flow8/input_icon_302.png) |
| Mandoline | 3 (Line instrument) | 3 | `input_icon_303` | — | — | ![Mandoline](mixer-icons/assets/flow8/input_icon_303.png) |
| Violine *(validated)* | 3 (Line instrument) | 4 | `input_icon_304` | 23 | `acoustic-guitar` | ![Violine](mixer-icons/assets/flow8/input_icon_304.png) |
| Les Paul Guitar | 3 (Line instrument) | 5 | `input_icon_305` | 20 | `les-paul` | ![Les Paul Guitar](mixer-icons/assets/flow8/input_icon_305.png) |
| Ibanez Guitar | 3 (Line instrument) | 6 | `input_icon_306` | 21 | `ibanez` | ![Ibanez Guitar](mixer-icons/assets/flow8/input_icon_306.png) |
| V shape guitar | 3 (Line instrument) | 7 | `input_icon_307` | — | — | ![V shape guitar](mixer-icons/assets/flow8/input_icon_307.png) |
| Violin | 3 (Line instrument) | 8 | `input_icon_308` | 39 | `violin` | ![Violin](mixer-icons/assets/flow8/input_icon_308.png) |
| Electric violine without bow | 3 (Line instrument) | 9 | `input_icon_309` | — | — | ![Electric violine without bow](mixer-icons/assets/flow8/input_icon_309.png) |
| Double bass with bow | 3 (Line instrument) | 10 | `input_icon_310` | — | — | ![Double bass with bow](mixer-icons/assets/flow8/input_icon_310.png) |
| Clarinet | 3 (Line instrument) | 11 | `input_icon_311` | 38 | `clarinet` | ![Clarinet](mixer-icons/assets/flow8/input_icon_311.png) |
| Saxophone | 3 (Line instrument) | 12 | `input_icon_312` | 37 | `saxophone` | ![Saxophone](mixer-icons/assets/flow8/input_icon_312.png) |
| Trombone | 3 (Line instrument) | 13 | `input_icon_313` | 36 | `trombone` | ![Trombone](mixer-icons/assets/flow8/input_icon_313.png) |
| Trumpet | 3 (Line instrument) | 14 | `input_icon_314` | 35 | `trumpet` | ![Trumpet](mixer-icons/assets/flow8/input_icon_314.png) |
| Harpsichord | 3 (Line instrument) | 15 | `input_icon_315` | 29 | `harpsichord` | ![Harpsichord](mixer-icons/assets/flow8/input_icon_315.png) |
| Harmonica | 3 (Line instrument) | 16 | `input_icon_316` | — | — | ![Harmonica](mixer-icons/assets/flow8/input_icon_316.png) |
| Accordeon | 3 (Line instrument) | 17 | `input_icon_317` | — | — | ![Accordeon](mixer-icons/assets/flow8/input_icon_317.png) |
| Grand piano | 4 (Guitar page (extended)) | 0 | `input_icon_400` | — | — | ![Grand piano](mixer-icons/assets/flow8/input_icon_400.png) |
| Upright piano | 4 (Guitar page (extended)) | 1 | `input_icon_401` | — | — | ![Upright piano](mixer-icons/assets/flow8/input_icon_401.png) |
| Acoustic Guitar *(validated)* | 4 (Guitar page (extended)) | 2 | `input_icon_402` | 31 | `synthesizer-1` | ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_402.png) |
| Synthesizer 2 | 4 (Guitar page (extended)) | 3 | `input_icon_403` | 32 | `synthesizer-2` | ![Synthesizer 2](mixer-icons/assets/flow8/input_icon_403.png) |
| Synthesizer 3 | 4 (Guitar page (extended)) | 4 | `input_icon_404` | 33 | `synthesizer-3` | ![Synthesizer 3](mixer-icons/assets/flow8/input_icon_404.png) |
| Synthesizer 4 | 4 (Guitar page (extended)) | 5 | `input_icon_405` | — | — | ![Synthesizer 4](mixer-icons/assets/flow8/input_icon_405.png) |
| Keytar | 4 (Guitar page (extended)) | 6 | `input_icon_406` | 34 | `keytar` | ![Keytar](mixer-icons/assets/flow8/input_icon_406.png) |
| Keyboard | 4 (Guitar page (extended)) | 7 | `input_icon_407` | 30 | `keyboard` | ![Keyboard](mixer-icons/assets/flow8/input_icon_407.png) |
| Guitar Amp | 5 (Playback / source) | 0 | `input_icon_500` | 25 | `guitar-amp` | ![Guitar Amp](mixer-icons/assets/flow8/input_icon_500.png) |
| Amp Cabinet | 5 (Playback / source) | 1 | `input_icon_501` | 26 | `amp-cabinet` | ![Amp Cabinet](mixer-icons/assets/flow8/input_icon_501.png) |
| Bass Amp | 5 (Playback / source) | 2 | `input_icon_502` | 24 | `bass-amp` | ![Bass Amp](mixer-icons/assets/flow8/input_icon_502.png) |
| Speakers | 5 (Playback / source) | 3 | `input_icon_503` | — | — | ![Speakers](mixer-icons/assets/flow8/input_icon_503.png) |
| Speaker Array | 5 (Playback / source) | 4 | `input_icon_504` | 66 | `speaker-array` | ![Speaker Array](mixer-icons/assets/flow8/input_icon_504.png) |
| Speaker | 5 (Playback / source) | 5 | `input_icon_505` | — | — | ![Speaker](mixer-icons/assets/flow8/input_icon_505.png) |
| Speaker (ceiling mount) | 5 (Playback / source) | 6 | `input_icon_506` | — | — | ![Speaker (ceiling mount)](mixer-icons/assets/flow8/input_icon_506.png) |
| Record player *(validated)* | 5 (Playback / source) | 7 | `input_icon_507` | — | — | ![Record player](mixer-icons/assets/flow8/input_icon_507.png) |
| Speaker on a Pole | 5 (Playback / source) | 8 | `input_icon_508` | 67 | `speaker-on-pole` | ![Speaker on a Pole](mixer-icons/assets/flow8/input_icon_508.png) |
| Monitor Wedge | 5 (Playback / source) | 9 | `input_icon_509` | 63 | `wedge` | ![Monitor Wedge](mixer-icons/assets/flow8/input_icon_509.png) |
| Left Speaker | 5 (Playback / source) | 10 | `input_icon_510` | 64 | `speaker-right` | ![Left Speaker](mixer-icons/assets/flow8/input_icon_510.png) |
| Right Speaker | 5 (Playback / source) | 11 | `input_icon_511` | 65 | `speaker-left` | ![Right Speaker](mixer-icons/assets/flow8/input_icon_511.png) |
| Hand Sign | 6 (Music / routing) | 0 | `input_icon_600` | 44 | `hand-sign` | ![Hand Sign](mixer-icons/assets/flow8/input_icon_600.png) |
| TRS Plug Left | 6 (Music / routing) | 1 | `input_icon_601` | 56 | `trs-left` | ![TRS Plug Left](mixer-icons/assets/flow8/input_icon_601.png) |
| TRS Plug Right | 6 (Music / routing) | 2 | `input_icon_602` | 57 | `trs-right` | ![TRS Plug Right](mixer-icons/assets/flow8/input_icon_602.png) |
| TS Plug Left | 6 (Music / routing) | 3 | `input_icon_603` | — | — | ![TS Plug Left](mixer-icons/assets/flow8/input_icon_603.png) |
| TS Plug Right | 6 (Music / routing) | 4 | `input_icon_604` | — | — | ![TS Plug Right](mixer-icons/assets/flow8/input_icon_604.png) |
| In ear monitor | 6 (Music / routing) | 5 | `input_icon_605` | — | — | ![In ear monitor](mixer-icons/assets/flow8/input_icon_605.png) |
| Headphones | 6 (Music / routing) | 6 | `input_icon_606` | — | — | ![Headphones](mixer-icons/assets/flow8/input_icon_606.png) |
| Amp Rack | 6 (Music / routing) | 7 | `input_icon_607` | 68 | `amp-rack` | ![Amp Rack](mixer-icons/assets/flow8/input_icon_607.png) |
| Computer | 6 (Music / routing) | 8 | `input_icon_608` | 62 | `computer` | ![Computer](mixer-icons/assets/flow8/input_icon_608.png) |
| Media player | 6 (Music / routing) | 9 | `input_icon_609` | — | — | ![Media player](mixer-icons/assets/flow8/input_icon_609.png) |
| Smartphone | 6 (Music / routing) | 10 | `input_icon_610` | — | — | ![Smartphone](mixer-icons/assets/flow8/input_icon_610.png) |
| Tablet (landscape) | 6 (Music / routing) | 11 | `input_icon_611` | — | — | ![Tablet (landscape)](mixer-icons/assets/flow8/input_icon_611.png) |
| Reel to Reel | 6 (Music / routing) | 12 | `input_icon_612` | 60 | `tape` | ![Reel to Reel](mixer-icons/assets/flow8/input_icon_612.png) |
| Talk A | 6 (Music / routing) | 13 | `input_icon_613` | 45 | `talk-a` | ![Talk A](mixer-icons/assets/flow8/input_icon_613.png) |
| Talk B | 6 (Music / routing) | 14 | `input_icon_614` | 46 | `talk-b` | ![Talk B](mixer-icons/assets/flow8/input_icon_614.png) |
| Vinyl record | 6 (Music / routing) | 15 | `input_icon_615` | — | — | ![Vinyl record](mixer-icons/assets/flow8/input_icon_615.png) |
| CD | 6 (Music / routing) | 16 | `input_icon_616` | — | — | ![CD](mixer-icons/assets/flow8/input_icon_616.png) |
| Cassette | 6 (Music / routing) | 17 | `input_icon_617` | — | — | ![Cassette](mixer-icons/assets/flow8/input_icon_617.png) |

## Appendix C: Hardware-validated preset → icon mapping

Firmware **v11749**, capture 2026-06-08. Other `(input_type, preset)` pairs must
be resolved via `getInputChannelPresetIconIdAtIndex` in the native library.

| Input type | Preset | Flow drawable | Flow UI label | MS ID | MS label |
| ---------- | ------ | ------------- | ------------- | ----- | -------- |
| 0 (Dynamic mic) | 4 | `input_icon_004` | Wired Mic | 50 | `handheld-mic` |
| 0 (Dynamic mic) | 7 | `input_icon_007` | Wired Mic | 50 | `handheld-mic` |
| 2 (Guitar / bass) | 2 | `input_icon_202` | Acoustic Guitar | 23 | `acoustic-guitar` |
| 3 (Line instrument) | 4 | `input_icon_304` | Violine | 39 | `violin` |
| 4 (Guitar page (extended)) | 2 | `input_icon_402` | Acoustic Guitar | 23 | `acoustic-guitar` |
| 5 (Playback / source) | 7 | `input_icon_507` | Record player | 60 | `tape` |

Drawable key formula: `type × 100 + preset`, zero-padded to three digits
(`input_icon_{key:03d}`).

*Maintained in `tools/flow8_icon_catalog.py` (`VALIDATED_MS_IDS`, `FLOW_UI_LABELS`).*

## Appendix D: Combined reference (by Mixing Station id)

Cross-reference of Mixing Station ids with every FLOW picker slot that resolves to
the same id. **Icon (MS)** uses the X32 BMP artwork; **Icon (FLOW)** is from the
Flow Mix APK drawable named in the FLOW drawable column.

| Label (MS) | MS slug | MS ID | Icon (MS) | FLOW drawable(s) | Icon (FLOW) |
| ---------- | ------- | ----- | --------- | ---------------- | ----------- |
| No icon | `blank` | 1 | ![No icon](mixer-icons/assets/mixing-station/1.png) | `input_icon_000` | ![No icon](mixer-icons/assets/flow8/input_icon_000.png) |
| Kick Back | `kick-back` | 2 | ![Kick Back](mixer-icons/assets/mixing-station/2.png) | — | — |
| Kick Front | `kick-front` | 3 | ![Kick Front](mixer-icons/assets/mixing-station/3.png) | — | — |
| Snare Top | `snare-top` | 4 | ![Snare Top](mixer-icons/assets/mixing-station/4.png) | `input_icon_204` | ![Snare Top](mixer-icons/assets/flow8/input_icon_204.png) |
| Snare Bottom | `snare-bottom` | 5 | ![Snare Bottom](mixer-icons/assets/mixing-station/5.png) | `input_icon_205` | ![Snare Bottom](mixer-icons/assets/flow8/input_icon_205.png) |
| High Tom | `tom-high` | 6 | ![High Tom](mixer-icons/assets/mixing-station/6.png) | `input_icon_210` | ![High Tom](mixer-icons/assets/flow8/input_icon_210.png) |
| Mid Tom | `tom-medium` | 7 | ![Mid Tom](mixer-icons/assets/mixing-station/7.png) | `input_icon_211` | ![Mid Tom](mixer-icons/assets/flow8/input_icon_211.png) |
| Floor Tom | `floor-tom` | 8 | ![Floor Tom](mixer-icons/assets/mixing-station/8.png) | `input_icon_212` | ![Floor Tom](mixer-icons/assets/flow8/input_icon_212.png) |
| Hi-Hat | `hi-hat` | 9 | ![Hi-Hat](mixer-icons/assets/mixing-station/9.png) | `input_icon_206` | ![Hi-Hat](mixer-icons/assets/flow8/input_icon_206.png) |
| Ride | `crash` | 10 | ![Ride](mixer-icons/assets/mixing-station/10.png) | `input_icon_203` | ![Ride](mixer-icons/assets/flow8/input_icon_203.png) |
| Drum Kit | `drum-kit` | 11 | ![Drum Kit](mixer-icons/assets/mixing-station/11.png) | `input_icon_207` | ![Drum Kit](mixer-icons/assets/flow8/input_icon_207.png) |
| Cowbell | `cowbell` | 12 | ![Cowbell](mixer-icons/assets/mixing-station/12.png) | `input_icon_215` | ![Cowbell](mixer-icons/assets/flow8/input_icon_215.png) |
| Bongos | `bongos` | 13 | ![Bongos](mixer-icons/assets/mixing-station/13.png) | `input_icon_213` | ![Bongos](mixer-icons/assets/flow8/input_icon_213.png) |
| Congas | `congas` | 14 | ![Congas](mixer-icons/assets/mixing-station/14.png) | `input_icon_214` | ![Congas](mixer-icons/assets/flow8/input_icon_214.png) |
| Tambourine | `tambourine` | 15 | ![Tambourine](mixer-icons/assets/mixing-station/15.png) | `input_icon_216` | ![Tambourine](mixer-icons/assets/flow8/input_icon_216.png) |
| Vibraphone | `vibraphone` | 16 | ![Vibraphone](mixer-icons/assets/mixing-station/16.png) | `input_icon_217` | ![Vibraphone](mixer-icons/assets/flow8/input_icon_217.png) |
| Electric Bass | `electric-bass` | 17 | ![Electric Bass](mixer-icons/assets/mixing-station/17.png) | — | — |
| Acoustic Bass | `acoustic-bass` | 18 | ![Acoustic Bass](mixer-icons/assets/mixing-station/18.png) | — | — |
| Contrabass | `contrabass` | 19 | ![Contrabass](mixer-icons/assets/mixing-station/19.png) | — | — |
| Les Paul Guitar | `les-paul` | 20 | ![Les Paul Guitar](mixer-icons/assets/mixing-station/20.png) | `input_icon_305` | ![Les Paul Guitar](mixer-icons/assets/flow8/input_icon_305.png) |
| Ibanez Guitar | `ibanez` | 21 | ![Ibanez Guitar](mixer-icons/assets/mixing-station/21.png) | `input_icon_306` | ![Ibanez Guitar](mixer-icons/assets/flow8/input_icon_306.png) |
| Washburn Guitar | `washburn` | 22 | ![Washburn Guitar](mixer-icons/assets/mixing-station/22.png) | `input_icon_300` | ![Washburn Guitar](mixer-icons/assets/flow8/input_icon_300.png) |
| Acoustic Guitar | `acoustic-guitar` | 23 | ![Acoustic Guitar](mixer-icons/assets/mixing-station/23.png) | `input_icon_304` | ![Violine](mixer-icons/assets/flow8/input_icon_304.png) |
| Bass Amp | `bass-amp` | 24 | ![Bass Amp](mixer-icons/assets/mixing-station/24.png) | `input_icon_502` | ![Bass Amp](mixer-icons/assets/flow8/input_icon_502.png) |
| Guitar Amp | `guitar-amp` | 25 | ![Guitar Amp](mixer-icons/assets/mixing-station/25.png) | `input_icon_500` | ![Guitar Amp](mixer-icons/assets/flow8/input_icon_500.png) |
| Amp Cabinet | `amp-cabinet` | 26 | ![Amp Cabinet](mixer-icons/assets/mixing-station/26.png) | `input_icon_501` | ![Amp Cabinet](mixer-icons/assets/flow8/input_icon_501.png) |
| Piano | `piano` | 27 | ![Piano](mixer-icons/assets/mixing-station/27.png) | — | — |
| Organ | `organ` | 28 | ![Organ](mixer-icons/assets/mixing-station/28.png) | — | — |
| Harpsichord | `harpsichord` | 29 | ![Harpsichord](mixer-icons/assets/mixing-station/29.png) | `input_icon_315` | ![Harpsichord](mixer-icons/assets/flow8/input_icon_315.png) |
| Keyboard | `keyboard` | 30 | ![Keyboard](mixer-icons/assets/mixing-station/30.png) | `input_icon_407` | ![Keyboard](mixer-icons/assets/flow8/input_icon_407.png) |
| Synthesizer 1 | `synthesizer-1` | 31 | ![Synthesizer 1](mixer-icons/assets/mixing-station/31.png) | `input_icon_402` | ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_402.png) |
| Synthesizer 2 | `synthesizer-2` | 32 | ![Synthesizer 2](mixer-icons/assets/mixing-station/32.png) | `input_icon_403` | ![Synthesizer 2](mixer-icons/assets/flow8/input_icon_403.png) |
| Synthesizer 3 | `synthesizer-3` | 33 | ![Synthesizer 3](mixer-icons/assets/mixing-station/33.png) | `input_icon_404` | ![Synthesizer 3](mixer-icons/assets/flow8/input_icon_404.png) |
| Keytar | `keytar` | 34 | ![Keytar](mixer-icons/assets/mixing-station/34.png) | `input_icon_406` | ![Keytar](mixer-icons/assets/flow8/input_icon_406.png) |
| Trumpet | `trumpet` | 35 | ![Trumpet](mixer-icons/assets/mixing-station/35.png) | `input_icon_314` | ![Trumpet](mixer-icons/assets/flow8/input_icon_314.png) |
| Trombone | `trombone` | 36 | ![Trombone](mixer-icons/assets/mixing-station/36.png) | `input_icon_313` | ![Trombone](mixer-icons/assets/flow8/input_icon_313.png) |
| Saxophone | `saxophone` | 37 | ![Saxophone](mixer-icons/assets/mixing-station/37.png) | `input_icon_312` | ![Saxophone](mixer-icons/assets/flow8/input_icon_312.png) |
| Clarinet | `clarinet` | 38 | ![Clarinet](mixer-icons/assets/mixing-station/38.png) | `input_icon_311` | ![Clarinet](mixer-icons/assets/flow8/input_icon_311.png) |
| Violin | `violin` | 39 | ![Violin](mixer-icons/assets/mixing-station/39.png) | `input_icon_308` | ![Violin](mixer-icons/assets/flow8/input_icon_308.png) |
| Cello | `cello` | 40 | ![Cello](mixer-icons/assets/mixing-station/40.png) | — | — |
| Male Vocal | `male-vocal` | 41 | ![Male Vocal](mixer-icons/assets/mixing-station/41.png) | `input_icon_110` | ![Male Vocal](mixer-icons/assets/flow8/input_icon_110.png) |
| Female Vocal | `female-vocal` | 42 | ![Female Vocal](mixer-icons/assets/mixing-station/42.png) | `input_icon_109` | ![Female Vocal](mixer-icons/assets/flow8/input_icon_109.png) |
| Choir | `choir` | 43 | ![Choir](mixer-icons/assets/mixing-station/43.png) | `input_icon_108` | ![Choir](mixer-icons/assets/flow8/input_icon_108.png) |
| Hand Sign | `hand-sign` | 44 | ![Hand Sign](mixer-icons/assets/mixing-station/44.png) | `input_icon_600` | ![Hand Sign](mixer-icons/assets/flow8/input_icon_600.png) |
| Talk A | `talk-a` | 45 | ![Talk A](mixer-icons/assets/mixing-station/45.png) | `input_icon_613` | ![Talk A](mixer-icons/assets/flow8/input_icon_613.png) |
| Talk B | `talk-b` | 46 | ![Talk B](mixer-icons/assets/mixing-station/46.png) | `input_icon_614` | ![Talk B](mixer-icons/assets/flow8/input_icon_614.png) |
| Large Diaphragm Mic | `large-diaphragm-mic` | 47 | ![Large Diaphragm Mic](mixer-icons/assets/mixing-station/47.png) | `input_icon_100` | ![Large Diaphragm Mic](mixer-icons/assets/flow8/input_icon_100.png) |
| Condenser Mic Left | `condenser-mic-left` | 48 | ![Condenser Mic Left](mixer-icons/assets/mixing-station/48.png) | `input_icon_101` | ![Condenser Mic Left](mixer-icons/assets/flow8/input_icon_101.png) |
| Condenser Mic Right | `condenser-mic-right` | 49 | ![Condenser Mic Right](mixer-icons/assets/mixing-station/49.png) | `input_icon_102` | ![Condenser Mic Right](mixer-icons/assets/flow8/input_icon_102.png) |
| Handheld Mic | `handheld-mic` | 50 | ![Handheld Mic](mixer-icons/assets/mixing-station/50.png) | `input_icon_106` | ![Handheld Mic](mixer-icons/assets/flow8/input_icon_106.png) |
| Wireless Mic | `wireless-mic` | 51 | ![Wireless Mic](mixer-icons/assets/mixing-station/51.png) | `input_icon_105` | ![Wireless Mic](mixer-icons/assets/flow8/input_icon_105.png) |
| Podium Mic | `podium-mic` | 52 | ![Podium Mic](mixer-icons/assets/mixing-station/52.png) | `input_icon_103` | ![Podium Mic](mixer-icons/assets/flow8/input_icon_103.png) |
| Headset Mic | `headset-mic` | 53 | ![Headset Mic](mixer-icons/assets/mixing-station/53.png) | `input_icon_107` | ![Headset Mic](mixer-icons/assets/flow8/input_icon_107.png) |
| XLR Jack | `xlr` | 54 | ![XLR Jack](mixer-icons/assets/mixing-station/54.png) | — | — |
| TRS Plug | `trs` | 55 | ![TRS Plug](mixer-icons/assets/mixing-station/55.png) | — | — |
| TRS Plug Left | `trs-left` | 56 | ![TRS Plug Left](mixer-icons/assets/mixing-station/56.png) | `input_icon_601` | ![TRS Plug Left](mixer-icons/assets/flow8/input_icon_601.png) |
| TRS Plug Right | `trs-right` | 57 | ![TRS Plug Right](mixer-icons/assets/mixing-station/57.png) | `input_icon_602` | ![TRS Plug Right](mixer-icons/assets/flow8/input_icon_602.png) |
| RCA Plug Left | `rca-left` | 58 | ![RCA Plug Left](mixer-icons/assets/mixing-station/58.png) | — | — |
| RCA Plug Right | `rca-right` | 59 | ![RCA Plug Right](mixer-icons/assets/mixing-station/59.png) | — | — |
| Reel to Reel | `tape` | 60 | ![Reel to Reel](mixer-icons/assets/mixing-station/60.png) | `input_icon_612` | ![Reel to Reel](mixer-icons/assets/flow8/input_icon_612.png) |
| FX | `fx` | 61 | ![FX](mixer-icons/assets/mixing-station/61.png) | `input_icon_002` | ![FX](mixer-icons/assets/flow8/input_icon_002.png) |
| Computer | `computer` | 62 | ![Computer](mixer-icons/assets/mixing-station/62.png) | `input_icon_608` | ![Computer](mixer-icons/assets/flow8/input_icon_608.png) |
| Monitor Wedge | `wedge` | 63 | ![Monitor Wedge](mixer-icons/assets/mixing-station/63.png) | `input_icon_509` | ![Monitor Wedge](mixer-icons/assets/flow8/input_icon_509.png) |
| Left Speaker | `speaker-right` | 64 | ![Left Speaker](mixer-icons/assets/mixing-station/64.png) | `input_icon_510` | ![Left Speaker](mixer-icons/assets/flow8/input_icon_510.png) |
| Right Speaker | `speaker-left` | 65 | ![Right Speaker](mixer-icons/assets/mixing-station/65.png) | `input_icon_511` | ![Right Speaker](mixer-icons/assets/flow8/input_icon_511.png) |
| Speaker Array | `speaker-array` | 66 | ![Speaker Array](mixer-icons/assets/mixing-station/66.png) | `input_icon_504` | ![Speaker Array](mixer-icons/assets/flow8/input_icon_504.png) |
| Speaker on a Pole | `speaker-on-pole` | 67 | ![Speaker on a Pole](mixer-icons/assets/mixing-station/67.png) | `input_icon_508` | ![Speaker on a Pole](mixer-icons/assets/flow8/input_icon_508.png) |
| Amp Rack | `amp-rack` | 68 | ![Amp Rack](mixer-icons/assets/mixing-station/68.png) | `input_icon_607` | ![Amp Rack](mixer-icons/assets/flow8/input_icon_607.png) |
| Controls | `controls` | 69 | ![Controls](mixer-icons/assets/mixing-station/69.png) | — | — |
| Fader | `fader` | 70 | ![Fader](mixer-icons/assets/mixing-station/70.png) | `input_icon_013` | ![Fader](mixer-icons/assets/flow8/input_icon_013.png) |
| MixBus | `mix-bus` | 71 | ![MixBus](mixer-icons/assets/mixing-station/71.png) | — | — |
| Matrix | `matrix` | 72 | ![Matrix](mixer-icons/assets/mixing-station/72.png) | `input_icon_011` | ![Matrix](mixer-icons/assets/flow8/input_icon_011.png) |
| Routing | `routing` | 73 | ![Routing](mixer-icons/assets/mixing-station/73.png) | `input_icon_012` | ![Routing](mixer-icons/assets/flow8/input_icon_012.png) |
| Smiley | `smiley` | 74 | ![Smiley](mixer-icons/assets/mixing-station/74.png) | `input_icon_014` | ![Smiley](mixer-icons/assets/flow8/input_icon_014.png) |

---

## Related

- `mixer-behringer/.../MixingStationIcons.kt`
- `mixer-behringer/.../Flow8IconPresets.kt`
