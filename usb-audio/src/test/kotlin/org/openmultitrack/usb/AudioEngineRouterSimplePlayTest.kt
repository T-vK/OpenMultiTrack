package org.openmultitrack.usb

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor

class AudioEngineRouterSimplePlayTest {
    @Test
    fun resolveOboePlaybackRoute_returnsNullWhenOboeMissing() {
        val probe = FullUsbProbeResult(
            usb = UsbAudioDeviceDescriptor(
                deviceName = "x",
                vendorId = 1,
                productId = 2,
                manufacturerName = null,
                productName = null,
                serialNumber = null,
                isLikelyBehringerMixer = false,
                guessedModel = null,
                androidAudioDeviceId = null,
            ),
            input = null,
            output = null,
            uac2Caps = null,
        )
        assertThat(AudioEngineRouter.resolveOboePlaybackRoute(probe)).isNull()
    }
}
