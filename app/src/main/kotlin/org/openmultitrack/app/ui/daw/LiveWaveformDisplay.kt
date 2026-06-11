package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Tag for instrumented pixel-stability tests. */
const val LIVE_WAVEFORM_TEST_TAG = "live_waveform_strip"

/** Timeline scale for the live recording waveform window. */
internal data class LiveRecordingTimelineView(
    val viewStartSec: Float,
    val viewWindowSec: Float,
    val contentDurationSec: Float,
    val playheadSec: Float,
)

/**
 * Maps [elapsedSec] and the rolling [windowSec] into ruler/waveform viewport coordinates.
 * Growth: 0…window with playhead advancing. Rolling: sliding window ending at playhead.
 */
internal fun liveRecordingTimelineView(
    elapsedSec: Float,
    windowSec: Float,
): LiveRecordingTimelineView {
    if (windowSec <= 0f) {
        return LiveRecordingTimelineView(0f, 1f, 0f, 0f)
    }
    val elapsed = elapsedSec.coerceAtLeast(0f)
    val rolling = elapsed > windowSec
    return if (rolling) {
        LiveRecordingTimelineView(
            viewStartSec = elapsed - windowSec,
            viewWindowSec = windowSec,
            contentDurationSec = windowSec,
            playheadSec = elapsed,
        )
    } else {
        LiveRecordingTimelineView(
            viewStartSec = 0f,
            viewWindowSec = windowSec,
            contentDurationSec = elapsed,
            playheadSec = elapsed,
        )
    }
}

@Composable
internal fun RecordingTimelineRuler(
    elapsedSec: Float,
    windowSec: Float,
    modifier: Modifier = Modifier,
) {
    val timeline = liveRecordingTimelineView(elapsedSec, windowSec)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawTimeRuler(
                viewStartSec = timeline.viewStartSec,
                viewWindowSec = timeline.viewWindowSec,
                contentDurationSec = timeline.contentDurationSec,
            )
            drawPlayheadAndLoop(
                viewStartSec = timeline.viewStartSec,
                viewWindowSec = timeline.viewWindowSec,
                playheadSec = timeline.playheadSec,
                loopStartSec = null,
                loopEndSec = null,
                loopEnabled = false,
            )
        }
    }
}

/**
 * Maps live recording peaks into per-pixel columns with fixed timeline anchors.
 *
 * Growth (elapsed ≤ window): columns fill left-to-right — 1 s of a 15 s window
 * occupies the left 1/15 of the strip. [elapsedSec] controls how far right
 * drawing may extend so a bloated peak buffer cannot zoom the view.
 *
 * Rolling (elapsed > window): the last [windowSec] of audio spans all columns.
 *
 * Column [i] always represents the same moment in the rolling window, so existing
 * columns never shift horizontally as new peaks arrive.
 */
internal fun liveWaveformColumnsForDisplay(
    peaks: FloatArray,
    windowSec: Float,
    elapsedSec: Float,
    peaksPerSec: Int,
    pixelCount: Int,
): FloatArray {
    if (pixelCount <= 0 || windowSec <= 0f || peaksPerSec <= 0) return FloatArray(0)
    val out = FloatArray(pixelCount)
    if (peaks.isEmpty() || elapsedSec <= 0f) return out

    val capacitySlots = (windowSec * peaksPerSec).toInt().coerceAtLeast(1)
    val elapsedSlots = (elapsedSec * peaksPerSec).toInt().coerceAtLeast(0)
    if (elapsedSlots <= 0) return out

    val rolling = elapsedSec > windowSec
    val samples = if (rolling) {
        val count = minOf(peaks.size, capacitySlots)
        peaks.copyOfRange(peaks.size - count, peaks.size)
    } else {
        val activeSlots = elapsedSlots.coerceAtMost(capacitySlots)
        val count = minOf(peaks.size, activeSlots)
        peaks.copyOfRange(0, count)
    }

    for (px in 0 until pixelCount) {
        val slotLo = px * capacitySlots / pixelCount
        val slotHi = (px + 1) * capacitySlots / pixelCount
        var maxPeak = 0f
        for (slot in slotLo until slotHi) {
            if (!rolling && slot >= elapsedSlots) continue
            val sampleIdx = if (rolling) {
                slot - (capacitySlots - samples.size)
            } else {
                slot
            }
            if (sampleIdx in samples.indices) {
                maxPeak = maxOf(maxPeak, samples[sampleIdx])
            }
        }
        out[px] = maxPeak
    }
    return out
}

/** Horizontal anchor for timeline slot [slot] across [widthPx] (stable as recording grows). */
internal fun liveWaveformSlotAnchorX(slot: Int, capacitySlots: Int, widthPx: Float): Float =
    (slot + 0.5f) / capacitySlots.toFloat() * widthPx

/** Index of the rightmost column that has waveform content. */
internal fun liveWaveformRightmostFilledColumn(columns: FloatArray): Int =
    columns.indexOfLast { it > 0.01f }

/** Counts filled pixel columns within the recorded region (for tests). */
internal fun liveWaveformFilledPixelCount(
    columns: FloatArray,
    elapsedSec: Float,
    windowSec: Float,
): Int {
    if (columns.isEmpty() || windowSec <= 0f || elapsedSec <= 0f) return 0
    val rolling = elapsedSec > windowSec
    val contentEnd = if (rolling) {
        columns.size
    } else {
        (columns.size * (elapsedSec / windowSec)).toInt().coerceIn(0, columns.size)
    }
    return columns.take(contentEnd).count { it > 0.01f }
}

/**
 * Simulates consecutive growth frames and returns the first frame index where an already-filled
 * column changes amplitude (horizontal stability violation), or -1 if stable.
 */
internal fun findGrowthColumnStabilityViolation(
    windowSec: Float = 15f,
    peaksPerSec: Int = 30,
    pixelCount: Int = 90,
    frames: Int = 90,
): Int {
    var previous = FloatArray(pixelCount)
    var prevRightmost = -1
    for (frame in 1..frames) {
        val peaks = FloatArray(frame) { 0.6f }
        val elapsed = frame / peaksPerSec.toFloat()
        val columns = liveWaveformColumnsForDisplay(peaks, windowSec, elapsed, peaksPerSec, pixelCount)
        val rightmost = liveWaveformRightmostFilledColumn(columns)
        if (prevRightmost >= 0) {
            for (px in 0..prevRightmost.coerceAtMost(rightmost)) {
                if (kotlin.math.abs(columns[px] - previous[px]) > 0.001f) {
                    return frame
                }
            }
        }
        previous = columns
        prevRightmost = rightmost
    }
    return -1
}

/**
 * Draws one vertical bar per pixel column at a fixed horizontal anchor.
 * Stroke spans the full column width so adjacent samples form a solid trace.
 */
internal fun DrawScope.drawLiveWaveformColumns(
    columns: FloatArray,
    elapsedSec: Float,
    windowSec: Float,
    color: Color,
) {
    if (columns.isEmpty()) return
    val fullW = size.width
    if (fullW <= 0f) return
    val h = size.height
    if (h <= 0f) return
    val rolling = elapsedSec > windowSec
    val contentW = if (rolling) fullW else fullW * (elapsedSec / windowSec).coerceIn(0f, 1f)
    if (contentW <= 0f) return
    val colWidth = fullW / columns.size
    val stroke = colWidth.coerceAtLeast(1f)
    val mid = h / 2f
    val minBar = h * 0.06f
    val barColor = color.copy(alpha = 0.9f)
    for (i in columns.indices) {
        val x = (i + 0.5f) * colWidth
        if (x > contentW) break
        val amp = columns[i].coerceIn(0f, 1f)
        if (amp <= 0f) continue
        val barH = maxOf(amp * h * 0.9f, minBar)
        drawLine(
            color = barColor,
            start = Offset(x, mid - barH / 2f),
            end = Offset(x, mid + barH / 2f),
            strokeWidth = stroke,
        )
    }
}

@Composable
internal fun LiveWaveformStrip(
    peaks: FloatArray,
    windowSec: Float,
    elapsedSec: Float,
    peaksPerSec: Int,
    color: Color,
    normalized: Boolean,
    modifier: Modifier = Modifier,
    testTag: String = LIVE_WAVEFORM_TEST_TAG,
) {
    val hasDrawableData = peaks.isNotEmpty() && windowSec > 0f && elapsedSec > 0f
    // Freeze the scale divisor on the first meaningful peak so a later loud transient
    // cannot rescale already-drawn timeline columns to invisibility.
    var livePeakCeiling by remember { mutableFloatStateOf(0f) }
    val rawMax = peaks.maxOrNull() ?: 0f
    if (livePeakCeiling <= 1e-6f && rawMax > 1e-6f) livePeakCeiling = rawMax
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .testTag(testTag)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(width = 0.5.dp, color = outline, shape = shape),
    ) {
        if (!hasDrawableData) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            if (h <= 0f) return@Canvas
            val capacitySlots = (windowSec * peaksPerSec).toInt().coerceAtLeast(1)
            // Never use more columns than timeline slots — otherwise integer slot binning
            // leaves most columns empty on wide strips (sparse vertical ticks / background gaps).
            val pixelCount = size.width.toInt().coerceIn(1, capacitySlots)
            val scaledPeaks = scalePeaksForLiveDisplay(peaks, normalized, livePeakCeiling)
            val columns = liveWaveformColumnsForDisplay(
                peaks = scaledPeaks,
                windowSec = windowSec,
                elapsedSec = elapsedSec,
                peaksPerSec = peaksPerSec,
                pixelCount = pixelCount,
            )
            if (columns.all { it <= 0f }) return@Canvas
            drawLiveWaveformColumns(
                columns = columns,
                elapsedSec = elapsedSec,
                windowSec = windowSec,
                color = color,
            )
        }
    }
}
