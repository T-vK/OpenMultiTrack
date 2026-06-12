package org.openmultitrack.usb

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor

class Flow8UsbPlaybackProfileTest {
    private val flow8 = UsbAudioDeviceDescriptor(
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

    @Test
    fun isFlow8_matchesProductId() {
        assertThat(Flow8UsbPlaybackProfile.isFlow8(flow8)).isTrue()
        assertThat(Flow8UsbPlaybackProfile.isFlow8(0x1397, 0x00d4)).isFalse()
    }

    @Test
    fun playbackChannelsFromProbe_usesFourUsbReturns() {
        assertThat(Flow8UsbPlaybackProfile.playbackChannelsFromProbe(4)).isEqualTo(4)
        assertThat(Flow8UsbPlaybackProfile.playbackChannelsFromProbe(10)).isEqualTo(4)
        assertThat(Flow8UsbPlaybackProfile.playbackChannelsFromProbe(0)).isEqualTo(4)
    }
}
