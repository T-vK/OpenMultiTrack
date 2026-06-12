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
    fun clampPlaybackChannels_capsAtStereo() {
        assertThat(Flow8UsbPlaybackProfile.clampPlaybackChannels(10)).isEqualTo(2)
        assertThat(Flow8UsbPlaybackProfile.clampPlaybackChannels(1)).isEqualTo(1)
    }
}
