package org.openmultitrack.mixer.behringer

/**
 * Decodes FLOW 8 channel names from a BLE MixerState buffer.
 *
 * The mixer always returns six names: Ch1–4, then Ch5+6, then Ch7+8.
 */
object Flow8StateDecoder {
    const val MIXER_NAME_COUNT = 6

    private val FIXED_OFFSETS = intArrayOf(
        0x0554, 0x0572, 0x0590, 0x05AE, 0x05CC, 0x05EA,
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
     * Parses the 48-byte ParamQuery `0x80` payload (12 groups × 4 bytes).
     *
     * FLOW 8 encodes icons as a two-byte marker/code pair per strip, not a raw
     * Mixing Station id in the first byte. Byte `0x03` is a type prefix (not icon 3).
     */
    fun parseIconConfig(payload: ByteArray, maxStrips: Int = MIXER_NAME_COUNT): List<Int?> {
        val groups = payload.size / 4
        return (0 until minOf(maxStrips, groups)).map { index ->
            val base = index * 4
            val marker = payload[base].toInt() and 0xFF
            val code = payload[base + 1].toInt() and 0xFF
            decodeBleIconGroup(index, marker, code)
        }
    }

    internal fun decodeBleIconGroup(stripIndex: Int, marker: Int, code: Int): Int? {
        if (marker == ICON_MARKER_TYPED) {
            return when (code) {
                ICON_CODE_HANDHELD_MIC -> MixingStationIcons.HANDHELD_MIC
                ICON_CODE_MIC_OR_PLAYBACK -> if (stripIndex == 5) {
                    MixingStationIcons.PC
                } else {
                    MixingStationIcons.HANDHELD_MIC
                }
                else -> code.takeIf { it in 1..MixingStationIcons.MAX_ID }
            }
        }
        if (marker == ICON_MARKER_PLAIN) {
            return when (code) {
                ICON_CODE_ELECTRIC_BASS -> MixingStationIcons.ELECTRIC_BASS
                ICON_CODE_VIOLIN -> MixingStationIcons.VIOLIN
                else -> code.takeIf { it in 1..MixingStationIcons.MAX_ID }
            }
        }
        return when {
            marker in 1..MixingStationIcons.MAX_ID -> marker
            code in 1..MixingStationIcons.MAX_ID -> code
            else -> null
        }
    }

    private const val ICON_MARKER_TYPED = 0x03
    private const val ICON_MARKER_PLAIN = 0x00
    private const val ICON_CODE_HANDHELD_MIC = 0x04
    private const val ICON_CODE_MIC_OR_PLAYBACK = 0x07
    private const val ICON_CODE_ELECTRIC_BASS = 0x02
    private const val ICON_CODE_VIOLIN = 0x04

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
