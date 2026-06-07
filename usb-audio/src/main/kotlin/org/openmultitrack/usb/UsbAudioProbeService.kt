package org.openmultitrack.usb

import org.openmultitrack.audio.NativeAudioProbe
import org.openmultitrack.audio.NativeUac2DeviceCaps
import org.openmultitrack.audio.NativeUac2Probe
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.AudioDirection
import org.openmultitrack.domain.audio.AudioEndpointProbe
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor

data class FullUsbProbeResult(
    val usb: UsbAudioDeviceDescriptor,
    val input: AudioEndpointProbe?,
    val output: AudioEndpointProbe?,
    val uac2Caps: NativeUac2DeviceCaps? = null,
    val note: String? = null,
)

class UsbAudioProbeService(
    private val enumerator: UsbAudioEnumerator,
) {
    fun probe(usb: UsbAudioDeviceDescriptor): FullUsbProbeResult {
        OmtLog.i("Probe", "probe ${usb.productName} cachedAudioId=${usb.androidAudioDeviceId}")
        val uac2Caps = probeUac2Descriptor(usb)

        val deviceId = usb.androidAudioDeviceId
            ?: enumerator.findAndroidAudioDeviceId(usb, input = true)

        if (deviceId == null) {
            OmtLog.w("Probe", "no device id for ${usb.productName}")
            return FullUsbProbeResult(
                usb = usb,
                input = null,
                output = null,
                uac2Caps = uac2Caps,
                note = buildNote(
                    uac2Caps = uac2Caps,
                    fallback = "No Android audio device id mapped — grant USB permission and reconnect.",
                ),
            )
        }

        val input = runCatching {
            NativeAudioProbe.probe(deviceId, AudioDirection.INPUT)
        }.getOrElse { error ->
            OmtLog.e("Probe", "input probe exception", error)
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
            OmtLog.e("Probe", "output probe exception", error)
            AudioEndpointProbe(
                deviceId = deviceId,
                direction = AudioDirection.OUTPUT,
                channelCount = 0,
                sampleRate = 0,
                framesPerBurst = 0,
                errorMessage = error.message,
            )
        }

        OmtLog.i(
            "Probe",
            "result deviceId=$deviceId input=${input.channelCount}ch/${input.sampleRate}Hz " +
                "output=${output.channelCount}ch/${output.sampleRate}Hz " +
                "uac2=${uac2Caps?.maxCaptureChannels ?: 0}in/${uac2Caps?.maxPlaybackChannels ?: 0}out",
        )
        return FullUsbProbeResult(
            usb = usb.copy(androidAudioDeviceId = deviceId),
            input = input,
            output = output,
            uac2Caps = uac2Caps,
            note = buildNote(uac2Caps = uac2Caps, oboeInputChannels = input.channelCount),
        )
    }

    private fun probeUac2Descriptor(usb: UsbAudioDeviceDescriptor): NativeUac2DeviceCaps? {
        val raw = enumerator.getRawConfigDescriptor(usb.deviceName) ?: run {
            OmtLog.d("Probe", "UAC2: no raw descriptor for ${usb.productName} (permission?)")
            return null
        }
        val caps = NativeUac2Probe.parseConfigDescriptor(raw) ?: run {
            OmtLog.w("Probe", "UAC2: parse returned null for ${usb.productName}")
            return null
        }
        if (!caps.parseOk) {
            OmtLog.w("Probe", "UAC2: parse failed for ${usb.productName}")
            return caps
        }
        OmtLog.i(
            "Probe",
            "UAC2 descriptor ${usb.productName}: v${caps.uacVersion} " +
                "capture=${caps.maxCaptureChannels}ch (${caps.captureAlts.size} alts) " +
                "playback=${caps.maxPlaybackChannels}ch (${caps.playbackAlts.size} alts)",
        )
        caps.captureAlts.forEach { alt ->
            OmtLog.d(
                "Probe",
                "  capture alt=${alt.alternateSetting} ep=0x${alt.endpointAddress.toString(16)} " +
                    "${alt.channels}ch @ ${alt.sampleRateHz}Hz ${alt.bitResolution}-bit",
            )
        }
        caps.playbackAlts.forEach { alt ->
            OmtLog.d(
                "Probe",
                "  playback alt=${alt.alternateSetting} ep=0x${alt.endpointAddress.toString(16)} " +
                    "${alt.channels}ch @ ${alt.sampleRateHz}Hz ${alt.bitResolution}-bit",
            )
        }
        return caps
    }

    private fun buildNote(
        uac2Caps: NativeUac2DeviceCaps?,
        oboeInputChannels: Int = 0,
        fallback: String? = null,
    ): String? {
        val uac2In = uac2Caps?.maxCaptureChannels ?: 0
        val uac2Out = uac2Caps?.maxPlaybackChannels ?: 0
        return when {
            uac2In > oboeInputChannels && oboeInputChannels > 0 ->
                "Oboe reports ${oboeInputChannels}ch but USB descriptor shows ${uac2In}ch capture " +
                    "(UAC2 host path needed for multichannel)."
            uac2In > 0 && oboeInputChannels == 0 && uac2Caps?.parseOk == true ->
                "Descriptor: ${uac2In}ch capture / ${uac2Out}ch playback (Oboe path unavailable)."
            uac2In > 0 && uac2Caps?.parseOk == true ->
                "Descriptor: ${uac2In}ch capture / ${uac2Out}ch playback."
            else -> fallback
        }
    }
}
