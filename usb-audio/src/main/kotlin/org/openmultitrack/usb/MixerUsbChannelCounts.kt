package org.openmultitrack.usb

import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.mixer.MixerProfile

/** Resolves USB capture/playback channel counts for UI and transport. */
object MixerUsbChannelCounts {
    fun playbackChannels(probe: FullUsbProbeResult): Int {
        if (isFlow8(probe.usb)) {
            return Flow8UsbPlaybackProfile.USB_PLAYBACK_CHANNELS
        }
        return probe.uac2Caps?.maxPlaybackChannels?.takeIf { it > 0 }
            ?: probe.output?.takeIf { it.isSuccess }?.channelCount
            ?: 2
    }

    /**
     * Playback USB columns for routing matrix / strip pickers.
     * Uses session probe when available; falls back to mixer profile for FLOW 8.
     */
    fun playbackChannelsForUi(
        profile: MixerProfile,
        sessionPlaybackCount: Int,
        probe: FullUsbProbeResult?,
    ): Int {
        if (isFlow8(profile, probe?.usb)) {
            return Flow8UsbPlaybackProfile.USB_PLAYBACK_CHANNELS
        }
        if (sessionPlaybackCount > 0) {
            return sessionPlaybackCount
        }
        if (probe != null) {
            return playbackChannels(probe).coerceAtLeast(1)
        }
        return 1
    }

    fun isFlow8(usb: UsbAudioDeviceDescriptor): Boolean = Flow8UsbPlaybackProfile.isFlow8(usb)

    fun isFlow8(profile: MixerProfile, usb: UsbAudioDeviceDescriptor? = null): Boolean {
        if (usb != null && Flow8UsbPlaybackProfile.isFlow8(usb)) return true
        if (Flow8UsbPlaybackProfile.isFlow8(profile.vendorId, profile.productId)) return true
        val name = "${profile.productName} ${profile.displayName}"
        return name.contains("FLOW 8", ignoreCase = true) ||
            name.contains("FLOW8", ignoreCase = true)
    }
}
