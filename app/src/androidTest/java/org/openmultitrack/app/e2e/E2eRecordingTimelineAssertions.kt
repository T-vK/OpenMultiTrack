package org.openmultitrack.app.e2e

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage

/**
 * Compares on-screen transport clocks and controller state against real wall-clock elapsed time.
 */
object E2eRecordingTimelineAssertions {
    const val RECORDING_TOLERANCE_SEC = 2.5f
    const val PLAYBACK_TOLERANCE_SEC = 1.5f

    fun assertElapsedMatchesWallClock(
        elapsedSec: Float,
        wallSec: Float,
        toleranceSec: Float = RECORDING_TOLERANCE_SEC,
        label: String = "elapsed",
    ) {
        assertWithMessage(
            "$label=$elapsedSec s vs wall=$wallSec s (tolerance=${toleranceSec}s)",
        ).that(elapsedSec).isWithin(toleranceSec).of(wallSec)
    }

    fun assertDurationMatchesWallClock(
        durationSec: Float,
        wallSec: Float,
        toleranceSec: Float = RECORDING_TOLERANCE_SEC,
        label: String = "duration",
    ) {
        assertWithMessage(
            "$label=$durationSec s vs wall=$wallSec s (tolerance=${toleranceSec}s)",
        ).that(durationSec).isWithin(toleranceSec).of(wallSec)
    }

    fun assertUiRecordingMatchesWallClock(wallSec: Float, toleranceSec: Float = RECORDING_TOLERANCE_SEC) {
        val uiElapsed = E2eUiTransport.readRecordingElapsedSec()
        assertElapsedMatchesWallClock(uiElapsed, wallSec, toleranceSec, label = "UI recording")
    }

    fun assertUiSoundcheckDurationMatchesWallClock(wallSec: Float, toleranceSec: Float = RECORDING_TOLERANCE_SEC) {
        val (_, uiDuration) = E2eUiTransport.readSoundcheckTransport()
        assertDurationMatchesWallClock(uiDuration, wallSec, toleranceSec, label = "UI soundcheck duration")
    }

    fun assertUiPlaybackPositionMatchesWallClock(wallSec: Float, toleranceSec: Float = PLAYBACK_TOLERANCE_SEC) {
        val (uiPosition, _) = E2eUiTransport.readSoundcheckTransport()
        assertElapsedMatchesWallClock(uiPosition, wallSec, toleranceSec, label = "UI playback position")
    }

    fun assertDurationsAgree(
        firstSec: Float,
        secondSec: Float,
        toleranceSec: Float = RECORDING_TOLERANCE_SEC,
        firstLabel: String,
        secondLabel: String,
    ) {
        assertWithMessage("$firstLabel=$firstSec s vs $secondLabel=$secondSec s")
            .that(firstSec)
            .isWithin(toleranceSec)
            .of(secondSec)
    }
}
