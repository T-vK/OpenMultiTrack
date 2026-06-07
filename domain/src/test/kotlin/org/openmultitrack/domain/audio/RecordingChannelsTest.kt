package org.openmultitrack.domain.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordingChannelsTest {
    @Test
    fun fromInputProbe_usesProbedChannelCount() {
        val result = RecordingChannels.fromInputProbe(
            AudioEndpointProbe(
                deviceId = 42,
                direction = AudioDirection.INPUT,
                channelCount = 32,
                sampleRate = 48_000,
                framesPerBurst = 96,
            ),
        )
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().channelCount).isEqualTo(32)
        assertThat(result.getOrThrow().sampleRate).isEqualTo(48_000)
    }

    @Test
    fun fromInputProbe_capsAtMaxChannels() {
        val result = RecordingChannels.fromInputProbe(
            AudioEndpointProbe(
                deviceId = 1,
                direction = AudioDirection.INPUT,
                channelCount = 128,
                sampleRate = 48_000,
                framesPerBurst = 96,
            ),
        )
        assertThat(result.getOrThrow().channelCount).isEqualTo(AudioConstants.MAX_CHANNELS)
    }

    @Test
    fun fromInputProbe_failsWhenProbeFailed() {
        val result = RecordingChannels.fromInputProbe(
            AudioEndpointProbe(
                deviceId = 1,
                direction = AudioDirection.INPUT,
                channelCount = 0,
                sampleRate = 0,
                framesPerBurst = 0,
                errorMessage = "openStream failed",
            ),
        )
        assertThat(result.isFailure).isTrue()
    }
}
