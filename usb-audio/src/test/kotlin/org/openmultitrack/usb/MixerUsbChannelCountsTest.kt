package org.openmultitrack.usb

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.mixer.MixerProfile

class MixerUsbChannelCountsTest {
    private val flow8Usb = UsbAudioDeviceDescriptor(
        deviceName = "/dev/bus/usb/001/002",
        vendorId = BehringerUsbIdentifiers.VENDOR_ID_BEHINGER,
        productId = Flow8UsbPlaybackProfile.PRODUCT_ID,
        manufacturerName = "Behringer",
        productName = "FLOW 8",
        serialNumber = "abc",
        isLikelyBehringerMixer = true,
        guessedModel = "FLOW8",
        androidAudioDeviceId = null,
    )

    private val flow8Profile = MixerProfile(
        id = "flow8",
        usbDeviceName = flow8Usb.deviceName,
        vendorId = flow8Usb.vendorId,
        productId = flow8Usb.productId,
        serialNumber = "abc",
        productName = "FLOW 8",
        displayName = "Flow 8",
    )

    @Test
    fun playbackChannelsForUi_flow8BeforeProbe_showsFourReturns() {
        assertThat(
            MixerUsbChannelCounts.playbackChannelsForUi(
                profile = flow8Profile,
                sessionPlaybackCount = 0,
                probe = null,
            ),
        ).isEqualTo(4)
    }

    @Test
    fun playbackChannelsForUi_flow8IgnoresStaleSessionCount() {
        assertThat(
            MixerUsbChannelCounts.playbackChannelsForUi(
                profile = flow8Profile,
                sessionPlaybackCount = 1,
                probe = null,
            ),
        ).isEqualTo(4)
    }

    @Test
    fun playbackChannels_flow8IgnoresOboeStereoFallback() {
        val probe = FullUsbProbeResult(
            usb = flow8Usb,
            input = null,
            output = org.openmultitrack.domain.audio.AudioEndpointProbe(
                deviceId = 1,
                direction = org.openmultitrack.domain.audio.AudioDirection.OUTPUT,
                channelCount = 1,
                sampleRate = 48_000,
                framesPerBurst = 96,
            ),
            uac2Caps = null,
        )
        assertThat(MixerUsbChannelCounts.playbackChannels(probe)).isEqualTo(4)
    }
}
