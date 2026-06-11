package org.openmultitrack.app.service

/** Playback transport rules shared by the mixer controller and notification layer. */
object PlaybackTransportPolicy {
    const val END_TOLERANCE_SEC = 0.1f

    fun shouldFinishAtEnd(
        positionSec: Float,
        durationSec: Float,
        loopEnabled: Boolean,
        toleranceSec: Float = END_TOLERANCE_SEC,
    ): Boolean =
        durationSec > 0f && !loopEnabled && positionSec >= durationSec - toleranceSec

    fun shouldFinishAtEnd(
        positionFrames: Long,
        durationFrames: Long,
        loopEnabled: Boolean,
        toleranceFrames: Long,
    ): Boolean =
        durationFrames > 0L && !loopEnabled && positionFrames >= durationFrames - toleranceFrames
}
