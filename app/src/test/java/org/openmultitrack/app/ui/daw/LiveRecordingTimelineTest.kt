package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveRecordingTimelineTest {
    @Test
    fun growthPhase_startsAtZero() {
        val view = liveRecordingTimelineView(elapsedSec = 5f, windowSec = 15f)
        assertThat(view.viewStartSec).isEqualTo(0f)
        assertThat(view.viewWindowSec).isEqualTo(15f)
        assertThat(view.contentDurationSec).isEqualTo(5f)
        assertThat(view.playheadSec).isEqualTo(5f)
    }

    @Test
    fun rollingPhase_slidesWindowWithPlayhead() {
        val view = liveRecordingTimelineView(elapsedSec = 42f, windowSec = 15f)
        assertThat(view.viewStartSec).isEqualTo(27f)
        assertThat(view.viewWindowSec).isEqualTo(15f)
        assertThat(view.contentDurationSec).isEqualTo(15f)
        assertThat(view.playheadSec).isEqualTo(42f)
    }
}
