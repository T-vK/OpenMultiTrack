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
 * Maps recording time into ruler/waveform viewport coordinates.
 * [viewStartSec] and [viewWindowSec] come from the interactive viewport (pinch/pan).
 */
internal fun liveRecordingTimelineView(
    elapsedSec: Float,
    viewStartSec: Float,
    viewWindowSec: Float,
): LiveRecordingTimelineView {
    if (viewWindowSec <= 0f) {
        return LiveRecordingTimelineView(0f, 1f, 0f, 0f)
    }
    val elapsed = elapsedSec.coerceAtLeast(0f)
    val start = viewStartSec.coerceAtLeast(0f)
    val window = viewWindowSec
    val visibleEnd = start + window
    val contentEnd = minOf(elapsed, visibleEnd)
    val contentDuration = (contentEnd - start).coerceAtLeast(0f)
    return LiveRecordingTimelineView(
        viewStartSec = start,
        viewWindowSec = window,
        contentDurationSec = contentDuration,
        playheadSec = elapsed,
    )
}

@Composable
internal fun RecordingTimelineRuler(
    elapsedSec: Float,
    viewStartSec: Float,
    viewWindowSec: Float,
    modifier: Modifier = Modifier,
) {
    val timeline = liveRecordingTimelineView(elapsedSec, viewStartSec, viewWindowSec)
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
        }
    }
}

internal fun liveWaveformEffectiveElapsedSec(
    elapsedSec: Float,
    peaks: FloatArray,
    peaksPerSec: Int,
): Float {
    val fromPeaks = if (peaks.isNotEmpty()) {
        peaks.size.toFloat() / peaksPerSec.coerceAtLeast(1)
    } else {
        0f
    }
    return maxOf(elapsedSec, fromPeaks)
}

/**
 * Maps live recording peaks into per-pixel columns for the visible viewport.
 * Column [i] represents a fixed slice of [viewWindowSec] starting at [viewStartSec].
 */
internal fun liveWaveformColumnsForDisplay(
    peaks: FloatArray,
    bufferWindowSec: Float,
    elapsedSec: Float,
    peaksPerSec: Int,
    pixelCount: Int,
    viewStartSec: Float = 0f,
    viewWindowSec: Float = bufferWindowSec,
): FloatArray {
    if (pixelCount <= 0 || viewWindowSec <= 0f || peaksPerSec <= 0) return FloatArray(0)
    val out = FloatArray(pixelCount)
    if (peaks.isEmpty() || elapsedSec <= 0f) return out

    for (px in 0 until pixelCount) {
        val tLo = viewStartSec + px * viewWindowSec / pixelCount
        val tHi = viewStartSec + (px + 1) * viewWindowSec / pixelCount
        out[px] = peakInTimeRange(
            peaks = peaks,
            elapsedSec = elapsedSec,
            bufferWindowSec = bufferWindowSec,
            peaksPerSec = peaksPerSec,
            startSec = tLo,
            endSec = tHi,
        )
    }
    return out
}

internal fun peakInTimeRange(
    peaks: FloatArray,
    elapsedSec: Float,
    bufferWindowSec: Float,
    peaksPerSec: Int,
    startSec: Float,
    endSec: Float,
): Float {
    if (peaks.isEmpty() || elapsedSec <= 0f || endSec <= startSec) return 0f
    val step = 1f / peaksPerSec
    var t = startSec
    var maxPeak = 0f
    while (t < endSec) {
        maxPeak = maxOf(maxPeak, peakAtElapsedTime(peaks, elapsedSec, bufferWindowSec, peaksPerSec, t))
        t += step
    }
    return maxPeak
}

internal fun peakAtElapsedTime(
    peaks: FloatArray,
    elapsedSec: Float,
    bufferWindowSec: Float,
    peaksPerSec: Int,
    timeSec: Float,
): Float {
    if (timeSec < 0f || timeSec > elapsedSec || peaks.isEmpty()) return 0f
    val rolling = elapsedSec > bufferWindowSec
    val index = if (rolling) {
        val oldestSec = elapsedSec - peaks.size.toFloat() / peaksPerSec
        ((timeSec - oldestSec) * peaksPerSec).toInt()
    } else {
        (timeSec * peaksPerSec).toInt()
    }
    return peaks.getOrNull(index) ?: 0f
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
    viewStartSec: Float,
    viewWindowSec: Float,
): Int {
    if (columns.isEmpty() || viewWindowSec <= 0f || elapsedSec <= 0f) return 0
    val contentEndSec = minOf(elapsedSec, viewStartSec + viewWindowSec)
    if (contentEndSec <= viewStartSec) return 0
    val contentEndPx = (
        columns.size * ((contentEndSec - viewStartSec) / viewWindowSec)
        ).toInt().coerceIn(0, columns.size)
    return columns.take(contentEndPx).count { it > 0.01f }
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
        val columns = liveWaveformColumnsForDisplay(
            peaks = peaks,
            bufferWindowSec = windowSec,
            elapsedSec = elapsed,
            peaksPerSec = peaksPerSec,
            pixelCount = pixelCount,
            viewStartSec = if (elapsed > windowSec) elapsed - windowSec else 0f,
            viewWindowSec = windowSec,
        )
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
    color: Color,
) {
    if (columns.isEmpty()) return
    val fullW = size.width
    if (fullW <= 0f) return
    val h = size.height
    if (h <= 0f) return
    val colWidth = fullW / columns.size
    val stroke = colWidth.coerceAtLeast(1f)
    val mid = h / 2f
    val minBar = h * 0.06f
    val barColor = color.copy(alpha = 0.9f)
    for (i in columns.indices) {
        val amp = columns[i].coerceIn(0f, 1f)
        if (amp <= 0f) continue
        val x = (i + 0.5f) * colWidth
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
    bufferWindowSec: Float,
    elapsedSec: Float,
    peaksPerSec: Int,
    color: Color,
    normalized: Boolean,
    viewStartSec: Float = 0f,
    viewWindowSec: Float = bufferWindowSec,
    modifier: Modifier = Modifier,
    testTag: String = LIVE_WAVEFORM_TEST_TAG,
) {
    val hasDrawableData = peaks.isNotEmpty() && viewWindowSec > 0f && elapsedSec > 0f
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
            val capacitySlots = (bufferWindowSec * peaksPerSec).toInt().coerceAtLeast(1)
            // Never use more columns than timeline slots — otherwise integer slot binning
            // leaves most columns empty on wide strips (sparse vertical ticks / background gaps).
            val pixelCount = size.width.toInt().coerceIn(1, capacitySlots)
            val scaledPeaks = scalePeaksForLiveDisplay(peaks, normalized, livePeakCeiling)
            val columns = liveWaveformColumnsForDisplay(
                peaks = scaledPeaks,
                bufferWindowSec = bufferWindowSec,
                elapsedSec = elapsedSec,
                peaksPerSec = peaksPerSec,
                pixelCount = pixelCount,
                viewStartSec = viewStartSec,
                viewWindowSec = viewWindowSec,
            )
            if (columns.all { it <= 0f }) return@Canvas
            drawLiveWaveformColumns(columns = columns, color = color)
        }
    }
}
