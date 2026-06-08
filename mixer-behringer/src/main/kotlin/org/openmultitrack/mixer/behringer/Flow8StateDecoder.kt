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
