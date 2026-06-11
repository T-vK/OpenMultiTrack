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
    fun layoutClampsZoomOutToRetainedHistory() {
        val (start, window) = RecordViewLayout.layout(
            elapsedSec = 60f,
            viewWindowSec = 300f,
            historySec = 15f,
        )
        assertEquals(45f, start, 0.001f)
        assertEquals(15f, window, 0.001f)
    }

    @Test
    fun zoomOutNeverExceedsHistoryOrElapsed() {
        assertEquals(15f, RecordViewLayout.maxWindowSec(historySec = 15f, elapsedSec = 60f), 0.001f)
        assertEquals(60f, RecordViewLayout.maxWindowSec(historySec = 120f, elapsedSec = 60f), 0.001f)
        assertEquals(120f, RecordViewLayout.maxWindowSec(historySec = 120f, elapsedSec = 300f), 0.001f)
    }
}
