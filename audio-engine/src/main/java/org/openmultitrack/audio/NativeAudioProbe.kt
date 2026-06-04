package org.openmultitrack.audio

import org.openmultitrack.domain.audio.AudioDirection
import org.openmultitrack.domain.audio.AudioEndpointProbe

/** JNI bridge to Oboe for USB / device audio capability probing. */
object NativeAudioProbe {
    init {
        System.loadLibrary("openmultitrack_audio")
    }

    fun probe(deviceId: Int, direction: AudioDirection): AudioEndpointProbe {
        val native = nativeProbe(deviceId, direction == AudioDirection.INPUT)
        return AudioEndpointProbe(
            deviceId = native.deviceId,
            direction = if (native.directionCode == 0) AudioDirection.INPUT else AudioDirection.OUTPUT,
            channelCount = native.channelCount,
            sampleRate = native.sampleRate,
            framesPerBurst = native.framesPerBurst,
            errorMessage = when {
                !native.success -> native.errorMessage ?: "Probe failed"
                else -> null
            },
        )
    }

    private external fun nativeProbe(deviceId: Int, isInput: Boolean): NativeProbeResult
}
