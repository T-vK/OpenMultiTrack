package org.openmultitrack.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.AppMode

class PlaybackNotificationContentTest {
    @Test
    fun sessionTitle_prefersLibraryTitle() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            selectedSoundcheckDir = "/data/sessions/gig-2026-03-14",
            soundcheckSessions = listOf(
                SoundcheckSessionItem(
                    sessionDir = "/data/sessions/gig-2026-03-14",
                    title = "Gig 2026-03-14",
                    startedAtEpochMs = 0L,
                    durationSec = 125f,
                    channelCount = 8,
                ),
            ),
        )
        assertThat(PlaybackNotificationContent.sessionTitle(session)).isEqualTo("Gig 2026-03-14")
    }

    @Test
    fun sessionTitle_fallsBackToFolderName() {
        val session = MixerSessionUiState(
            mixerId = "m1",
            selectedSoundcheckDir = "/data/sessions/my-take-01",
        )
        assertThat(PlaybackNotificationContent.sessionTitle(session)).isEqualTo("my-take-01")
    }

    @Test
    fun progressAndTotalFormatting() {
        assertThat(PlaybackNotificationTransport.formatTimestamp(30f, 125f)).isEqualTo("0:30 / 2:05")
        assertThat(PlaybackNotificationTransport.formatClock(125f)).isEqualTo("2:05")
    }
}
