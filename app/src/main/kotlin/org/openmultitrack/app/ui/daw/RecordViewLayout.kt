package org.openmultitrack.app.ui.daw

import kotlin.math.max

/**
 * Live recording waveform viewport layout.
 *
 * When the visible window is wider than recorded audio ([elapsedSec] < [viewWindowSec]),
 * time 0 stays pinned to the left and empty space remains on the right.
 *
 * When the window is narrower than recorded audio, the live edge (end of recording) stays
 * pinned to the right — the viewport shows the last [viewWindowSec] seconds.
 */
object RecordViewLayout {
  private const val MIN_WINDOW_SEC = 1f
  private const val MAX_ZOOM_OUT_SEC = 600f

  fun maxWindowSec(bufferMaxSec: Float, elapsedSec: Float): Float =
      max(max(bufferMaxSec.coerceIn(5f, 120f), MAX_ZOOM_OUT_SEC), elapsedSec.coerceAtLeast(0f))
          .coerceAtLeast(MIN_WINDOW_SEC)

  fun clampWindow(viewWindowSec: Float, bufferMaxSec: Float, elapsedSec: Float): Float {
      val maxWindow = maxWindowSec(bufferMaxSec, elapsedSec)
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
      bufferMaxSec: Float,
  ): Pair<Float, Float> {
      val window = clampWindow(viewWindowSec, bufferMaxSec, elapsedSec)
      val start = anchoredStartSec(elapsedSec, window)
      return start to window
  }
}
