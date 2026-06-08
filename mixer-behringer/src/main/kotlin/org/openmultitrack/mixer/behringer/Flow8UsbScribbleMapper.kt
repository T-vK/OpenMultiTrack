package org.openmultitrack.mixer.behringer

/**
 * Maps the seven FLOW 8 mixer strips onto ten USB capture channels.
 *
 * USB 1–8 follow the analog inputs; USB 9–10 are always Main L/R. When Ch5/6 or Ch7/8 are
 * stereo-linked in the mixer, one strip name (and icon) is copied to both USB channels.
 */
object Flow8UsbScribbleMapper {
    const val USB_CHANNELS = 10
    const val MAIN_L_NAME = "Main L"
    const val MAIN_R_NAME = "Main R"

    fun mapSlotsToUsb(slots: List<Flow8StateDecoder.MixerChannelSlot>): List<UsbChannelScribble> {
        require(slots.size >= Flow8StateDecoder.MIXER_SLOT_COUNT) {
            "Expected ${Flow8StateDecoder.MIXER_SLOT_COUNT} mixer slots, got ${slots.size}"
        }

        val byUsb = linkedMapOf<Int, UsbChannelScribble>()

        for (index in 0..3) {
            putSlot(byUsb, usbChannel = index + 1, sourceLabel = "Ch${index + 1}", slot = slots[index])
        }

        val link56 = slots[4].stereoLinked
        val link78 = slots[5].stereoLinked

        if (link56) {
            putSlot(byUsb, 5, "Ch5/6", slots[4])
            putSlot(byUsb, 6, "Ch5/6", slots[4])
        } else {
            putSlot(byUsb, 5, "Ch5", slots[4])
            putSlot(byUsb, 6, "Ch6", slots[5])
        }

        when {
            link78 -> {
                val ch78Slot = if (link56) slots[5] else slots[6]
                putSlot(byUsb, 7, "Ch7/8", ch78Slot)
                putSlot(byUsb, 8, "Ch7/8", ch78Slot)
            }
            link56 -> {
                putSlot(byUsb, 7, "Ch7", slots[5])
                putSlot(byUsb, 8, "Ch8", slots[6])
            }
            else -> putSlot(byUsb, 7, "Ch7", slots[6])
        }

        byUsb[9] = UsbChannelScribble(
            usbChannel = 9,
            sourceLabel = "Main L",
            name = MAIN_L_NAME,
            colorIndex = null,
            iconId = null,
        )
        byUsb[10] = UsbChannelScribble(
            usbChannel = 10,
            sourceLabel = "Main R",
            name = MAIN_R_NAME,
            colorIndex = null,
            iconId = null,
        )

        return byUsb.values.sortedBy { it.usbChannel }
    }

    private fun putSlot(
        out: MutableMap<Int, UsbChannelScribble>,
        usbChannel: Int,
        sourceLabel: String,
        slot: Flow8StateDecoder.MixerChannelSlot,
    ) {
        val name = slot.name?.takeIf { it.isNotBlank() } ?: return
        out[usbChannel] = UsbChannelScribble(
            usbChannel = usbChannel,
            sourceLabel = sourceLabel,
            name = name,
            colorIndex = null,
            iconId = slot.iconId,
        )
    }
}
