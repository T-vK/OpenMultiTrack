package org.openmultitrack.app.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecordingNotificationIdsTest {
    @Test
    fun idFor_assignsStableDistinctIdsPerMixer() {
        val first = RecordingNotificationIds.idFor("mixer-a")
        val second = RecordingNotificationIds.idFor("mixer-b")
        val firstAgain = RecordingNotificationIds.idFor("mixer-a")
        assertThat(firstAgain).isEqualTo(first)
        assertThat(second).isNotEqualTo(first)
        assertThat(first).isAtLeast(RecordingNotificationIds.PLAYBACK_ID - 99)
        assertThat(first).isLessThan(RecordingNotificationIds.PLAYBACK_ID)
    }

    @Test
    fun release_removesTrackedMixer() {
        RecordingNotificationIds.idFor("mixer-release")
        assertThat(RecordingNotificationIds.trackedMixerIds()).contains("mixer-release")
        RecordingNotificationIds.release("mixer-release")
        assertThat(RecordingNotificationIds.trackedMixerIds()).doesNotContain("mixer-release")
    }
}
