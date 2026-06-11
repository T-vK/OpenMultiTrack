package org.openmultitrack.app.ui.daw

import org.junit.Assert.assertEquals
import org.junit.Test

class SoundcheckViewLayoutTest {
    @Test
    fun shortSessionUsesFullDuration() {
        assertEquals(45f, SoundcheckViewLayout.initialWindowSec(defaultWindowSec = 180f, durationSec = 45f), 0.001f)
    }

    @Test
    fun longSessionUsesDefaultWindow() {
        assertEquals(180f, SoundcheckViewLayout.initialWindowSec(defaultWindowSec = 180f, durationSec = 600f), 0.001f)
    }

    @Test
    fun maxWindowNeverExceedsDuration() {
        assertEquals(45f, SoundcheckViewLayout.maxWindowSec(45f), 0.001f)
    }

    @Test
    fun clampAllowsZoomInBelowThirtySeconds() {
        assertEquals(5f, SoundcheckViewLayout.clampWindow(5f, 45f), 0.001f)
    }

    @Test
    fun clampCapsZoomOutAtDuration() {
        assertEquals(45f, SoundcheckViewLayout.clampWindow(600f, 45f), 0.001f)
    }
}
