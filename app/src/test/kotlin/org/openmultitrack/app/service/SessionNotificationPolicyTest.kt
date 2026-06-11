package org.openmultitrack.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.AppMode

class SessionNotificationPolicyTest {
    @Test
    fun recordMode_idleAfterStop_usesNoneNotRecording() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.MULTITRACK_RECORD,
            isRecording = false,
        )
        assertThat(SessionNotificationPolicy.mode(session)).isEqualTo(SessionNotificationMode.NONE)
    }

    @Test
    fun recordMode_activeRecording_usesRecording() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.MULTITRACK_RECORD,
            isRecording = true,
        )
        assertThat(SessionNotificationPolicy.mode(session)).isEqualTo(SessionNotificationMode.RECORDING)
    }

    @Test
    fun recordMode_monitoring_usesRecording() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.MULTITRACK_RECORD,
            isMonitoring = true,
        )
        assertThat(SessionNotificationPolicy.mode(session)).isEqualTo(SessionNotificationMode.RECORDING)
    }

    @Test
    fun statusHint_beforeStateUpdates_usesRecording() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.MULTITRACK_RECORD,
            isRecording = false,
        )
        assertThat(SessionNotificationPolicy.mode(session, "Recording"))
            .isEqualTo(SessionNotificationMode.RECORDING)
    }

    @Test
    fun playbackMode_paused_usesPlayback() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.VIRTUAL_SOUNDCHECK,
            isPlaying = false,
            playbackDurationSec = 120f,
        )
        assertThat(SessionNotificationPolicy.mode(session)).isEqualTo(SessionNotificationMode.PLAYBACK)
    }

    @Test
    fun recordMode_playingBack_usesPlayback() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.MULTITRACK_RECORD,
            isPlaying = true,
            playbackDurationSec = 60f,
        )
        assertThat(SessionNotificationPolicy.mode(session)).isEqualTo(SessionNotificationMode.PLAYBACK)
    }
}
