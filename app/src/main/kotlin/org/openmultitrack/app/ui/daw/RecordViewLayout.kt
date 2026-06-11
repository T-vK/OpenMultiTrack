package org.openmultitrack.app.ui.daw

import kotlin.math.max
import kotlin.math.min

/**
 * Live recording waveform viewport layout.
 *
 * When the visible window is wider than recorded audio ([elapsedSec] < [viewWindowSec]),
 * time 0 stays pinned to the left and empty space remains on the right.
 *
 * When the window is narrower than recorded audio, the live edge (end of recording) stays
 * pinned to the right — the viewport shows the last [viewWindowSec] seconds.
 *
 * The zoom window is user-controlled only; it is not shrunk automatically as recording
 * progresses. [maxWindowSec] caps manual zoom-out at retained peak history.
 */
object RecordViewLayout {
  const val MIN_HISTORY_SEC = 15f
  const val MAX_HISTORY_SEC = 600f
  private const val MIN_WINDOW_SEC = 1f

  /** Widest manual zoom-out allowed (peak history retained in memory). */
  fun maxWindowSec(historySec: Float): Float =
      historySec.coerceIn(MIN_HISTORY_SEC, MAX_HISTORY_SEC)

  fun clampWindow(viewWindowSec: Float, historySec: Float): Float {
      val maxWindow = maxWindowSec(historySec)
      return viewWindowSec.coerceIn(MIN_WINDOW_SEC, maxWindow)
  }

  /** Viewport start time for a given zoom window and elapsed recording length. */
  fun anchoredStartSec(elapsedSec: Float, viewWindowSec: Float): Float {
      val elapsed = elapsedSec.coerceAtLeast(0f)
      val window = viewWindowSec.coerceAtLeast(MIN_WINDOW_SEC)
      return if (elapsed <= window) 0f else elapsed - window
  }

  fun layout(
      elapsedSec: Float,
      viewWindowSec: Float,
      historySec: Float,
  ): Pair<Float, Float> {
      val window = clampWindow(viewWindowSec, historySec)
      val start = anchoredStartSec(elapsedSec, window)
      return start to window
  }
}
