package org.openmultitrack.app.ui.daw

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordViewLayoutTest {
    @Test
    fun leftAnchorsWhenWindowWiderThanRecording() {
        assertEquals(0f, RecordViewLayout.anchoredStartSec(elapsedSec = 180f, viewWindowSec = 300f), 0.001f)
    }

    @Test
    fun rightAnchorsWhenWindowNarrowerThanRecording() {
        assertEquals(480f, RecordViewLayout.anchoredStartSec(elapsedSec = 600f, viewWindowSec = 120f), 0.001f)
    }

    @Test
    fun atEqualityAnchorsLeft() {
        assertEquals(0f, RecordViewLayout.anchoredStartSec(elapsedSec = 120f, viewWindowSec = 120f), 0.001f)
    }

    @Test
    fun layoutKeepsFollowWhileZoomingOutInLeftAnchorZone() {
        val (start, window) = RecordViewLayout.layout(
            elapsedSec = 60f,
            viewWindowSec = 300f,
            bufferMaxSec = 15f,
        )
        assertEquals(0f, start, 0.001f)
        assertEquals(300f, window, 0.001f)
    }

    @Test
    fun zoomOutCanExceedElapsedUpToMax() {
        val max = RecordViewLayout.maxWindowSec(bufferMaxSec = 15f, elapsedSec = 60f)
        assertEquals(600f, max, 0.001f)
    }
}
