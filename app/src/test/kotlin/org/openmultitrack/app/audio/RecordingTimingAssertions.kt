package org.openmultitrack.app.audio

import com.google.common.truth.Truth.assertThat

/**
 * Shared tolerances for recording / soundcheck / playback timing checks.
 */
object RecordingTimingAssertions {
    const val DEFAULT_TOLERANCE_SEC = 1.5f
    const val PLAYBACK_TOLERANCE_SEC = 1.0f

    fun assertElapsedMatchesWallClock(
        elapsedSec: Float,
        wallSec: Float,
        toleranceSec: Float = DEFAULT_TOLERANCE_SEC,
    ) {
        assertThat(elapsedSec)
            .isWithin(toleranceSec)
            .of(wallSec)
    }

    fun assertDurationMatchesWallClock(
        durationSec: Float,
        wallSec: Float,
        toleranceSec: Float = DEFAULT_TOLERANCE_SEC,
    ) {
        assertThat(durationSec)
            .isWithin(toleranceSec)
            .of(wallSec)
    }

    fun assertCaptureKeepsUp(
        snapshot: CaptureSessionEngine.RecordingTimingSnapshot,
        wallSec: Float,
        toleranceSec: Float = DEFAULT_TOLERANCE_SEC,
    ) {
        assertElapsedMatchesWallClock(snapshot.capturedSec, wallSec, toleranceSec)
        assertThat(snapshot.droppedFrames).isEqualTo(0)
    }

    fun assertDiskMatchesCapture(
        snapshot: CaptureSessionEngine.RecordingTimingSnapshot,
        diskDurationSec: Float,
        toleranceSec: Float = DEFAULT_TOLERANCE_SEC,
    ) {
        assertThat(diskDurationSec)
            .isWithin(toleranceSec)
            .of(snapshot.capturedSec)
    }

    fun expectedPositionSec(positionFrames: Long, sampleRate: Int): Float =
        if (sampleRate > 0) positionFrames.toFloat() / sampleRate else 0f

    fun assertPlaybackPositionMatchesWallClock(
        positionFrames: Long,
        sampleRate: Int,
        wallSec: Float,
        toleranceSec: Float = PLAYBACK_TOLERANCE_SEC,
    ) {
        val positionSec = expectedPositionSec(positionFrames, sampleRate)
        assertThat(positionSec)
            .isWithin(toleranceSec)
            .of(wallSec.coerceAtLeast(0f))
    }
}
