package org.openmultitrack.app.audio

import org.openmultitrack.domain.audio.AudioDirection
import org.openmultitrack.domain.audio.AudioEndpointProbe
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.mixer.VirtualMixer
import org.openmultitrack.usb.FullUsbProbeResult

/** Fake USB probe for the built-in demo band mixer. */
object VirtualMixerProbe {
    fun usbDescriptor(): UsbAudioDeviceDescriptor = UsbAudioDeviceDescriptor(
        deviceName = "virtual:demo",
        vendorId = VirtualMixer.VENDOR_ID,
        productId = VirtualMixer.PRODUCT_ID_DEMO,
        manufacturerName = "OpenMultiTrack",
        productName = "OMT Demo",
        serialNumber = "virtual-demo",
        isLikelyBehringerMixer = false,
        guessedModel = "DemoBand",
        androidAudioDeviceId = null,
    )

    fun demoProbeResult(): FullUsbProbeResult {
        val usb = usbDescriptor()
        val ch = VirtualMixer.DEMO_CHANNEL_COUNT
        val rate = VirtualMixer.SAMPLE_RATE_HZ
        val input = AudioEndpointProbe(
            deviceId = -1,
            direction = AudioDirection.INPUT,
            channelCount = ch,
            sampleRate = rate,
            framesPerBurst = 96,
        )
        val output = AudioEndpointProbe(
            deviceId = -1,
            direction = AudioDirection.OUTPUT,
            channelCount = 2,
            sampleRate = rate,
            framesPerBurst = 96,
        )
        return FullUsbProbeResult(
            usb = usb,
            input = input,
            output = output,
            uac2Caps = null,
            note = "Virtual demo band — no USB hardware",
        )
    }
}
