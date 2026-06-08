package org.openmultitrack.mixer.behringer

/**
 * Decodes FLOW 8 mixer channel slots from a BLE/SysEx state buffer (firmware v11749).
 *
 * Each of the seven mixer strips has a 30-byte slot (stride `0x1E`) starting at `0x0554`.
 * Names are length-prefixed ASCII; icon id and stereo-link live in the trailing config bytes
 * (not in the name suffix used on X-Air mixers).
 */
object Flow8StateDecoder {
    const val NAMES_START = 0x0554
    const val NAMES_STRIDE = 0x1E
    const val NAME_SCAN_LEN = 14
    const val MIXER_SLOT_COUNT = 7

    /** Config byte within each 30-byte slot — bit 0 set when the strip is stereo-linked. */
    const val SLOT_FLAGS_OFFSET = 0x0E
    const val STEREO_LINK_FLAG_MASK = 0x01

    /** Icon id (1–74) stored separately from the name string. */
    const val SLOT_ICON_OFFSET = 0x0F

    data class MixerChannelSlot(
        val mixerIndex: Int,
        val name: String?,
        val iconId: Int?,
        val stereoLinked: Boolean,
    )

    fun decodeSlots(buf: ByteArray, iconConfig: ByteArray? = null): List<MixerChannelSlot> {
        val iconsFromQuery = iconConfig?.let(::parseIconConfig)
        return (0 until MIXER_SLOT_COUNT).map { index ->
            val slotOffset = NAMES_START + index * NAMES_STRIDE
            val slotEnd = minOf(slotOffset + NAMES_STRIDE, buf.size)
            val slotBytes = if (slotOffset < buf.size) {
                buf.copyOfRange(slotOffset, slotEnd)
            } else {
                byteArrayOf()
            }
            val name = readLengthPrefixed(buf, slotOffset)
            val flags = slotBytes.getOrNull(SLOT_FLAGS_OFFSET)?.toInt()?.and(0xFF) ?: 0
            val iconFromSlot = readIconId(slotBytes)
            val iconFromQuery = iconsFromQuery?.getOrNull(index)
            MixerChannelSlot(
                mixerIndex = index,
                name = name,
                iconId = iconFromQuery ?: iconFromSlot,
                stereoLinked = (flags and STEREO_LINK_FLAG_MASK) != 0,
            )
        }
    }

    /**
     * Optional BLE parameter `0x80` response (48 bytes = 12 × 4-byte channel groups).
     * Icon ids are stored in the third byte of each group for the first seven mixer strips.
     */
    fun parseIconConfig(payload: ByteArray): List<Int?> {
        return (0 until MIXER_SLOT_COUNT).map { index ->
            val groupOffset = index * 4 + 2
            if (groupOffset >= payload.size) return@map null
            val icon = payload[groupOffset].toInt() and 0xFF
            icon.takeIf { it in 1..MixingStationIcons.MAX_ID }
        }
    }

    internal fun readLengthPrefixed(buf: ByteArray, offset: Int): String? {
        if (offset >= buf.size) return null
        val len = buf[offset].toInt() and 0xFF
        if (len !in 2..18 || offset + 1 + len > buf.size) return null
        val slice = buf.copyOfRange(offset + 1, offset + 1 + len)
        if (!slice.all { it in 0x20..0x7E }) return null
        return String(slice, Charsets.US_ASCII)
    }

    private fun readIconId(slotBytes: ByteArray): Int? {
        val direct = slotBytes.getOrNull(SLOT_ICON_OFFSET)?.toInt()?.and(0xFF)
        if (direct != null && direct in 1..MixingStationIcons.MAX_ID) return direct
        for (i in NAME_SCAN_LEN until slotBytes.size) {
            val value = slotBytes[i].toInt() and 0xFF
            if (value in 1..MixingStationIcons.MAX_ID) return value
        }
        return null
    }
}
