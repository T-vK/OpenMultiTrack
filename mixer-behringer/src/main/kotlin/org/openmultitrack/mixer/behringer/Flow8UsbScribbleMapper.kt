package org.openmultitrack.mixer.behringer

/**
 * Maps the six FLOW 8 mixer names onto ten USB capture channels.
 *
 * Names 1–4 → USB 1–4; name 5 → USB 5+6; name 6 → USB 7+8; USB 9–10 are always Main L/R.
 */
object Flow8UsbScribbleMapper {
    const val USB_CHANNELS = 10
    const val MAIN_L_NAME = "Main L"
    const val MAIN_R_NAME = "Main R"

    fun mapNamesToUsb(names: List<String>, icons: List<Int?>? = null): List<UsbChannelScribble> {
        val byUsb = linkedMapOf<Int, UsbChannelScribble>()

        for (index in 0 until 4) {
            putName(
                byUsb,
                usbChannel = index + 1,
                sourceLabel = "Ch${index + 1}",
                name = names.getOrNull(index),
                iconId = icons?.getOrNull(index),
            )
        }

        names.getOrNull(4)?.takeIf { it.isNotBlank() }?.let { name ->
            val iconId = icons?.getOrNull(4)
            putName(byUsb, 5, "Ch5/6", name, iconId)
            putName(byUsb, 6, "Ch5/6", name, iconId)
        }

        names.getOrNull(5)?.takeIf { it.isNotBlank() }?.let { name ->
            val iconId = icons?.getOrNull(5)
            putName(byUsb, 7, "Ch7/8", name, iconId)
            putName(byUsb, 8, "Ch7/8", name, iconId)
        }

        byUsb[9] = UsbChannelScribble(
            9,
            "Main L",
            MAIN_L_NAME,
            colorIndex = null,
            iconId = MixingStationIcons.SPEAKER_LEFT,
        )
        byUsb[10] = UsbChannelScribble(
            10,
            "Main R",
            MAIN_R_NAME,
            colorIndex = null,
            iconId = MixingStationIcons.SPEAKER_RIGHT,
        )

        return byUsb.values.sortedBy { it.usbChannel }
    }

    private fun putName(
        out: MutableMap<Int, UsbChannelScribble>,
        usbChannel: Int,
        sourceLabel: String,
        name: String?,
        iconId: Int? = null,
    ) {
        val label = name?.takeIf { it.isNotBlank() } ?: return
        out[usbChannel] = UsbChannelScribble(usbChannel, sourceLabel, label, colorIndex = null, iconId = iconId)
    }
}
