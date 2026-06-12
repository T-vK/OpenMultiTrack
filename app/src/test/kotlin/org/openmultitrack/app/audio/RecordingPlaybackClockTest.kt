package org.openmultitrack.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.app.service.PlaybackTransportPolicy

class RecordingPlaybackClockTest {
    @Test
    fun transportPositionTracksSampleClock() {
        val sampleRate = 48_000
        val fiveSecondsFrames = sampleRate * 5L
        val positionSec = RecordingTimingAssertions.expectedPositionSec(fiveSecondsFrames, sampleRate)
        RecordingTimingAssertions.assertPlaybackPositionMatchesWallClock(
            positionFrames = fiveSecondsFrames,
            sampleRate = sampleRate,
            wallSec = 5f,
            toleranceSec = 0.05f,
        )
        assertThat(positionSec).isWithin(0.01f).of(5f)
    }

    @Test
    fun playbackEndsNearDuration() {
        val durationSec = 12.5f
        val positionSec = 12.45f
        assertThat(
            PlaybackTransportPolicy.shouldFinishAtEnd(
                positionSec = positionSec,
                durationSec = durationSec,
                loopEnabled = false,
            ),
        ).isTrue()
    }

    @Test
    fun playbackDoesNotEndEarly() {
        assertThat(
            PlaybackTransportPolicy.shouldFinishAtEnd(
                positionSec = 3f,
                durationSec = 12f,
                loopEnabled = false,
            ),
        ).isFalse()
    }
}
