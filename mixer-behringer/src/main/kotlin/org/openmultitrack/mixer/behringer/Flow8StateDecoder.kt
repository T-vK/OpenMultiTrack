package org.openmultitrack.mixer.behringer

/**
 * Decodes FLOW 8 channel names and icons from a BLE MixerState buffer.
 *
 * The mixer always returns six names: Ch1–4, then Ch5+6, then Ch7+8.
 * Icons require both the `0x38` state buffer (input type per strip) and the
 * `0x80` icon-config payload (preset index per strip). See doc 06.
 */
object Flow8StateDecoder {
    const val MIXER_NAME_COUNT = 6

    private val FIXED_OFFSETS = intArrayOf(
        0x0554, 0x0572, 0x0590, 0x05AE, 0x05CC, 0x05EA,
    )

    private const val INPUT_TYPE_DYNAMIC_MIC = 0
    private const val INPUT_TYPE_CONDENSOR_MIC = 1
    private const val INPUT_TYPE_GUITAR_OR_BASS = 2
    private const val INPUT_TYPE_LINE_INSTRUMENT = 3
    private const val INPUT_TYPE_GUITAR_PAGE = 4
    private const val INPUT_TYPE_PLAYBACK = 5

    private const val ICON_MARKER_TYPED = 0x03
    private const val ICON_MARKER_PLAIN = 0x00
    private const val RECORD_MAGIC = 0x6A

    private val PRESET_TO_MS_ICON = mapOf(
        Pair(INPUT_TYPE_DYNAMIC_MIC, 4) to MixingStationIcons.HANDHELD_MIC,
        Pair(INPUT_TYPE_DYNAMIC_MIC, 7) to MixingStationIcons.HANDHELD_MIC,
        Pair(INPUT_TYPE_LINE_INSTRUMENT, 4) to MixingStationIcons.VIOLIN,
        Pair(INPUT_TYPE_GUITAR_OR_BASS, 2) to MixingStationIcons.ACOUSTIC_GUITAR,
        Pair(INPUT_TYPE_GUITAR_PAGE, 2) to MixingStationIcons.ACOUSTIC_GUITAR,
        Pair(INPUT_TYPE_PLAYBACK, 7) to MixingStationIcons.TAPE,
    )

    private val PLAIN_PRESET_TO_MS_ICON = mapOf(
        0x02 to MixingStationIcons.ELECTRIC_BASS,
        0x04 to MixingStationIcons.VIOLIN,
    )

    fun decodeNames(buf: ByteArray): List<String> {
        val fixed = FIXED_OFFSETS.toList().mapNotNull { offset -> readLengthPrefixed(buf, offset) }
        if (fixed.size >= MIXER_NAME_COUNT) return fixed.take(MIXER_NAME_COUNT)
        return scanNames(buf).take(MIXER_NAME_COUNT)
    }

    internal fun readLengthPrefixed(buf: ByteArray, offset: Int): String? {
        if (offset >= buf.size) return null
        val len = buf[offset].toInt() and 0xFF
        if (len !in 2..18 || offset + 1 + len > buf.size) return null
        val slice = buf.copyOfRange(offset + 1, offset + 1 + len)
        if (!slice.all { it in 0x20..0x7E }) return null
        return String(slice, Charsets.US_ASCII)
    }

    /**
     * Parses the 48-byte ParamQuery `0x80` payload together with the MixerState buffer.
     * Preset bytes alone are not Mixing Station icon ids — combine with per-strip input type.
     */
    fun parseIconConfig(
        payload: ByteArray,
        mixerState: ByteArray? = null,
        maxStrips: Int = MIXER_NAME_COUNT,
    ): List<Int?> {
        val nameOffsets = mixerState?.let(::scanNameOffsets).orEmpty()
        val groups = payload.size / 4
        return (0 until minOf(maxStrips, groups)).map { index ->
            val base = index * 4
            val marker = payload[base].toInt() and 0xFF
            val preset = payload[base + 1].toInt() and 0xFF
            decodeIconGroup(index, marker, preset, mixerState, nameOffsets)
        }
    }

    internal fun scanNameOffsets(buf: ByteArray, maxNames: Int = MIXER_NAME_COUNT): List<Int> {
        val offsets = mutableListOf<Int>()
        var i = 0
        while (i < buf.size && offsets.size < maxNames) {
            val name = readLengthPrefixed(buf, i)
            if (name != null) {
                offsets.add(i)
                i += 1 + (buf[i].toInt() and 0xFF)
            } else {
                i++
            }
        }
        return offsets
    }

    internal fun decodeInputType(buf: ByteArray, nameOffset: Int): Int {
        if (nameOffset >= 1 && (buf[nameOffset - 1].toInt() and 0xFF) == RECORD_MAGIC) {
            return INPUT_TYPE_DYNAMIC_MIC
        }
        if (nameOffset >= 3) {
            val value = buf[nameOffset - 3].toInt() and 0xFF
            if (value <= INPUT_TYPE_PLAYBACK) return value
        }
        return INPUT_TYPE_DYNAMIC_MIC
    }

    internal fun decodeIconGroup(
        stripIndex: Int,
        marker: Int,
        preset: Int,
        mixerState: ByteArray?,
        nameOffsets: List<Int>,
    ): Int? {
        if (marker == ICON_MARKER_TYPED) {
            val inputType = if (mixerState != null && stripIndex < nameOffsets.size) {
                decodeInputType(mixerState, nameOffsets[stripIndex])
            } else {
                INPUT_TYPE_DYNAMIC_MIC
            }
            return resolvePresetIcon(inputType, preset)
        }
        if (marker == ICON_MARKER_PLAIN) {
            PLAIN_PRESET_TO_MS_ICON[preset]?.let { return it }
            return preset.takeIf { it in 1..MixingStationIcons.MAX_ID }
        }
        return when {
            marker in 1..MixingStationIcons.MAX_ID -> marker
            preset in 1..MixingStationIcons.MAX_ID -> preset
            else -> null
        }
    }

    private fun resolvePresetIcon(inputType: Int, preset: Int): Int? {
        PRESET_TO_MS_ICON[inputType to preset]?.let { return it }
        return preset.takeIf { it in 1..MixingStationIcons.MAX_ID }
    }

    private fun scanNames(buf: ByteArray): List<String> {
        val names = mutableListOf<String>()
        var i = 0
        while (i < buf.size) {
            val name = readLengthPrefixed(buf, i)
            if (name != null) {
                val len = buf[i].toInt() and 0xFF
                names.add(name)
                i += 1 + len
            } else {
                i++
            }
        }
        return names
    }
}
