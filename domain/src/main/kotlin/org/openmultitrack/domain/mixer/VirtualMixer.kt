package org.openmultitrack.domain.mixer

/** Built-in test signal generator (no USB hardware). */
object VirtualMixer {
    const val VENDOR_ID = 0x4F4D
    const val PRODUCT_ID_SINE = 0x5453
    const val SINE_CHANNEL_COUNT = 8
    const val SAMPLE_RATE_HZ = 48_000
    const val DISPLAY_NAME = "Test Signal (sine)"

    fun isSineGenerator(profile: MixerProfile): Boolean =
        profile.vendorId == VENDOR_ID && profile.productId == PRODUCT_ID_SINE
}
