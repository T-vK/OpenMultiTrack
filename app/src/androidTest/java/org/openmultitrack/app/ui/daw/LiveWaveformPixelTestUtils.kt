package org.openmultitrack.app.ui.daw

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs

fun expectedSlotCenterX(slot: Int, capacitySlots: Int, imageWidth: Int): Float =
    (slot + 0.5f) / capacitySlots * imageWidth

/** Slot index at the horizontal center of timeline second [second] (1-based). */
fun slotAtSecondCenter(second: Int, peaksPerSec: Int): Int =
    (second * peaksPerSec - peaksPerSec / 2).coerceAtLeast(0)

fun timelineSecondCenterX(second: Int, capacitySlots: Int, peaksPerSec: Int, imageWidth: Int): Int {
    val slot = slotAtSecondCenter(second, peaksPerSec)
    return expectedSlotCenterX(slot, capacitySlots, imageWidth)
        .toInt()
        .coerceIn(0, imageWidth - 1)
}

/** Samples one pixel at vertical center and the horizontal center of [second] on the timeline. */
fun sampleMiddleWaveformPixel(bitmap: Bitmap, second: Int, capacitySlots: Int, peaksPerSec: Int): Int {
    val x = timelineSecondCenterX(second, capacitySlots, peaksPerSec, bitmap.width)
    return bitmap.getPixel(x, bitmap.height / 2)
}

/** True when any pixel in a vertical band at the timeline marker differs from background. */
fun hasBarAtTimelineSecond(
    bitmap: Bitmap,
    second: Int,
    capacitySlots: Int,
    peaksPerSec: Int,
    backgroundArgb: Int,
): Boolean {
    val x = timelineSecondCenterX(second, capacitySlots, peaksPerSec, bitmap.width)
    val yMid = bitmap.height / 2
    val ySpan = (bitmap.height * 0.22f).toInt().coerceIn(2, 12)
    for (y in (yMid - ySpan)..(yMid + ySpan)) {
        if (y !in 0 until bitmap.height) continue
        if (isWaveformBarPixel(bitmap.getPixel(x, y), backgroundArgb)) return true
    }
    return false
}

data class WaveformTimelineProbe(
    val second: Int,
    val pixel: Int,
    val isBar: Boolean,
)

/**
 * Background color from the unfilled tail of the strip (right of the growth frontier).
 * More reliable than edge sampling while only the left portion contains bars.
 */
fun estimateTrailingWaveformBackground(
    bitmap: Bitmap,
    elapsedSec: Float,
    windowSec: Float,
): Int {
    val filledFraction = (elapsedSec / windowSec).coerceIn(0f, 1f)
    val sampleX = (bitmap.width * (filledFraction + 0.1f).coerceIn(0.12f, 0.94f)).toInt()
        .coerceIn(0, bitmap.width - 1)
    return bitmap.getPixel(sampleX, bitmap.height / 2)
}

fun probeSecondMarker(
    bitmap: Bitmap,
    second: Int,
    capacitySlots: Int,
    peaksPerSec: Int,
    elapsedSec: Float,
    windowSec: Float,
): WaveformTimelineProbe {
    val bg = estimateTrailingWaveformBackground(bitmap, elapsedSec, windowSec)
    val pixel = sampleMiddleWaveformPixel(bitmap, second, capacitySlots, peaksPerSec)
    val isBar = hasBarAtTimelineSecond(bitmap, second, capacitySlots, peaksPerSec, bg)
    return WaveformTimelineProbe(second, pixel, isBar)
}

fun isWaveformBackgroundPixel(pixel: Int, backgroundArgb: Int, tolerance: Int = 18): Boolean =
    colorDistance(pixel, backgroundArgb) < tolerance

fun pixelsMatch(pixelA: Int, pixelB: Int, tolerance: Int = 2): Boolean =
    pixelA == pixelB || colorDistance(pixelA, pixelB) <= tolerance

/**
 * Verifies left-to-right waveform growth using vertical-center pixel probes.
 *
 * - At 0 s: seconds 1–3 are container background (no bars yet).
 * - Each 1 s step: the matching second marker gains a bar; earlier seconds stay pixel-stable.
 * - Seconds beyond the current elapsed time remain background.
 */
fun assertLeftToRightWaveformGrowth(
    framesByElapsedSec: Map<Int, ImageBitmap>,
    capacitySlots: Int,
    peaksPerSec: Int,
    windowSec: Float = 15f,
    maxSecond: Int = 9,
    /** False for live audio with per-buffer normalization (bar height may rescale). */
    requireLockedPixelValues: Boolean = true,
    /** When set (host e2e), frontier checks use peak buffer size instead of wall-clock labels. */
    peakCountByFrame: Map<Int, Int>? = null,
) {
    fun frontierSecond(frameKey: Int): Float {
        val peaks = peakCountByFrame?.get(frameKey)
        return if (peaks != null) peaks / peaksPerSec.toFloat() else frameKey.toFloat()
    }

    fun isBeyondFrontier(second: Int, frameKey: Int): Boolean =
        second.toFloat() > frontierSecond(frameKey) + 0.34f

    fun isAtFrontier(second: Int, frameKey: Int): Boolean =
        kotlin.math.abs(second - frontierSecond(frameKey)) <= 0.34f
    check(framesByElapsedSec.containsKey(0)) { "missing frame at 0 s (before recording)" }
    for (second in 1..3) {
        val probe = probeSecondMarker(
            framesByElapsedSec.getValue(0).asAndroidBitmap(),
            second,
            capacitySlots,
            peaksPerSec,
            elapsedSec = 0f,
            windowSec = windowSec,
        )
        check(!probe.isBar) {
            "second $second should be background before recording (pixel=0x${probe.pixel.toString(16)})"
        }
    }

    var previous = framesByElapsedSec.getValue(0)
    for (elapsed in 1..maxSecond) {
        val current = framesByElapsedSec[elapsed]
            ?: error("missing frame at $elapsed s")
        for (second in 1..maxSecond) {
            val prevProbe = probeSecondMarker(
                previous.asAndroidBitmap(),
                second,
                capacitySlots,
                peaksPerSec,
                elapsedSec = (elapsed - 1).toFloat(),
                windowSec = windowSec,
            )
            val curProbe = probeSecondMarker(
                current.asAndroidBitmap(),
                second,
                capacitySlots,
                peaksPerSec,
                elapsedSec = elapsed.toFloat(),
                windowSec = windowSec,
            )
            when {
                isBeyondFrontier(second, elapsed) -> {
                    check(!curProbe.isBar) {
                        "second $second should be background at ${elapsed}s " +
                            "(frontier=${frontierSecond(elapsed)}s)"
                    }
                }
                isAtFrontier(second, elapsed) -> {
                    check(!prevProbe.isBar) {
                        "second $second should still be background before frontier ${frontierSecond(elapsed)}s"
                    }
                    val appeared = curProbe.isBar || !pixelsMatch(prevProbe.pixel, curProbe.pixel, tolerance = 4)
                    check(appeared) {
                        "second $second should gain waveform pixels at frontier ${frontierSecond(elapsed)}s"
                    }
                }
                second < elapsed -> {
                    val slot = slotAtSecondCenter(second, peaksPerSec)
                    if (requireLockedPixelValues) {
                        check(prevProbe.isBar) {
                            "second $second should already be drawn at ${elapsed - 1}s"
                        }
                        check(curProbe.isBar) {
                            "second $second lost its bar at ${elapsed}s"
                        }
                        check(pixelsMatch(prevProbe.pixel, curProbe.pixel)) {
                            "second $second pixel changed between ${elapsed - 1}s and ${elapsed}s " +
                                "(was 0x${prevProbe.pixel.toString(16)}, " +
                                "now 0x${curProbe.pixel.toString(16)})"
                        }
                    } else {
                        val prevX = measureSlotBarCentroidX(previous, slot, capacitySlots)
                        val curX = measureSlotBarCentroidX(current, slot, capacitySlots)
                        check(prevX != null) {
                            "second $second bar not visible at ${elapsed - 1}s"
                        }
                        check(curX != null) {
                            "second $second bar not visible at ${elapsed}s"
                        }
                        check(kotlin.math.abs(curX!! - prevX!!) < 4.5f) {
                            "second $second bar shifted horizontally from $prevX to $curX px " +
                                "between ${elapsed - 1}s and ${elapsed}s"
                        }
                    }
                }
            }
        }
        previous = current
    }

    val finalFrame = framesByElapsedSec.getValue(maxSecond)
    for (second in 1..maxSecond) {
        if (requireLockedPixelValues) {
            val probe = probeSecondMarker(
                finalFrame.asAndroidBitmap(),
                second,
                capacitySlots,
                peaksPerSec,
                elapsedSec = maxSecond.toFloat(),
                windowSec = windowSec,
            )
            check(probe.isBar) { "second $second should be drawn at ${maxSecond}s" }
        } else {
            val slot = slotAtSecondCenter(second, peaksPerSec)
            check(measureSlotBarCentroidX(finalFrame, slot, capacitySlots) != null) {
                "second $second should be drawn at ${maxSecond}s"
            }
        }
    }
}

fun measureSlotBarCentroidX(
    image: ImageBitmap,
    slot: Int,
    capacitySlots: Int,
): Float? {
    val bitmap = image.asAndroidBitmap()
    val tolerance = (bitmap.width.toFloat() / capacitySlots * 1.5f).toInt().coerceAtLeast(4)
    val expected = expectedSlotCenterX(slot, capacitySlots, bitmap.width)
    val bg = estimateWaveformStripBackground(bitmap)
    return measureBarCentroidX(bitmap, expected, tolerance, bg)
}

/** Estimates the waveform strip background from margins (areas without bars). */
fun estimateWaveformStripBackground(strip: Bitmap): Int {
    val points = listOf(
        strip.getPixel(1.coerceAtMost(strip.width - 1), strip.height / 2),
        strip.getPixel((strip.width - 2).coerceAtLeast(0), strip.height / 2),
        strip.getPixel(strip.width / 2, 1.coerceAtMost(strip.height - 1)),
        strip.getPixel(strip.width / 2, (strip.height - 2).coerceAtLeast(0)),
    )
    val r = points.map { (it shr 16) and 0xFF }.average().toInt()
    val g = points.map { (it shr 8) and 0xFF }.average().toInt()
    val b = points.map { it and 0xFF }.average().toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

fun isWaveformBarPixel(argb: Int, backgroundArgb: Int? = null): Boolean {
    val alpha = argb ushr 24
    if (alpha < 100) return false
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    if (backgroundArgb != null) {
        val bgR = (backgroundArgb shr 16) and 0xFF
        val bgG = (backgroundArgb shr 8) and 0xFF
        val bgB = backgroundArgb and 0xFF
        val delta = abs(red - bgR) + abs(green - bgG) + abs(blue - bgB)
        if (delta >= 18) return true
    }
    val maxChannel = maxOf(red, green, blue)
    val minChannel = minOf(red, green, blue)
    return maxChannel > 45 && (maxChannel - minChannel) > 12
}

/**
 * Finds the horizontal centroid of bar pixels near [nearX]. Returns null when no bar is visible.
 */
fun measureBarCentroidX(
    image: ImageBitmap,
    nearX: Float,
    tolerancePx: Int,
    backgroundArgb: Int? = null,
): Float? = measureBarCentroidX(image.asAndroidBitmap(), nearX, tolerancePx, backgroundArgb)

fun measureBarCentroidX(
    bitmap: Bitmap,
    nearX: Float,
    tolerancePx: Int,
    backgroundArgb: Int? = null,
): Float? {
    val bg = backgroundArgb ?: estimateWaveformStripBackground(bitmap)
    val x0 = (nearX - tolerancePx).toInt().coerceAtLeast(0)
    val x1 = (nearX + tolerancePx).toInt().coerceAtMost(bitmap.width - 1)
    var sumX = 0.0
    var count = 0
    val yStep = (bitmap.height / 8).coerceAtLeast(1)
    var y = yStep
    while (y < bitmap.height - yStep) {
        for (x in x0..x1) {
            if (isWaveformBarPixel(bitmap.getPixel(x, y), bg)) {
                sumX += x
                count++
            }
        }
        y += yStep
    }
    return if (count > 0) (sumX / count).toFloat() else null
}

/** Rightmost x coordinate that contains waveform bar pixels. */
fun waveformRightEdgeX(image: ImageBitmap, backgroundArgb: Int? = null): Int =
    waveformRightEdgeX(image.asAndroidBitmap(), backgroundArgb)

fun waveformRightEdgeX(bitmap: Bitmap, backgroundArgb: Int? = null): Int {
    val bg = backgroundArgb ?: estimateWaveformStripBackground(bitmap)
    val xMargin = (bitmap.width * 0.02f).toInt().coerceIn(2, 8)
    val yMargin = (bitmap.height * 0.12f).toInt().coerceIn(2, 8)
    val yStep = (bitmap.height / 8).coerceAtLeast(1)
    for (x in (bitmap.width - 1 - xMargin) downTo xMargin) {
        var y = yMargin
        var hits = 0
        while (y < bitmap.height - yMargin) {
            if (isWaveformBarPixel(bitmap.getPixel(x, y), bg)) hits++
            y += yStep
        }
        if (hits >= 2) return x
    }
    return -1
}

/** Bar pixels in the strip interior (excludes border chrome). */
fun interiorWaveformBarPixelCount(bitmap: Bitmap, backgroundArgb: Int? = null): Int {
    val bg = backgroundArgb ?: estimateWaveformStripBackground(bitmap)
    val xMargin = (bitmap.width * 0.04f).toInt().coerceIn(3, 12)
    val yMargin = (bitmap.height * 0.15f).toInt().coerceIn(3, 12)
    var count = 0
    for (x in xMargin until bitmap.width - xMargin) {
        for (y in yMargin until bitmap.height - yMargin) {
            if (isWaveformBarPixel(bitmap.getPixel(x, y), bg)) count++
        }
    }
    return count
}

fun assertSlotCentroidStable(
    before: ImageBitmap,
    after: ImageBitmap,
    slot: Int,
    capacitySlots: Int,
    tolerancePx: Float = 2.5f,
) {
    val beforeBitmap = before.asAndroidBitmap()
    val afterBitmap = after.asAndroidBitmap()
    val width = beforeBitmap.width
    val expected = expectedSlotCenterX(slot, capacitySlots, width)
    val searchRadius = (width.toFloat() / capacitySlots * 1.5f).toInt().coerceAtLeast(4)
    val bgBefore = estimateWaveformStripBackground(beforeBitmap)
    val bgAfter = estimateWaveformStripBackground(afterBitmap)
    val beforeX = measureBarCentroidX(beforeBitmap, expected, searchRadius, bgBefore)
    val afterX = measureBarCentroidX(afterBitmap, expected, searchRadius, bgAfter)
    checkNotNull(beforeX) { "slot $slot bar not visible in baseline frame (expected ~$expected px)" }
    checkNotNull(afterX) { "slot $slot bar not visible after growth (expected ~$expected px)" }
    check(abs(afterX - beforeX) <= tolerancePx) {
        "slot $slot centroid moved from $beforeX to $afterX px (allowed ≤ $tolerancePx)"
    }
}

fun Bitmap.asComposeImage(): ImageBitmap = asImageBitmap()

/** True when the strip shows its surface background (empty or with bars). */
fun hasWaveformStripContainer(bitmap: Bitmap): Boolean {
    if (bitmap.width < 4 || bitmap.height < 4) return false
    val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    val alpha = center ushr 24
    if (alpha < 200) return false
    val corners = listOf(
        bitmap.getPixel(1, bitmap.height / 2),
        bitmap.getPixel(bitmap.width - 2, bitmap.height / 2),
        bitmap.getPixel(bitmap.width / 2, 1),
        bitmap.getPixel(bitmap.width / 2, bitmap.height - 2),
    )
    return corners.all { corner ->
        val cornerAlpha = corner ushr 24
        cornerAlpha >= 200 && colorDistance(center, corner) < 40
    }
}

internal fun colorDistance(a: Int, b: Int): Int {
    val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
    val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
    val db = (a and 0xFF) - (b and 0xFF)
    return kotlin.math.abs(dr) + kotlin.math.abs(dg) + kotlin.math.abs(db)
}
