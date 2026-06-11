package org.openmultitrack.app.service

import org.openmultitrack.domain.session.isPlaybackMode

enum class SessionNotificationMode {
    RECORDING,
    PLAYBACK,
    NONE,
}

/** Chooses which foreground notification (if any) matches live session transport state. */
object SessionNotificationPolicy {
    fun mode(session: MixerSessionUiState?, statusHint: String? = null): SessionNotificationMode {
        if (session?.isRecording == true || session?.isMonitoring == true) {
            return SessionNotificationMode.RECORDING
        }
        statusHint?.let { hint ->
            when {
                hint.contains("record", ignoreCase = true) -> return SessionNotificationMode.RECORDING
                hint.contains("monitor", ignoreCase = true) -> return SessionNotificationMode.RECORDING
                hint.contains("playback", ignoreCase = true) -> return SessionNotificationMode.PLAYBACK
            }
        }
        if (session?.appMode?.isPlaybackMode == true || session?.isPlaying == true) {
            return SessionNotificationMode.PLAYBACK
        }
        return SessionNotificationMode.NONE
    }
}
