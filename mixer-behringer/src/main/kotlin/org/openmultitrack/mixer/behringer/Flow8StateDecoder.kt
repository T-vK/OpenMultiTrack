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

    private const val SYSEX_NAME_REGION_MIN_SIZE = 0x600
    private const val SYSEX_START = 0xF0
    private const val NAMES_START = 0x0554

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
        if (usesSysexNameRegion(buf)) {
            val fixed = FIXED_OFFSETS.map { readLengthPrefixed(buf, it).orEmpty() }
            if (fixed.count { it.isNotEmpty() } >= MIXER_NAME_COUNT) {
                return fixed.take(MIXER_NAME_COUNT)
            }
        }
        return orderedNamesFromStripMap(scanChannelRecords(buf))
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
     * Reads a BLE compact channel name at [lengthOffset].
     * Strips 4–6 may echo the icon-preset byte as the first counted character.
     */
    internal fun readChannelName(buf: ByteArray, lengthOffset: Int): String? {
        if (lengthOffset >= buf.size) return null
        var len = buf[lengthOffset].toInt() and 0xFF
        if (len !in 2..18 || lengthOffset + 1 + len > buf.size) return null
        var start = lengthOffset + 1
        if (lengthOffset >= 1 &&
            (buf[lengthOffset - 1].toInt() and 0xFF) != RECORD_MAGIC &&
            start < buf.size &&
            buf[start] == buf[lengthOffset - 1]
        ) {
            start++
            len--
        }
        if (len !in 2..18 || start + len > buf.size) return null
        val slice = buf.copyOfRange(start, start + len)
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
        val nameOffsets = mixerState?.let(::nameOffsetsByStrip).orEmpty()
        val groups = payload.size / 4
        return (0 until minOf(maxStrips, groups)).map { index ->
            val base = index * 4
            val marker = payload[base].toInt() and 0xFF
            val preset = payload[base + 1].toInt() and 0xFF
            decodeIconGroup(index, marker, preset, mixerState, nameOffsets)
        }
    }

    internal fun scanNameOffsets(buf: ByteArray, maxNames: Int = MIXER_NAME_COUNT): List<Int> =
        (0 until maxNames).mapNotNull { nameOffsetsByStrip(buf)[it] }

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
        nameOffsets: Map<Int, Int>,
    ): Int? {
        if (marker == ICON_MARKER_TYPED) {
            val inputType = mixerState?.let { state ->
                nameOffsets[stripIndex]?.let { offset -> decodeInputType(state, offset) }
            } ?: INPUT_TYPE_DYNAMIC_MIC
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

    internal fun isValidChannelNameRecord(buf: ByteArray, offset: Int): Boolean {
        if (readChannelName(buf, offset) == null || offset < 6) return false
        if ((buf[offset - 1].toInt() and 0xFF) == RECORD_MAGIC) {
            val stripIndex = buf[offset - 3].toInt() and 0xFF
            return stripIndex in 0..2 &&
                (buf[offset - 2].toInt() and 0xFF) == 0 &&
                (buf[offset - 4].toInt() and 0xFF) == 0 &&
                (buf[offset - 5].toInt() and 0xFF) == 0
        }
        val stripIndex = buf[offset - 3].toInt() and 0xFF
        if (stripIndex !in 3..5 ||
            (buf[offset - 5].toInt() and 0xFF) != 0 ||
            (buf[offset - 4].toInt() and 0xFF) != 0
        ) {
            return false
        }
        if ((buf[offset - 2].toInt() and 0xFF) !in 1..2) return false
        // [strip][sub][preset][len] — reject the preset byte mistaken for length.
        if ((buf[offset - 1].toInt() and 0xFF) in 1..2) return false
        return true
    }

    internal fun channelStripIndexFromRecord(buf: ByteArray, offset: Int): Int =
        buf[offset - 3].toInt() and 0xFF

    internal fun scanChannelRecords(buf: ByteArray): Map<Int, String> {
        val records = linkedMapOf<Int, String>()
        var i = 0
        while (i < buf.size) {
            if (isValidChannelNameRecord(buf, i)) {
                val stripIndex = channelStripIndexFromRecord(buf, i)
                val name = readChannelName(buf, i)
                if (name != null && stripIndex in 0 until MIXER_NAME_COUNT) {
                    records[stripIndex] = name
                }
                i += 1 + (buf[i].toInt() and 0xFF)
            } else {
                maybeRecordPresetHeaderName(buf, i, records)
                i++
            }
        }
        return records
    }

    internal fun nameOffsetsByStrip(buf: ByteArray): Map<Int, Int> {
        val offsets = linkedMapOf<Int, Int>()
        var i = 0
        while (i < buf.size) {
            if (isValidChannelNameRecord(buf, i)) {
                val stripIndex = channelStripIndexFromRecord(buf, i)
                if (stripIndex in 0 until MIXER_NAME_COUNT) {
                    offsets[stripIndex] = i
                }
                i += 1 + (buf[i].toInt() and 0xFF)
            } else {
                maybeRecordPresetHeaderOffset(buf, i, offsets)
                i++
            }
        }
        return offsets
    }

    private fun maybeRecordPresetHeaderName(buf: ByteArray, presetOffset: Int, records: MutableMap<Int, String>) {
        val stripIndex = presetHeaderStripIndex(buf, presetOffset) ?: return
        if (records.containsKey(stripIndex)) return
        val nameOffset = presetOffset + 1
        val name = readChannelName(buf, nameOffset) ?: readPrintableAsciiRun(buf, nameOffset) ?: return
        records[stripIndex] = name
    }

    private fun maybeRecordPresetHeaderOffset(buf: ByteArray, presetOffset: Int, offsets: MutableMap<Int, Int>) {
        val stripIndex = presetHeaderStripIndex(buf, presetOffset) ?: return
        if (offsets.containsKey(stripIndex)) return
        val nameOffset = presetOffset + 1
        if (readChannelName(buf, nameOffset) != null || readPrintableAsciiRun(buf, nameOffset) != null) {
            offsets[stripIndex] = nameOffset
        }
    }

    private fun presetHeaderStripIndex(buf: ByteArray, presetOffset: Int): Int? {
        if (presetOffset < 2) return null
        val stripIndex = buf[presetOffset - 2].toInt() and 0xFF
        if (stripIndex !in 3..5) return null
        if ((buf[presetOffset - 1].toInt() and 0xFF) !in 1..2) return null
        return stripIndex
    }

    private fun readPrintableAsciiRun(buf: ByteArray, start: Int, maxLen: Int = 18): String? {
        if (start >= buf.size) return null
        val end = minOf(buf.size, start + maxLen)
        val slice = buf.copyOfRange(start, end).takeWhile { it in 0x20..0x7E }
        if (slice.size < 2) return null
        return String(slice.toByteArray(), Charsets.US_ASCII)
    }

    private fun usesSysexNameRegion(buf: ByteArray): Boolean {
        if (buf.size < SYSEX_NAME_REGION_MIN_SIZE) return false
        if ((buf[0].toInt() and 0xFF) == SYSEX_START) return true
        return readLengthPrefixed(buf, NAMES_START) != null
    }

    private fun orderedNamesFromStripMap(records: Map<Int, String>): List<String> =
        (0 until MIXER_NAME_COUNT).map { records[it].orEmpty() }

    private fun resolvePresetIcon(inputType: Int, preset: Int): Int? =
        PRESET_TO_MS_ICON[inputType to preset]
}
