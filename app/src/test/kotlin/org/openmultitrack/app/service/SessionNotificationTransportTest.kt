package org.openmultitrack.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.sessionio.session.SessionTrackmark

class SessionNotificationTransportTest {
    @Test
    fun recordingSnapshot_hasPauseAndCustomStop_withElapsedPosition() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.MULTITRACK_RECORD,
            isRecording = true,
            recordElapsedSec = 65f,
        )
        val snap = SessionNotificationTransport.snapshot(session)
        assertThat(snap.positionMs).isEqualTo(65_000L)
        assertThat(snap.durationMs).isNull()
        assertThat(snap.customActionIds).containsExactly(SessionNotificationTransport.CUSTOM_ACTION_STOP)
        assertThat(snap.standardActions and android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE)
            .isNotEqualTo(0L)
        assertThat(snap.showTime).isTrue()
    }

    @Test
    fun playingSnapshot_hasPauseStopAndDuration() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.SIMPLE_PLAY,
            isPlaying = true,
            playbackPositionSec = 12f,
            playbackDurationSec = 180f,
        )
        val snap = SessionNotificationTransport.snapshot(session)
        assertThat(snap.positionMs).isEqualTo(12_000L)
        assertThat(snap.durationMs).isEqualTo(180_000L)
        assertThat(snap.customActionIds).containsExactly(SessionNotificationTransport.CUSTOM_ACTION_STOP)
    }

    @Test
    fun soundcheckWithChapters_ordersCustomActionsForCompactStop() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.VIRTUAL_SOUNDCHECK,
            isPlaying = true,
            playbackPositionSec = 1f,
            playbackDurationSec = 60f,
            trackmarks = listOf(SessionTrackmark(index = 1, startSec = 5f, title = "A")),
        )
        val snap = SessionNotificationTransport.snapshot(session)
        assertThat(snap.customActionIds).containsExactly(
            SessionNotificationTransport.CUSTOM_ACTION_PREVIOUS,
            SessionNotificationTransport.CUSTOM_ACTION_STOP,
            SessionNotificationTransport.CUSTOM_ACTION_NEXT,
        ).inOrder()
        assertThat(snap.standardActions and android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .isEqualTo(0L)
    }

    @Test
    fun formatTimestamp_unknownTotal_showsPlaceholder() {
        assertThat(SessionNotificationTransport.formatTimestamp(45f, null, unknownTotal = true))
            .isEqualTo("0:45 / --:--")
        assertThat(SessionNotificationTransport.formatTimestamp(90f, 180f))
            .isEqualTo("1:30 / 3:00")
    }
}
