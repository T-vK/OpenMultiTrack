package org.openmultitrack.app.ui.daw

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordViewZoomTest {
    @Test
    fun leftAnchorsWhenElapsedShorterThanWindow() {
        val elapsed = 180f
        val newWindow = 300f
        val start = if (elapsed <= newWindow) 0f else elapsed - newWindow
        assertEquals(0f, start, 0.001f)
    }

    @Test
    fun followsLiveEdgeWhenElapsedLongerThanWindow() {
        val elapsed = 600f
        val newWindow = 120f
        val start = if (elapsed <= newWindow) 0f else elapsed - newWindow
        assertEquals(480f, start, 0.001f)
    }
}
