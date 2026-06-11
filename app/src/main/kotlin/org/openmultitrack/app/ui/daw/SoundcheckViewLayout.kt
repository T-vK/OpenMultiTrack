package org.openmultitrack.app.ui.daw

import kotlin.math.min

object SoundcheckViewLayout {
    private const val MIN_WINDOW_SEC = 5f
    private const val DEFAULT_MIN_SEC = 30f
    private const val DEFAULT_MAX_SEC = 600f

    /** Fit short sessions to their full length; otherwise use the configured default window. */
    fun initialWindowSec(defaultWindowSec: Float, durationSec: Float): Float {
        val default = defaultWindowSec.coerceIn(DEFAULT_MIN_SEC, DEFAULT_MAX_SEC)
        if (durationSec <= 0f) return default
        return min(default, durationSec).coerceAtLeast(MIN_WINDOW_SEC)
    }
}
