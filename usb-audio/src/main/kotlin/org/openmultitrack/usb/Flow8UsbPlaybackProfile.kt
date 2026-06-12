package org.openmultitrack.usb

import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor

/**
 * FLOW 8 USB playback constraints.
 *
 * The mixer exposes **four** USB playback returns (U01–U04 per UAC2 descriptor).
 * Capture must be fully released before playback to avoid firmware lockup; channel
 * count should follow the probe, not be forced down to stereo.
 */
object Flow8UsbPlaybackProfile {
    const val PRODUCT_ID = 0x050c
    /** USB return channels reported by hardware probe (UAC2 playback alt). */
    const val USB_PLAYBACK_CHANNELS = 4
    const val PRE_PLAYBACK_DELAY_MS = 120L
    const val POST_PLAYBACK_STOP_DELAY_MS = 80L

    fun isFlow8(usb: UsbAudioDeviceDescriptor): Boolean =
        usb.vendorId == BehringerUsbIdentifiers.VENDOR_ID_BEHINGER &&
            usb.productId == PRODUCT_ID

    fun isFlow8(vendorId: Int, productId: Int): Boolean =
        vendorId == BehringerUsbIdentifiers.VENDOR_ID_BEHINGER && productId == PRODUCT_ID

    fun playbackChannelsFromProbe(maxPlaybackChannels: Int): Int =
        when {
            maxPlaybackChannels >= USB_PLAYBACK_CHANNELS -> USB_PLAYBACK_CHANNELS
            maxPlaybackChannels > 0 -> maxPlaybackChannels
            else -> USB_PLAYBACK_CHANNELS
        }
}
