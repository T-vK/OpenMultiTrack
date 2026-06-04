package org.openmultitrack.usb

import org.openmultitrack.audio.NativeAudioProbe
import org.openmultitrack.domain.audio.AudioDirection
import org.openmultitrack.domain.audio.AudioEndpointProbe
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor

data class FullUsbProbeResult(
    val usb: UsbAudioDeviceDescriptor,
    val input: AudioEndpointProbe?,
    val output: AudioEndpointProbe?,
    val note: String? = null,
)

class UsbAudioProbeService(
    private val enumerator: UsbAudioEnumerator,
) {
    fun probe(usb: UsbAudioDeviceDescriptor): FullUsbProbeResult {
        val deviceId = usb.androidAudioDeviceId
            ?: enumerator.findAndroidAudioDeviceId(usb, input = true)

        if (deviceId == null) {
            return FullUsbProbeResult(
                usb = usb,
                input = null,
                output = null,
                note = "No Android audio device id mapped — grant USB permission and reconnect.",
            )
        }

        val input = runCatching {
            NativeAudioProbe.probe(deviceId, AudioDirection.INPUT)
        }.getOrElse { error ->
            AudioEndpointProbe(
                deviceId = deviceId,
                direction = AudioDirection.INPUT,
                channelCount = 0,
                sampleRate = 0,
                framesPerBurst = 0,
                errorMessage = error.message,
            )
        }

        val output = runCatching {
            NativeAudioProbe.probe(deviceId, AudioDirection.OUTPUT)
        }.getOrElse { error ->
            AudioEndpointProbe(
                deviceId = deviceId,
                direction = AudioDirection.OUTPUT,
                channelCount = 0,
                sampleRate = 0,
                framesPerBurst = 0,
                errorMessage = error.message,
            )
        }

        return FullUsbProbeResult(usb = usb.copy(androidAudioDeviceId = deviceId), input, output)
    }
}
