package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Tag for instrumented pixel-stability tests. */
const val LIVE_WAVEFORM_TEST_TAG = "live_waveform_strip"

/**
 * Maps live recording peaks into fixed timeline slots.
 *
 * Growth: slot [i] is always at horizontal position i / capacity across the window.
 * Only [peaks.size] slots are filled; new audio appends on the right.
 *
 * Rolling: last [window] of peaks span all slots.
 */
internal fun liveWaveformSlotPeaks(
    peaks: FloatArray,
    windowSec: Float,
    elapsedSec: Float,
    peaksPerSec: Int,
): FloatArray {
    if (windowSec <= 0f || peaksPerSec <= 0 || peaks.isEmpty() || elapsedSec <= 0f) {
        return FloatArray(0)
    }
    val capacitySlots = (windowSec * peaksPerSec).toInt().coerceAtLeast(1)
    val rolling = elapsedSec > windowSec
    val out = FloatArray(capacitySlots)
    if (rolling) {
        val count = minOf(peaks.size, capacitySlots)
        val start = peaks.size - count
        for (i in 0 until count) {
            out[i] = peaks[start + i]
        }
    } else {
        val elapsedCap = (elapsedSec * peaksPerSec).toInt().coerceIn(0, capacitySlots)
        val sourceLen = if (peaks.size > elapsedCap + 2) elapsedCap else peaks.size
        val count = minOf(sourceLen, capacitySlots)
        for (i in 0 until count) {
            out[i] = peaks[i]
        }
    }
    return out
}

/** Horizontal anchor for slot [slot] across [widthPx] (stable as new peaks append). */
internal fun liveWaveformSlotAnchorX(slot: Int, capacitySlots: Int, widthPx: Float): Float =
    slot.toFloat() / capacitySlots.toFloat() * widthPx

/**
 * Bins [slotPeaks] into [pixelCount] columns. Slot i always maps to the same column index
 * for a given pixel count, so existing columns do not shift horizontally as new slots arrive.
 */
internal fun liveWaveformPixelColumns(
    slotPeaks: FloatArray,
    pixelCount: Int,
): FloatArray {
    val out = FloatArray(pixelCount)
    if (slotPeaks.isEmpty() || pixelCount <= 0) return out
    val capacity = slotPeaks.size
    slotPeaks.forEachIndexed { slot, peak ->
        if (peak <= 0f) return@forEachIndexed
        val px = (slot.toLong() * pixelCount / capacity).toInt().coerceIn(0, pixelCount - 1)
        out[px] = maxOf(out[px], peak)
    }
    return out
}

/** Index of the rightmost column that has waveform content. */
internal fun liveWaveformRightmostFilledColumn(columns: FloatArray): Int =
    columns.indexOfLast { it > 0.01f }

/** Counts filled pixel columns (for tests). */
internal fun liveWaveformFilledPixelCount(
    slotPeaks: FloatArray,
    pixelCount: Int,
): Int = liveWaveformPixelColumns(slotPeaks, pixelCount).count { it > 0.01f }

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
        val slots = liveWaveformSlotPeaks(peaks, windowSec, elapsed, peaksPerSec)
        val columns = liveWaveformPixelColumns(slots, pixelCount)
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
    if (peaks.isEmpty() || windowSec <= 0f || elapsedSec <= 0f) {
        Box(
            modifier = modifier
                .testTag(testTag)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(3.dp),
                ),
        )
        return
    }

    var lockedWidthPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .testTag(testTag)
            .onSizeChanged { size ->
                if (lockedWidthPx <= 0f && size.width > 0) {
                    lockedWidthPx = size.width.toFloat()
                }
            }
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            if (h <= 0f) return@Canvas
            val drawWidth = lockedWidthPx.takeIf { it > 0f } ?: size.width
            if (drawWidth <= 0f) return@Canvas
            val scaledPeaks = scalePeaksForDisplay(peaks, normalized)
            val slotPeaks = liveWaveformSlotPeaks(
                peaks = scaledPeaks,
                windowSec = windowSec,
                elapsedSec = elapsedSec,
                peaksPerSec = peaksPerSec,
            )
            if (slotPeaks.isEmpty()) return@Canvas
            val capacity = slotPeaks.size
            val slotWidth = drawWidth / capacity
            val mid = h / 2f
            val minBar = h * 0.06f
            val barColor = color.copy(alpha = 0.9f)
            val inset = slotWidth * 0.04f
            slotPeaks.forEachIndexed { slot, peak ->
                val amp = peak.coerceIn(0f, 1f)
                if (amp <= 0.01f) return@forEachIndexed
                val barH = maxOf(amp * h * 0.9f, minBar)
                val x = liveWaveformSlotAnchorX(slot, capacity, drawWidth)
                drawRect(
                    color = barColor,
                    topLeft = Offset(x + inset, mid - barH / 2f),
                    size = Size(
                        (slotWidth - inset * 2f).coerceAtLeast(1f),
                        barH,
                    ),
                )
            }
        }
    }
}
