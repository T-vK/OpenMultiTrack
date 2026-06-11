package org.openmultitrack.app.ui.daw

import kotlin.math.min

object SoundcheckViewLayout {
    /** Narrowest zoom (most detail). */
    const val MIN_WINDOW_SEC = 1f

    /** Fallback max when session duration is not known yet. */
    const val FALLBACK_MAX_SEC = 600f

    /** Widest zoom is always the full track — never show empty space past the audio end. */
    fun maxWindowSec(durationSec: Float): Float =
        if (durationSec > 0f) durationSec.coerceAtLeast(MIN_WINDOW_SEC) else FALLBACK_MAX_SEC

    fun clampWindow(windowSec: Float, durationSec: Float): Float =
        windowSec.coerceIn(MIN_WINDOW_SEC, maxWindowSec(durationSec))

    /** Fit short sessions to their full length; otherwise use the configured default window. */
    fun initialWindowSec(defaultWindowSec: Float, durationSec: Float): Float {
        val default = defaultWindowSec.coerceIn(MIN_WINDOW_SEC, FALLBACK_MAX_SEC)
        if (durationSec <= 0f) return default
        return min(default, maxWindowSec(durationSec))
    }
}
