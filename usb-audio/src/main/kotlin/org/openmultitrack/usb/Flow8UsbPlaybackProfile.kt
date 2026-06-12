package org.openmultitrack.usb

import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor

/**
 * FLOW 8 USB playback constraints.
 *
 * Hardware validation: multichannel UAC2 playback from Android can leave the mixer
 * in a broken output state until power cycle. Stereo USB return 1/2 is safe.
 */
object Flow8UsbPlaybackProfile {
    const val PRODUCT_ID = 0x050c
    const val PREFERRED_PLAYBACK_CHANNELS = 2
    const val PRE_PLAYBACK_DELAY_MS = 120L
    const val POST_PLAYBACK_STOP_DELAY_MS = 80L

    fun isFlow8(usb: UsbAudioDeviceDescriptor): Boolean =
        usb.vendorId == BehringerUsbIdentifiers.VENDOR_ID_BEHINGER &&
            usb.productId == PRODUCT_ID

    fun isFlow8(vendorId: Int, productId: Int): Boolean =
        vendorId == BehringerUsbIdentifiers.VENDOR_ID_BEHINGER && productId == PRODUCT_ID

    fun clampPlaybackChannels(requested: Int): Int =
        minOf(requested, PREFERRED_PLAYBACK_CHANNELS).coerceAtLeast(1)
}
