package org.openmultitrack.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.sessionio.session.SessionTrackmark

class SessionNotificationTransportTest {
    @Test
    fun recordingSnapshot_pinsPlayheadAtGrowingDuration() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.MULTITRACK_RECORD,
            isRecording = true,
            recordElapsedSec = 65f,
        )
        val snap = SessionNotificationTransport.snapshot(session)
        assertThat(snap.positionMs).isEqualTo(65_000L)
        assertThat(snap.durationMs).isEqualTo(65_000L)
        assertThat(snap.customActionIds).containsExactly(SessionNotificationTransport.CUSTOM_ACTION_STOP)
        assertThat(snap.standardActions and android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE)
            .isNotEqualTo(0L)
        assertThat(snap.standardActions and android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO)
            .isEqualTo(0L)
    }

    @Test
    fun playingSnapshot_hasSeekPauseStopAndDuration() {
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
        assertThat(snap.standardActions and android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO)
            .isNotEqualTo(0L)
    }

    @Test
    fun soundcheckWithChapters_usesStandardSkipActions() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.VIRTUAL_SOUNDCHECK,
            isPlaying = true,
            playbackPositionSec = 1f,
            playbackDurationSec = 60f,
            trackmarks = listOf(SessionTrackmark(index = 1, startSec = 5f, title = "A")),
        )
        val snap = SessionNotificationTransport.snapshot(session)
        assertThat(snap.standardActions and android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .isNotEqualTo(0L)
        assertThat(snap.standardActions and android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            .isNotEqualTo(0L)
    }

    @Test
    fun formatTimestamp_showsElapsedAndTotal() {
        assertThat(SessionNotificationTransport.formatTimestamp(90f, 180f))
            .isEqualTo("1:30 / 3:00")
    }
}
