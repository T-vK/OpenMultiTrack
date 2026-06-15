package org.openmultitrack.mixer.behringer

/**
 * Maps FLOW 8 BLE `(input_type, preset_index)` pairs to Mixing Station scribble ids (1–74).
 *
 * Preset tables follow the Flow Mix icon picker order (see doc 06). Hardware-validated
 * pairs override the generated defaults. Regenerate with [Flow8IconTableExtractionTest]
 * when Flow Mix is installed on a device.
 */
object Flow8IconPresets {
    const val INPUT_TYPE_DYNAMIC_MIC = 0
    const val INPUT_TYPE_CONDENSOR_MIC = 1
    const val INPUT_TYPE_GUITAR_OR_BASS = 2
    const val INPUT_TYPE_LINE_INSTRUMENT = 3
    const val INPUT_TYPE_GUITAR_PAGE = 4
    const val INPUT_TYPE_PLAYBACK = 5

    private val PRESET_ICONS: List<IntArray> = listOf(
        // Type 0 — dynamic / wired mics (15 presets, input_icon_000–014)
        intArrayOf(
            1, 47, 48, 49, 50, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59,
        ),
        // Type 1 — condenser mics (11 presets, input_icon_100–110)
        intArrayOf(
            47, 48, 49, 48, 49, 53, 54, 55, 56, 57, 58,
        ),
        // Type 2 — guitar / bass (18 presets, input_icon_200–217)
        intArrayOf(
            17, 18, 23, 20, 21, 22, 23, 24, 25, 26, 17, 18, 19, 20, 21, 22, 23, 26,
        ),
        // Type 3 — line instruments (18 presets, input_icon_300–317)
        intArrayOf(
            27, 28, 29, 30, 39, 35, 36, 37, 38, 40, 15, 16, 13, 14, 31, 32, 33, 34,
        ),
        // Type 4 — extended guitar page (8 presets, input_icon_400–407)
        intArrayOf(
            20, 21, 23, 24, 25, 26, 17, 18,
        ),
        // Type 5 — playback / sources (12 presets, input_icon_500–511)
        intArrayOf(
            60, 61, 62, 63, 64, 65, 66, 67, 54, 55, 62, 60,
        ),
    )

    /** Hardware-validated overrides (firmware v11749 capture, 2026-06-08). */
    private val VALIDATED_OVERRIDES: Map<Pair<Int, Int>, Int> = mapOf(
        (INPUT_TYPE_DYNAMIC_MIC to 4) to MixingStationIcons.HANDHELD_MIC,
        (INPUT_TYPE_DYNAMIC_MIC to 7) to MixingStationIcons.HANDHELD_MIC,
        (INPUT_TYPE_GUITAR_OR_BASS to 2) to MixingStationIcons.ACOUSTIC_GUITAR,
        (INPUT_TYPE_LINE_INSTRUMENT to 4) to MixingStationIcons.VIOLIN,
        (INPUT_TYPE_GUITAR_PAGE to 2) to MixingStationIcons.ACOUSTIC_GUITAR,
        (INPUT_TYPE_PLAYBACK to 7) to MixingStationIcons.TAPE,
    )

    private val PLAIN_PRESET_TO_MS_ICON = mapOf(
        0x02 to MixingStationIcons.ELECTRIC_BASS,
        0x04 to MixingStationIcons.VIOLIN,
    )

    fun resolve(inputType: Int, preset: Int): Int? {
        VALIDATED_OVERRIDES[inputType to preset]?.let { return it }
        if (inputType in PRESET_ICONS.indices) {
            val table = PRESET_ICONS[inputType]
            if (preset in table.indices) {
                return table[preset].takeIf { it in 1..MixingStationIcons.MAX_ID }
            }
        }
        return null
    }

    fun resolvePlainPreset(preset: Int): Int? =
        PLAIN_PRESET_TO_MS_ICON[preset]
            ?: preset.takeIf { it in 1..MixingStationIcons.MAX_ID }
}
