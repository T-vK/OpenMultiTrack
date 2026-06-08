package org.openmultitrack.app.scribble

import org.openmultitrack.domain.mixer.MixerProfile

/** Which mixers can load channel strip labels from the device (OSC/LAN or FLOW 8 BLE). */
object ScribbleImportSupport {
    private const val XR18_PRODUCT_ID = 0x00d4
    private const val FLOW8_PRODUCT_ID = 0x050c

    fun supports(profile: MixerProfile): Boolean =
        supportsOsc(profile) || supportsFlow8(profile)

    fun supportsOsc(profile: MixerProfile): Boolean {
        if (profile.productId == XR18_PRODUCT_ID) return true
        val name = "${profile.productName} ${profile.displayName}"
        return name.contains("XR18", ignoreCase = true) ||
            name.contains("X18", ignoreCase = true) ||
            name.contains("X32", ignoreCase = true)
    }

    fun supportsFlow8(profile: MixerProfile): Boolean {
        if (profile.productId == FLOW8_PRODUCT_ID) return true
        val name = "${profile.productName} ${profile.displayName}"
        return name.contains("FLOW 8", ignoreCase = true) || name.contains("FLOW8", ignoreCase = true)
    }
}
