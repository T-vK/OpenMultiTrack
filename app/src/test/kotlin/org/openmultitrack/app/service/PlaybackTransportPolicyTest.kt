package org.openmultitrack.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackTransportPolicyTest {
    @Test
    fun shouldFinishAtEnd_whenNearDurationAndNotLooping() {
        assertThat(
            PlaybackTransportPolicy.shouldFinishAtEnd(
                positionSec = 179.95f,
                durationSec = 180f,
                loopEnabled = false,
            ),
        ).isTrue()
    }

    @Test
    fun shouldFinishAtEnd_falseWhenLooping() {
        assertThat(
            PlaybackTransportPolicy.shouldFinishAtEnd(
                positionSec = 180f,
                durationSec = 180f,
                loopEnabled = true,
            ),
        ).isFalse()
    }

    @Test
    fun shouldFinishAtEnd_falseWhenStillFarFromEnd() {
        assertThat(
            PlaybackTransportPolicy.shouldFinishAtEnd(
                positionSec = 90f,
                durationSec = 180f,
                loopEnabled = false,
            ),
        ).isFalse()
    }
}
