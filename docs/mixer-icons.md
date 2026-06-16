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
others are inferred from the resolved Mixing Station id.

| Label | Input type | Preset | Drawable | MS ID | MS slug | Icon |
| ----- | ---------- | ------ | -------- | ----- | ------- | ---- |
| No icon | 0 (Dynamic mic) | 0 | `input_icon_000` | 1 | `blank` | ![No icon](mixer-icons/assets/flow8/input_icon_000.png) |
| Large Diaphragm Mic | 0 (Dynamic mic) | 1 | `input_icon_001` | 47 | `large-diaphragm-mic` | ![Large Diaphragm Mic](mixer-icons/assets/flow8/input_icon_001.png) |
| Condenser Mic Left | 0 (Dynamic mic) | 2 | `input_icon_002` | 48 | `condenser-mic-left` | ![Condenser Mic Left](mixer-icons/assets/flow8/input_icon_002.png) |
| Condenser Mic Right | 0 (Dynamic mic) | 3 | `input_icon_003` | 49 | `condenser-mic-right` | ![Condenser Mic Right](mixer-icons/assets/flow8/input_icon_003.png) |
| Wired Mic *(validated)* | 0 (Dynamic mic) | 4 | `input_icon_004` | 50 | `handheld-mic` | ![Wired Mic](mixer-icons/assets/flow8/input_icon_004.png) |
| Handheld Mic | 0 (Dynamic mic) | 5 | `input_icon_005` | 50 | `handheld-mic` | ![Handheld Mic](mixer-icons/assets/flow8/input_icon_005.png) |
| Wireless Mic | 0 (Dynamic mic) | 6 | `input_icon_006` | 51 | `wireless-mic` | ![Wireless Mic](mixer-icons/assets/flow8/input_icon_006.png) |
| Wired Mic *(validated)* | 0 (Dynamic mic) | 7 | `input_icon_007` | 50 | `handheld-mic` | ![Wired Mic](mixer-icons/assets/flow8/input_icon_007.png) |
| Headset Mic | 0 (Dynamic mic) | 8 | `input_icon_008` | 53 | `headset-mic` | ![Headset Mic](mixer-icons/assets/flow8/input_icon_008.png) |
| XLR Jack | 0 (Dynamic mic) | 9 | `input_icon_009` | 54 | `xlr` | ![XLR Jack](mixer-icons/assets/flow8/input_icon_009.png) |
| TRS Plug | 0 (Dynamic mic) | 10 | `input_icon_010` | 55 | `trs` | ![TRS Plug](mixer-icons/assets/flow8/input_icon_010.png) |
| TRS Plug Left | 0 (Dynamic mic) | 11 | `input_icon_011` | 56 | `trs-left` | ![TRS Plug Left](mixer-icons/assets/flow8/input_icon_011.png) |
| TRS Plug Right | 0 (Dynamic mic) | 12 | `input_icon_012` | 57 | `trs-right` | ![TRS Plug Right](mixer-icons/assets/flow8/input_icon_012.png) |
| RCA Plug Left | 0 (Dynamic mic) | 13 | `input_icon_013` | 58 | `rca-left` | ![RCA Plug Left](mixer-icons/assets/flow8/input_icon_013.png) |
| RCA Plug Right | 0 (Dynamic mic) | 14 | `input_icon_014` | 59 | `rca-right` | ![RCA Plug Right](mixer-icons/assets/flow8/input_icon_014.png) |
| Large Diaphragm Mic | 1 (Condenser mic) | 0 | `input_icon_100` | 47 | `large-diaphragm-mic` | ![Large Diaphragm Mic](mixer-icons/assets/flow8/input_icon_100.png) |
| Condenser Mic Left | 1 (Condenser mic) | 1 | `input_icon_101` | 48 | `condenser-mic-left` | ![Condenser Mic Left](mixer-icons/assets/flow8/input_icon_101.png) |
| Condenser Mic Right | 1 (Condenser mic) | 2 | `input_icon_102` | 49 | `condenser-mic-right` | ![Condenser Mic Right](mixer-icons/assets/flow8/input_icon_102.png) |
| Condenser Mic Left | 1 (Condenser mic) | 3 | `input_icon_103` | 48 | `condenser-mic-left` | ![Condenser Mic Left](mixer-icons/assets/flow8/input_icon_103.png) |
| Condenser Mic Right | 1 (Condenser mic) | 4 | `input_icon_104` | 49 | `condenser-mic-right` | ![Condenser Mic Right](mixer-icons/assets/flow8/input_icon_104.png) |
| Headset Mic | 1 (Condenser mic) | 5 | `input_icon_105` | 53 | `headset-mic` | ![Headset Mic](mixer-icons/assets/flow8/input_icon_105.png) |
| XLR Jack | 1 (Condenser mic) | 6 | `input_icon_106` | 54 | `xlr` | ![XLR Jack](mixer-icons/assets/flow8/input_icon_106.png) |
| TRS Plug | 1 (Condenser mic) | 7 | `input_icon_107` | 55 | `trs` | ![TRS Plug](mixer-icons/assets/flow8/input_icon_107.png) |
| TRS Plug Left | 1 (Condenser mic) | 8 | `input_icon_108` | 56 | `trs-left` | ![TRS Plug Left](mixer-icons/assets/flow8/input_icon_108.png) |
| TRS Plug Right | 1 (Condenser mic) | 9 | `input_icon_109` | 57 | `trs-right` | ![TRS Plug Right](mixer-icons/assets/flow8/input_icon_109.png) |
| RCA Plug Left | 1 (Condenser mic) | 10 | `input_icon_110` | 58 | `rca-left` | ![RCA Plug Left](mixer-icons/assets/flow8/input_icon_110.png) |
| Electric Bass | 2 (Guitar / bass) | 0 | `input_icon_200` | 17 | `electric-bass` | ![Electric Bass](mixer-icons/assets/flow8/input_icon_200.png) |
| Acoustic Bass | 2 (Guitar / bass) | 1 | `input_icon_201` | 18 | `acoustic-bass` | ![Acoustic Bass](mixer-icons/assets/flow8/input_icon_201.png) |
| Acoustic Guitar *(validated)* | 2 (Guitar / bass) | 2 | `input_icon_202` | 23 | `acoustic-guitar` | ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_202.png) |
| Les Paul Guitar | 2 (Guitar / bass) | 3 | `input_icon_203` | 20 | `les-paul` | ![Les Paul Guitar](mixer-icons/assets/flow8/input_icon_203.png) |
| Ibanez Guitar | 2 (Guitar / bass) | 4 | `input_icon_204` | 21 | `ibanez` | ![Ibanez Guitar](mixer-icons/assets/flow8/input_icon_204.png) |
| Washburn Guitar | 2 (Guitar / bass) | 5 | `input_icon_205` | 22 | `washburn` | ![Washburn Guitar](mixer-icons/assets/flow8/input_icon_205.png) |
| Acoustic Guitar | 2 (Guitar / bass) | 6 | `input_icon_206` | 23 | `acoustic-guitar` | ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_206.png) |
| Bass Amp | 2 (Guitar / bass) | 7 | `input_icon_207` | 24 | `bass-amp` | ![Bass Amp](mixer-icons/assets/flow8/input_icon_207.png) |
| Guitar Amp | 2 (Guitar / bass) | 8 | `input_icon_208` | 25 | `guitar-amp` | ![Guitar Amp](mixer-icons/assets/flow8/input_icon_208.png) |
| Amp Cabinet | 2 (Guitar / bass) | 9 | `input_icon_209` | 26 | `amp-cabinet` | ![Amp Cabinet](mixer-icons/assets/flow8/input_icon_209.png) |
| Electric Bass | 2 (Guitar / bass) | 10 | `input_icon_210` | 17 | `electric-bass` | ![Electric Bass](mixer-icons/assets/flow8/input_icon_210.png) |
| Acoustic Bass | 2 (Guitar / bass) | 11 | `input_icon_211` | 18 | `acoustic-bass` | ![Acoustic Bass](mixer-icons/assets/flow8/input_icon_211.png) |
| Contrabass | 2 (Guitar / bass) | 12 | `input_icon_212` | 19 | `contrabass` | ![Contrabass](mixer-icons/assets/flow8/input_icon_212.png) |
| Les Paul Guitar | 2 (Guitar / bass) | 13 | `input_icon_213` | 20 | `les-paul` | ![Les Paul Guitar](mixer-icons/assets/flow8/input_icon_213.png) |
| Ibanez Guitar | 2 (Guitar / bass) | 14 | `input_icon_214` | 21 | `ibanez` | ![Ibanez Guitar](mixer-icons/assets/flow8/input_icon_214.png) |
| Washburn Guitar | 2 (Guitar / bass) | 15 | `input_icon_215` | 22 | `washburn` | ![Washburn Guitar](mixer-icons/assets/flow8/input_icon_215.png) |
| Acoustic Guitar | 2 (Guitar / bass) | 16 | `input_icon_216` | 23 | `acoustic-guitar` | ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_216.png) |
| Amp Cabinet | 2 (Guitar / bass) | 17 | `input_icon_217` | 26 | `amp-cabinet` | ![Amp Cabinet](mixer-icons/assets/flow8/input_icon_217.png) |
| Piano | 3 (Line instrument) | 0 | `input_icon_300` | 27 | `piano` | ![Piano](mixer-icons/assets/flow8/input_icon_300.png) |
| Organ | 3 (Line instrument) | 1 | `input_icon_301` | 28 | `organ` | ![Organ](mixer-icons/assets/flow8/input_icon_301.png) |
| Harpsichord | 3 (Line instrument) | 2 | `input_icon_302` | 29 | `harpsichord` | ![Harpsichord](mixer-icons/assets/flow8/input_icon_302.png) |
| Keyboard | 3 (Line instrument) | 3 | `input_icon_303` | 30 | `keyboard` | ![Keyboard](mixer-icons/assets/flow8/input_icon_303.png) |
| Violine *(validated)* | 3 (Line instrument) | 4 | `input_icon_304` | 39 | `violin` | ![Violine](mixer-icons/assets/flow8/input_icon_304.png) |
| Trumpet | 3 (Line instrument) | 5 | `input_icon_305` | 35 | `trumpet` | ![Trumpet](mixer-icons/assets/flow8/input_icon_305.png) |
| Trombone | 3 (Line instrument) | 6 | `input_icon_306` | 36 | `trombone` | ![Trombone](mixer-icons/assets/flow8/input_icon_306.png) |
| Saxophone | 3 (Line instrument) | 7 | `input_icon_307` | 37 | `saxophone` | ![Saxophone](mixer-icons/assets/flow8/input_icon_307.png) |
| Clarinet | 3 (Line instrument) | 8 | `input_icon_308` | 38 | `clarinet` | ![Clarinet](mixer-icons/assets/flow8/input_icon_308.png) |
| Cello | 3 (Line instrument) | 9 | `input_icon_309` | 40 | `cello` | ![Cello](mixer-icons/assets/flow8/input_icon_309.png) |
| Tambourine | 3 (Line instrument) | 10 | `input_icon_310` | 15 | `tambourine` | ![Tambourine](mixer-icons/assets/flow8/input_icon_310.png) |
| Vibraphone | 3 (Line instrument) | 11 | `input_icon_311` | 16 | `vibraphone` | ![Vibraphone](mixer-icons/assets/flow8/input_icon_311.png) |
| Bongos | 3 (Line instrument) | 12 | `input_icon_312` | 13 | `bongos` | ![Bongos](mixer-icons/assets/flow8/input_icon_312.png) |
| Congas | 3 (Line instrument) | 13 | `input_icon_313` | 14 | `congas` | ![Congas](mixer-icons/assets/flow8/input_icon_313.png) |
| Synthesizer 1 | 3 (Line instrument) | 14 | `input_icon_314` | 31 | `synthesizer-1` | ![Synthesizer 1](mixer-icons/assets/flow8/input_icon_314.png) |
| Synthesizer 2 | 3 (Line instrument) | 15 | `input_icon_315` | 32 | `synthesizer-2` | ![Synthesizer 2](mixer-icons/assets/flow8/input_icon_315.png) |
| Synthesizer 3 | 3 (Line instrument) | 16 | `input_icon_316` | 33 | `synthesizer-3` | ![Synthesizer 3](mixer-icons/assets/flow8/input_icon_316.png) |
| Keytar | 3 (Line instrument) | 17 | `input_icon_317` | 34 | `keytar` | ![Keytar](mixer-icons/assets/flow8/input_icon_317.png) |
| Les Paul Guitar | 4 (Guitar page (extended)) | 0 | `input_icon_400` | 20 | `les-paul` | ![Les Paul Guitar](mixer-icons/assets/flow8/input_icon_400.png) |
| Ibanez Guitar | 4 (Guitar page (extended)) | 1 | `input_icon_401` | 21 | `ibanez` | ![Ibanez Guitar](mixer-icons/assets/flow8/input_icon_401.png) |
| Acoustic Guitar *(validated)* | 4 (Guitar page (extended)) | 2 | `input_icon_402` | 23 | `acoustic-guitar` | ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_402.png) |
| Bass Amp | 4 (Guitar page (extended)) | 3 | `input_icon_403` | 24 | `bass-amp` | ![Bass Amp](mixer-icons/assets/flow8/input_icon_403.png) |
| Guitar Amp | 4 (Guitar page (extended)) | 4 | `input_icon_404` | 25 | `guitar-amp` | ![Guitar Amp](mixer-icons/assets/flow8/input_icon_404.png) |
| Amp Cabinet | 4 (Guitar page (extended)) | 5 | `input_icon_405` | 26 | `amp-cabinet` | ![Amp Cabinet](mixer-icons/assets/flow8/input_icon_405.png) |
| Electric Bass | 4 (Guitar page (extended)) | 6 | `input_icon_406` | 17 | `electric-bass` | ![Electric Bass](mixer-icons/assets/flow8/input_icon_406.png) |
| Acoustic Bass | 4 (Guitar page (extended)) | 7 | `input_icon_407` | 18 | `acoustic-bass` | ![Acoustic Bass](mixer-icons/assets/flow8/input_icon_407.png) |
| Reel to Reel | 5 (Playback / source) | 0 | `input_icon_500` | 60 | `tape` | ![Reel to Reel](mixer-icons/assets/flow8/input_icon_500.png) |
| FX | 5 (Playback / source) | 1 | `input_icon_501` | 61 | `fx` | ![FX](mixer-icons/assets/flow8/input_icon_501.png) |
| Computer | 5 (Playback / source) | 2 | `input_icon_502` | 62 | `computer` | ![Computer](mixer-icons/assets/flow8/input_icon_502.png) |
| Monitor Wedge | 5 (Playback / source) | 3 | `input_icon_503` | 63 | `wedge` | ![Monitor Wedge](mixer-icons/assets/flow8/input_icon_503.png) |
| Left Speaker | 5 (Playback / source) | 4 | `input_icon_504` | 64 | `speaker-right` | ![Left Speaker](mixer-icons/assets/flow8/input_icon_504.png) |
| Right Speaker | 5 (Playback / source) | 5 | `input_icon_505` | 65 | `speaker-left` | ![Right Speaker](mixer-icons/assets/flow8/input_icon_505.png) |
| Speaker Array | 5 (Playback / source) | 6 | `input_icon_506` | 66 | `speaker-array` | ![Speaker Array](mixer-icons/assets/flow8/input_icon_506.png) |
| Record player *(validated)* | 5 (Playback / source) | 7 | `input_icon_507` | 60 | `tape` | ![Record player](mixer-icons/assets/flow8/input_icon_507.png) |
| XLR Jack | 5 (Playback / source) | 8 | `input_icon_508` | 54 | `xlr` | ![XLR Jack](mixer-icons/assets/flow8/input_icon_508.png) |
| TRS Plug | 5 (Playback / source) | 9 | `input_icon_509` | 55 | `trs` | ![TRS Plug](mixer-icons/assets/flow8/input_icon_509.png) |
| Computer | 5 (Playback / source) | 10 | `input_icon_510` | 62 | `computer` | ![Computer](mixer-icons/assets/flow8/input_icon_510.png) |
| Reel to Reel | 5 (Playback / source) | 11 | `input_icon_511` | 60 | `tape` | ![Reel to Reel](mixer-icons/assets/flow8/input_icon_511.png) |

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

*Maintained in `tools/flow8_icon_decode.py` (`PRESET_TO_MS_ICON`, `FLOW_UI_LABELS`).*

## Appendix D: Combined reference (by Mixing Station id)

Cross-reference of Mixing Station ids with every FLOW picker slot that resolves to
the same id. **Icon (MS)** uses the X32 BMP artwork; **Icon (FLOW)** is from the
Flow Mix APK drawable named in the FLOW drawable column.

| Label (MS) | MS slug | MS ID | Icon (MS) | FLOW drawable(s) | Icon (FLOW) |
| ---------- | ------- | ----- | --------- | ---------------- | ----------- |
| No icon | `blank` | 1 | ![No icon](mixer-icons/assets/mixing-station/1.png) | `input_icon_000` | ![No icon](mixer-icons/assets/flow8/input_icon_000.png) |
| Kick Back | `kick-back` | 2 | ![Kick Back](mixer-icons/assets/mixing-station/2.png) | — | — |
| Kick Front | `kick-front` | 3 | ![Kick Front](mixer-icons/assets/mixing-station/3.png) | — | — |
| Snare Top | `snare-top` | 4 | ![Snare Top](mixer-icons/assets/mixing-station/4.png) | — | — |
| Snare Bottom | `snare-bottom` | 5 | ![Snare Bottom](mixer-icons/assets/mixing-station/5.png) | — | — |
| High Tom | `tom-high` | 6 | ![High Tom](mixer-icons/assets/mixing-station/6.png) | — | — |
| Mid Tom | `tom-medium` | 7 | ![Mid Tom](mixer-icons/assets/mixing-station/7.png) | — | — |
| Floor Tom | `floor-tom` | 8 | ![Floor Tom](mixer-icons/assets/mixing-station/8.png) | — | — |
| Hi-Hat | `hi-hat` | 9 | ![Hi-Hat](mixer-icons/assets/mixing-station/9.png) | — | — |
| Ride | `crash` | 10 | ![Ride](mixer-icons/assets/mixing-station/10.png) | — | — |
| Drum Kit | `drum-kit` | 11 | ![Drum Kit](mixer-icons/assets/mixing-station/11.png) | — | — |
| Cowbell | `cowbell` | 12 | ![Cowbell](mixer-icons/assets/mixing-station/12.png) | — | — |
| Bongos | `bongos` | 13 | ![Bongos](mixer-icons/assets/mixing-station/13.png) | `input_icon_312` | ![Bongos](mixer-icons/assets/flow8/input_icon_312.png) |
| Congas | `congas` | 14 | ![Congas](mixer-icons/assets/mixing-station/14.png) | `input_icon_313` | ![Congas](mixer-icons/assets/flow8/input_icon_313.png) |
| Tambourine | `tambourine` | 15 | ![Tambourine](mixer-icons/assets/mixing-station/15.png) | `input_icon_310` | ![Tambourine](mixer-icons/assets/flow8/input_icon_310.png) |
| Vibraphone | `vibraphone` | 16 | ![Vibraphone](mixer-icons/assets/mixing-station/16.png) | `input_icon_311` | ![Vibraphone](mixer-icons/assets/flow8/input_icon_311.png) |
| Electric Bass | `electric-bass` | 17 | ![Electric Bass](mixer-icons/assets/mixing-station/17.png) | `input_icon_200`, `input_icon_210`, `input_icon_406` | ![Electric Bass](mixer-icons/assets/flow8/input_icon_200.png) ![Electric Bass](mixer-icons/assets/flow8/input_icon_210.png) ![Electric Bass](mixer-icons/assets/flow8/input_icon_406.png) |
| Acoustic Bass | `acoustic-bass` | 18 | ![Acoustic Bass](mixer-icons/assets/mixing-station/18.png) | `input_icon_201`, `input_icon_211`, `input_icon_407` | ![Acoustic Bass](mixer-icons/assets/flow8/input_icon_201.png) ![Acoustic Bass](mixer-icons/assets/flow8/input_icon_211.png) ![Acoustic Bass](mixer-icons/assets/flow8/input_icon_407.png) |
| Contrabass | `contrabass` | 19 | ![Contrabass](mixer-icons/assets/mixing-station/19.png) | `input_icon_212` | ![Contrabass](mixer-icons/assets/flow8/input_icon_212.png) |
| Les Paul Guitar | `les-paul` | 20 | ![Les Paul Guitar](mixer-icons/assets/mixing-station/20.png) | `input_icon_203`, `input_icon_213`, `input_icon_400` | ![Les Paul Guitar](mixer-icons/assets/flow8/input_icon_203.png) ![Les Paul Guitar](mixer-icons/assets/flow8/input_icon_213.png) ![Les Paul Guitar](mixer-icons/assets/flow8/input_icon_400.png) |
| Ibanez Guitar | `ibanez` | 21 | ![Ibanez Guitar](mixer-icons/assets/mixing-station/21.png) | `input_icon_204`, `input_icon_214`, `input_icon_401` | ![Ibanez Guitar](mixer-icons/assets/flow8/input_icon_204.png) ![Ibanez Guitar](mixer-icons/assets/flow8/input_icon_214.png) ![Ibanez Guitar](mixer-icons/assets/flow8/input_icon_401.png) |
| Washburn Guitar | `washburn` | 22 | ![Washburn Guitar](mixer-icons/assets/mixing-station/22.png) | `input_icon_205`, `input_icon_215` | ![Washburn Guitar](mixer-icons/assets/flow8/input_icon_205.png) ![Washburn Guitar](mixer-icons/assets/flow8/input_icon_215.png) |
| Acoustic Guitar | `acoustic-guitar` | 23 | ![Acoustic Guitar](mixer-icons/assets/mixing-station/23.png) | `input_icon_202`, `input_icon_206`, `input_icon_216`, `input_icon_402` | ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_202.png) ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_206.png) ![Acoustic Guitar](mixer-icons/assets/flow8/input_icon_216.png) … +1 |
| Bass Amp | `bass-amp` | 24 | ![Bass Amp](mixer-icons/assets/mixing-station/24.png) | `input_icon_207`, `input_icon_403` | ![Bass Amp](mixer-icons/assets/flow8/input_icon_207.png) ![Bass Amp](mixer-icons/assets/flow8/input_icon_403.png) |
| Guitar Amp | `guitar-amp` | 25 | ![Guitar Amp](mixer-icons/assets/mixing-station/25.png) | `input_icon_208`, `input_icon_404` | ![Guitar Amp](mixer-icons/assets/flow8/input_icon_208.png) ![Guitar Amp](mixer-icons/assets/flow8/input_icon_404.png) |
| Amp Cabinet | `amp-cabinet` | 26 | ![Amp Cabinet](mixer-icons/assets/mixing-station/26.png) | `input_icon_209`, `input_icon_217`, `input_icon_405` | ![Amp Cabinet](mixer-icons/assets/flow8/input_icon_209.png) ![Amp Cabinet](mixer-icons/assets/flow8/input_icon_217.png) ![Amp Cabinet](mixer-icons/assets/flow8/input_icon_405.png) |
| Piano | `piano` | 27 | ![Piano](mixer-icons/assets/mixing-station/27.png) | `input_icon_300` | ![Piano](mixer-icons/assets/flow8/input_icon_300.png) |
| Organ | `organ` | 28 | ![Organ](mixer-icons/assets/mixing-station/28.png) | `input_icon_301` | ![Organ](mixer-icons/assets/flow8/input_icon_301.png) |
| Harpsichord | `harpsichord` | 29 | ![Harpsichord](mixer-icons/assets/mixing-station/29.png) | `input_icon_302` | ![Harpsichord](mixer-icons/assets/flow8/input_icon_302.png) |
| Keyboard | `keyboard` | 30 | ![Keyboard](mixer-icons/assets/mixing-station/30.png) | `input_icon_303` | ![Keyboard](mixer-icons/assets/flow8/input_icon_303.png) |
| Synthesizer 1 | `synthesizer-1` | 31 | ![Synthesizer 1](mixer-icons/assets/mixing-station/31.png) | `input_icon_314` | ![Synthesizer 1](mixer-icons/assets/flow8/input_icon_314.png) |
| Synthesizer 2 | `synthesizer-2` | 32 | ![Synthesizer 2](mixer-icons/assets/mixing-station/32.png) | `input_icon_315` | ![Synthesizer 2](mixer-icons/assets/flow8/input_icon_315.png) |
| Synthesizer 3 | `synthesizer-3` | 33 | ![Synthesizer 3](mixer-icons/assets/mixing-station/33.png) | `input_icon_316` | ![Synthesizer 3](mixer-icons/assets/flow8/input_icon_316.png) |
| Keytar | `keytar` | 34 | ![Keytar](mixer-icons/assets/mixing-station/34.png) | `input_icon_317` | ![Keytar](mixer-icons/assets/flow8/input_icon_317.png) |
| Trumpet | `trumpet` | 35 | ![Trumpet](mixer-icons/assets/mixing-station/35.png) | `input_icon_305` | ![Trumpet](mixer-icons/assets/flow8/input_icon_305.png) |
| Trombone | `trombone` | 36 | ![Trombone](mixer-icons/assets/mixing-station/36.png) | `input_icon_306` | ![Trombone](mixer-icons/assets/flow8/input_icon_306.png) |
| Saxophone | `saxophone` | 37 | ![Saxophone](mixer-icons/assets/mixing-station/37.png) | `input_icon_307` | ![Saxophone](mixer-icons/assets/flow8/input_icon_307.png) |
| Clarinet | `clarinet` | 38 | ![Clarinet](mixer-icons/assets/mixing-station/38.png) | `input_icon_308` | ![Clarinet](mixer-icons/assets/flow8/input_icon_308.png) |
| Violin | `violin` | 39 | ![Violin](mixer-icons/assets/mixing-station/39.png) | `input_icon_304` | ![Violine](mixer-icons/assets/flow8/input_icon_304.png) |
| Cello | `cello` | 40 | ![Cello](mixer-icons/assets/mixing-station/40.png) | `input_icon_309` | ![Cello](mixer-icons/assets/flow8/input_icon_309.png) |
| Male Vocal | `male-vocal` | 41 | ![Male Vocal](mixer-icons/assets/mixing-station/41.png) | — | — |
| Female Vocal | `female-vocal` | 42 | ![Female Vocal](mixer-icons/assets/mixing-station/42.png) | — | — |
| Choir | `choir` | 43 | ![Choir](mixer-icons/assets/mixing-station/43.png) | — | — |
| Hand Sign | `hand-sign` | 44 | ![Hand Sign](mixer-icons/assets/mixing-station/44.png) | — | — |
| Talk A | `talk-a` | 45 | ![Talk A](mixer-icons/assets/mixing-station/45.png) | — | — |
| Talk B | `talk-b` | 46 | ![Talk B](mixer-icons/assets/mixing-station/46.png) | — | — |
| Large Diaphragm Mic | `large-diaphragm-mic` | 47 | ![Large Diaphragm Mic](mixer-icons/assets/mixing-station/47.png) | `input_icon_001`, `input_icon_100` | ![Large Diaphragm Mic](mixer-icons/assets/flow8/input_icon_001.png) ![Large Diaphragm Mic](mixer-icons/assets/flow8/input_icon_100.png) |
| Condenser Mic Left | `condenser-mic-left` | 48 | ![Condenser Mic Left](mixer-icons/assets/mixing-station/48.png) | `input_icon_002`, `input_icon_101`, `input_icon_103` | ![Condenser Mic Left](mixer-icons/assets/flow8/input_icon_002.png) ![Condenser Mic Left](mixer-icons/assets/flow8/input_icon_101.png) ![Condenser Mic Left](mixer-icons/assets/flow8/input_icon_103.png) |
| Condenser Mic Right | `condenser-mic-right` | 49 | ![Condenser Mic Right](mixer-icons/assets/mixing-station/49.png) | `input_icon_003`, `input_icon_102`, `input_icon_104` | ![Condenser Mic Right](mixer-icons/assets/flow8/input_icon_003.png) ![Condenser Mic Right](mixer-icons/assets/flow8/input_icon_102.png) ![Condenser Mic Right](mixer-icons/assets/flow8/input_icon_104.png) |
| Handheld Mic | `handheld-mic` | 50 | ![Handheld Mic](mixer-icons/assets/mixing-station/50.png) | `input_icon_004`, `input_icon_005`, `input_icon_007` | ![Wired Mic](mixer-icons/assets/flow8/input_icon_004.png) ![Handheld Mic](mixer-icons/assets/flow8/input_icon_005.png) ![Wired Mic](mixer-icons/assets/flow8/input_icon_007.png) |
| Wireless Mic | `wireless-mic` | 51 | ![Wireless Mic](mixer-icons/assets/mixing-station/51.png) | `input_icon_006` | ![Wireless Mic](mixer-icons/assets/flow8/input_icon_006.png) |
| Podium Mic | `podium-mic` | 52 | ![Podium Mic](mixer-icons/assets/mixing-station/52.png) | — | — |
| Headset Mic | `headset-mic` | 53 | ![Headset Mic](mixer-icons/assets/mixing-station/53.png) | `input_icon_008`, `input_icon_105` | ![Headset Mic](mixer-icons/assets/flow8/input_icon_008.png) ![Headset Mic](mixer-icons/assets/flow8/input_icon_105.png) |
| XLR Jack | `xlr` | 54 | ![XLR Jack](mixer-icons/assets/mixing-station/54.png) | `input_icon_009`, `input_icon_106`, `input_icon_508` | ![XLR Jack](mixer-icons/assets/flow8/input_icon_009.png) ![XLR Jack](mixer-icons/assets/flow8/input_icon_106.png) ![XLR Jack](mixer-icons/assets/flow8/input_icon_508.png) |
| TRS Plug | `trs` | 55 | ![TRS Plug](mixer-icons/assets/mixing-station/55.png) | `input_icon_010`, `input_icon_107`, `input_icon_509` | ![TRS Plug](mixer-icons/assets/flow8/input_icon_010.png) ![TRS Plug](mixer-icons/assets/flow8/input_icon_107.png) ![TRS Plug](mixer-icons/assets/flow8/input_icon_509.png) |
| TRS Plug Left | `trs-left` | 56 | ![TRS Plug Left](mixer-icons/assets/mixing-station/56.png) | `input_icon_011`, `input_icon_108` | ![TRS Plug Left](mixer-icons/assets/flow8/input_icon_011.png) ![TRS Plug Left](mixer-icons/assets/flow8/input_icon_108.png) |
| TRS Plug Right | `trs-right` | 57 | ![TRS Plug Right](mixer-icons/assets/mixing-station/57.png) | `input_icon_012`, `input_icon_109` | ![TRS Plug Right](mixer-icons/assets/flow8/input_icon_012.png) ![TRS Plug Right](mixer-icons/assets/flow8/input_icon_109.png) |
| RCA Plug Left | `rca-left` | 58 | ![RCA Plug Left](mixer-icons/assets/mixing-station/58.png) | `input_icon_013`, `input_icon_110` | ![RCA Plug Left](mixer-icons/assets/flow8/input_icon_013.png) ![RCA Plug Left](mixer-icons/assets/flow8/input_icon_110.png) |
| RCA Plug Right | `rca-right` | 59 | ![RCA Plug Right](mixer-icons/assets/mixing-station/59.png) | `input_icon_014` | ![RCA Plug Right](mixer-icons/assets/flow8/input_icon_014.png) |
| Reel to Reel | `tape` | 60 | ![Reel to Reel](mixer-icons/assets/mixing-station/60.png) | `input_icon_500`, `input_icon_507`, `input_icon_511` | ![Reel to Reel](mixer-icons/assets/flow8/input_icon_500.png) ![Record player](mixer-icons/assets/flow8/input_icon_507.png) ![Reel to Reel](mixer-icons/assets/flow8/input_icon_511.png) |
| FX | `fx` | 61 | ![FX](mixer-icons/assets/mixing-station/61.png) | `input_icon_501` | ![FX](mixer-icons/assets/flow8/input_icon_501.png) |
| Computer | `computer` | 62 | ![Computer](mixer-icons/assets/mixing-station/62.png) | `input_icon_502`, `input_icon_510` | ![Computer](mixer-icons/assets/flow8/input_icon_502.png) ![Computer](mixer-icons/assets/flow8/input_icon_510.png) |
| Monitor Wedge | `wedge` | 63 | ![Monitor Wedge](mixer-icons/assets/mixing-station/63.png) | `input_icon_503` | ![Monitor Wedge](mixer-icons/assets/flow8/input_icon_503.png) |
| Left Speaker | `speaker-right` | 64 | ![Left Speaker](mixer-icons/assets/mixing-station/64.png) | `input_icon_504` | ![Left Speaker](mixer-icons/assets/flow8/input_icon_504.png) |
| Right Speaker | `speaker-left` | 65 | ![Right Speaker](mixer-icons/assets/mixing-station/65.png) | `input_icon_505` | ![Right Speaker](mixer-icons/assets/flow8/input_icon_505.png) |
| Speaker Array | `speaker-array` | 66 | ![Speaker Array](mixer-icons/assets/mixing-station/66.png) | `input_icon_506` | ![Speaker Array](mixer-icons/assets/flow8/input_icon_506.png) |
| Speaker on a Pole | `speaker-on-pole` | 67 | ![Speaker on a Pole](mixer-icons/assets/mixing-station/67.png) | — | — |
| Amp Rack | `amp-rack` | 68 | ![Amp Rack](mixer-icons/assets/mixing-station/68.png) | — | — |
| Controls | `controls` | 69 | ![Controls](mixer-icons/assets/mixing-station/69.png) | — | — |
| Fader | `fader` | 70 | ![Fader](mixer-icons/assets/mixing-station/70.png) | — | — |
| MixBus | `mix-bus` | 71 | ![MixBus](mixer-icons/assets/mixing-station/71.png) | — | — |
| Matrix | `matrix` | 72 | ![Matrix](mixer-icons/assets/mixing-station/72.png) | — | — |
| Routing | `routing` | 73 | ![Routing](mixer-icons/assets/mixing-station/73.png) | — | — |
| Smiley | `smiley` | 74 | ![Smiley](mixer-icons/assets/mixing-station/74.png) | — | — |

---

## Related

- `mixer-behringer/.../MixingStationIcons.kt`
- `mixer-behringer/.../Flow8IconPresets.kt`
