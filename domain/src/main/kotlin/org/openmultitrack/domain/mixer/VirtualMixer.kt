package org.openmultitrack.domain.mixer

/** Built-in demo band signal generator (no USB hardware). */
object VirtualMixer {
    const val VENDOR_ID = 0x4F4D
    const val PRODUCT_ID_DEMO = 0x5453
    val DEMO_CHANNEL_COUNT: Int get() = DemoBandChannels.specs.size
    const val SAMPLE_RATE_HZ = 48_000
    const val DISPLAY_NAME = "Demo"

    fun isDemoMixer(profile: MixerProfile): Boolean =
        profile.vendorId == VENDOR_ID && profile.productId == PRODUCT_ID_DEMO
}
