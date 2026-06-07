package org.openmultitrack.usb

/** USB vendor/product heuristics for Behringer/Midas mixers (UNVERIFIED PIDs — extend on hardware). */
object BehringerUsbIdentifiers {
    const val VENDOR_ID_BEHINGER = 0x1397

    private val nameHints = listOf("X32", "X18", "XR18", "FLOW", "UAX", "MIXER", "BEHRINGER")

    fun isLikelyBehringerMixer(vendorId: Int, productName: String?): Boolean {
        if (vendorId != VENDOR_ID_BEHINGER) return false
        val name = productName?.uppercase() ?: return true
        return nameHints.any { name.contains(it) }
    }

    fun guessModel(productName: String?): String? {
        val name = productName?.uppercase() ?: return null
        return when {
            name.contains("FLOW") -> "FLOW8"
            name.contains("XR18") || name.contains("X18") -> "XR18"
            name.contains("X32") -> "X32"
            else -> null
        }
    }
}
