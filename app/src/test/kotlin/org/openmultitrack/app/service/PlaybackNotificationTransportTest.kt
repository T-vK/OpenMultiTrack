package org.openmultitrack.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.sessionio.session.SessionTrackmark

class PlaybackNotificationTransportTest {
    @Test
    fun playingSnapshot_usesPlayerPositionAndDuration() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            appMode = AppMode.SIMPLE_PLAY,
            isPlaying = true,
            playbackPositionSec = 45f,
            playbackDurationSec = 90f,
        )
        val snap = PlaybackNotificationTransport.snapshot(session)
        assertThat(snap.positionMs).isEqualTo(45_000L)
        assertThat(snap.durationMs).isEqualTo(90_000L)
        assertThat(snap.customActionIds).containsExactly(PlaybackNotificationTransport.CUSTOM_ACTION_STOP)
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
        val snap = PlaybackNotificationTransport.snapshot(session)
        assertThat(snap.standardActions and android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .isNotEqualTo(0L)
        assertThat(snap.standardActions and android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            .isNotEqualTo(0L)
    }

    @Test
    fun formatTimestamp_showsElapsedAndTotal() {
        assertThat(PlaybackNotificationTransport.formatTimestamp(90f, 180f))
            .isEqualTo("1:30 / 3:00")
    }
}
