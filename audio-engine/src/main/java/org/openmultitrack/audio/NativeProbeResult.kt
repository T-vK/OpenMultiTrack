package org.openmultitrack.audio

/**
 * JNI result from Oboe stream probe.
 *
 * @param deviceId AAudio device id.
 * @param directionCode 0 = input, 1 = output.
 * @param success Whether probe opened and queried a stream.
 * @param channelCount Reported channel count.
 * @param sampleRate Reported sample rate.
 * @param framesPerBurst Burst size for buffer tuning.
 * @param reserved Reserved for future flags.
 * @param errorMessage Null on success.
 */
data class NativeProbeResult(
    val deviceId: Int,
    val directionCode: Int,
    val success: Boolean,
    val channelCount: Int,
    val sampleRate: Int,
    val framesPerBurst: Int,
    val reserved: Int,
    val errorMessage: String?,
)
