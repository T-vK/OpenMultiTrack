package org.openmultitrack.domain.audio

/** Resolves how many channels to record from a USB probe result. */
object RecordingChannels {
    data class Resolved(
        val channelCount: Int,
        val sampleRate: Int,
        val deviceId: Int,
    )

    fun fromInputProbe(input: AudioEndpointProbe?): Result<Resolved> {
        if (input == null) {
            return Result.failure(IllegalStateException("Probe USB input channels before recording."))
        }
        if (!input.isSuccess) {
            return Result.failure(
                IllegalStateException(input.errorMessage ?: "Input probe failed"),
            )
        }
        val channels = input.channelCount.coerceIn(AudioConstants.MIN_CHANNELS, AudioConstants.MAX_CHANNELS)
        return Result.success(
            Resolved(
                channelCount = channels,
                sampleRate = input.sampleRate.coerceAtLeast(8_000),
                deviceId = input.deviceId,
            ),
        )
    }
}
