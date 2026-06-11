package org.openmultitrack.app.ui.daw

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs

fun expectedSlotCenterX(slot: Int, capacitySlots: Int, imageWidth: Int): Float =
    (slot + 0.5f) / capacitySlots * imageWidth

fun slotAtSecondCenter(second: Int, peaksPerSec: Int): Int =
    (second * peaksPerSec - peaksPerSec / 2).coerceAtLeast(0)

fun timelineSecondCenterX(second: Int, capacitySlots: Int, peaksPerSec: Int, imageWidth: Int): Int {
    val slot = slotAtSecondCenter(second, peaksPerSec)
    return expectedSlotCenterX(slot, capacitySlots, imageWidth)
        .toInt()
        .coerceIn(0, imageWidth - 1)
}

fun recordedRegionEndX(imageWidth: Int, elapsedSec: Float, windowSec: Float): Int =
    (imageWidth * (elapsedSec / windowSec).coerceIn(0f, 1f)).toInt().coerceIn(0, imageWidth)

const val RECORDING_STABILITY_CHECKS_PER_SEC = 10
const val RECORDING_STABILITY_PROBE_SEC = 5

/** Interior lock boundary: excludes the live growth frontier from monotonic checks. */
fun interiorLockEndX(
    imageWidth: Int,
    elapsedSec: Float,
    windowSec: Float,
    frontierSec: Float = 0.2f,
): Int = recordedRegionEndX(imageWidth, (elapsedSec - frontierSec).coerceAtLeast(0f), windowSec)

data class RecordingProbeFrame(
    val checkIndex: Int,
    val elapsedSec: Float,
    val peakCount: Int,
    val image: ImageBitmap,
)

/**
 * Tracks columns that have ever shown bar pixels in the recorded interior.
 * Once a column has been painted, background must never reappear there.
 */
class RecordedColumnLock(private val width: Int) {
    private val everBar = BooleanArray(width)

    fun lockObserved(occupancy: BooleanArray, upToX: Int, margin: Int) {
        val end = upToX.coerceIn(margin, width)
        for (x in margin until end) {
            if (occupancy[x]) everBar[x] = true
        }
    }

    fun lockedColumnCount(margin: Int = 0): Int =
        (margin until width).count { everBar[it] }

    fun assertNoBackgroundRegression(occupancy: BooleanArray, checkIndex: Int, elapsedSec: Float, margin: Int) {
        val regressions = mutableListOf<Int>()
        for (x in margin until width) {
            if (everBar[x] && !occupancy[x]) regressions.add(x)
        }
        check(regressions.isEmpty()) {
            "check $checkIndex @ ${"%.2f".format(elapsedSec)}s: ${regressions.size} columns regressed " +
                "to background (first x: ${regressions.take(12)}) — " +
                "recorded waveform must never lose bar pixels"
        }
    }
}

/**
 * Runs [checksPerSec] × [probeSec] pixel probes (default 10/s for 5s = 50 checks).
 * Fails when any previously-painted interior column becomes background again.
 */
fun assertRecordingStabilityProbe(
    frames: List<RecordingProbeFrame>,
    windowSec: Float,
    checksPerSec: Int = RECORDING_STABILITY_CHECKS_PER_SEC,
    probeSec: Int = RECORDING_STABILITY_PROBE_SEC,
    requireDenseInterior: Boolean = true,
    minDenseOccupiedFraction: Float = 0.92f,
    maxGapPx: Int = 4,
) {
    val requiredChecks = checksPerSec * probeSec
    check(frames.size >= requiredChecks) {
        "need ≥$requiredChecks checks ($checksPerSec/s for ${probeSec}s), got ${frames.size}"
    }
    val lock = RecordedColumnLock(frames.first().image.asAndroidBitmap().width)
    frames.take(requiredChecks).forEach { frame ->
        val bitmap = frame.image.asAndroidBitmap()
        val margin = waveformInteriorXMargin(bitmap)
        val bg = estimateWaveformStripBackground(bitmap)
        val recordedEnd = recordedRegionEndX(bitmap.width, frame.elapsedSec, windowSec)
        val lockEnd = interiorLockEndX(bitmap.width, frame.elapsedSec, windowSec)
        val occupancy = waveformColumnOccupancy(bitmap, margin, recordedEnd.coerceAtLeast(lockEnd), bg)
        if (lockEnd > margin + 8) {
            lock.assertNoBackgroundRegression(occupancy, frame.checkIndex, frame.elapsedSec, margin)
        }
        if (requireDenseInterior && lockEnd > margin + 20) {
            assertRecordedRegionDense(
                bitmap = bitmap,
                recordedEndX = lockEnd,
                minOccupiedFraction = minDenseOccupiedFraction,
                maxGapPx = maxGapPx,
            )
        }
        lock.lockObserved(occupancy, lockEnd, margin)
    }
}

/**
 * Per-column occupancy: true when the vertical mid-band at [x] contains waveform bar pixels.
 */
fun waveformInteriorXMargin(bitmap: Bitmap): Int =
    (bitmap.width * 0.02f).toInt().coerceIn(2, 12)

fun waveformColumnOccupancy(
    bitmap: Bitmap,
    xStart: Int = 0,
    xEndExclusive: Int = bitmap.width,
    backgroundArgb: Int? = null,
): BooleanArray {
    val bg = backgroundArgb ?: estimateWaveformStripBackground(bitmap)
    val occupancy = BooleanArray(bitmap.width)
    val yMid = bitmap.height / 2
    val ySpan = (bitmap.height * 0.35f).toInt().coerceIn(3, bitmap.height / 2 - 1)
    val margin = waveformInteriorXMargin(bitmap)
    val xEnd = xEndExclusive.coerceIn(0, bitmap.width)
    val xBegin = xStart.coerceAtLeast(margin)
    for (x in xBegin until xEnd) {
        for (y in (yMid - ySpan)..(yMid + ySpan)) {
            if (y !in 0 until bitmap.height) continue
            if (isWaveformBarPixel(bitmap.getPixel(x, y), bg)) {
                occupancy[x] = true
                break
            }
        }
    }
    return occupancy
}

fun maxBackgroundRunInRange(occupancy: BooleanArray, xStart: Int, xEndExclusive: Int): Int {
    var maxRun = 0
    var run = 0
    val xEnd = xEndExclusive.coerceAtMost(occupancy.size)
    for (x in xStart.coerceAtLeast(0) until xEnd) {
        if (!occupancy[x]) {
            run++
        } else {
            maxRun = maxOf(maxRun, run)
            run = 0
        }
    }
    return maxOf(maxRun, run)
}

fun occupiedColumnCount(occupancy: BooleanArray, xStart: Int, xEndExclusive: Int): Int {
    val xEnd = xEndExclusive.coerceAtMost(occupancy.size)
    var count = 0
    for (x in xStart.coerceAtLeast(0) until xEnd) {
        if (occupancy[x]) count++
    }
    return count
}

fun occupiedColumnFraction(occupancy: BooleanArray, xStart: Int, xEndExclusive: Int): Float {
    val width = (xEndExclusive - xStart).coerceAtLeast(1)
    return occupiedColumnCount(occupancy, xStart, xEndExclusive).toFloat() / width
}

/**
 * Recorded region must be densely filled with bar columns (no large background gaps).
 */
fun assertRecordedRegionDense(
    bitmap: Bitmap,
    recordedEndX: Int,
    minOccupiedFraction: Float = 0.92f,
    maxGapPx: Int = 4,
) {
    val margin = waveformInteriorXMargin(bitmap)
    val end = recordedEndX.coerceIn(margin + 1, bitmap.width)
    val bg = estimateWaveformStripBackground(bitmap)
    val occupancy = waveformColumnOccupancy(bitmap, margin, end, bg)
    val fraction = occupiedColumnFraction(occupancy, margin, end)
    check(fraction >= minOccupiedFraction) {
        "recorded region only ${(fraction * 100).toInt()}% column-occupied " +
            "(need ≥${(minOccupiedFraction * 100).toInt()}%) up to x=$end"
    }
    val maxGap = maxBackgroundRunInRange(occupancy, margin, end)
    check(maxGap <= maxGapPx) {
        "background gap of $maxGap px in recorded region (max allowed $maxGapPx)"
    }
}

/**
 * Interior columns (excluding [frontierMarginPx] at the growth edge) must keep the same
 * bar/background occupancy — catches horizontal jiggle and gap flicker.
 */
fun assertRecordedInteriorOccupancyStable(
    before: ImageBitmap,
    after: ImageBitmap,
    interiorEndX: Int,
    frontierMarginPx: Int = 10,
) {
    val beforeBitmap = before.asAndroidBitmap()
    val afterBitmap = after.asAndroidBitmap()
    check(beforeBitmap.width == afterBitmap.width) {
        "width mismatch ${beforeBitmap.width} vs ${afterBitmap.width}"
    }
    val margin = waveformInteriorXMargin(beforeBitmap)
    val end = (interiorEndX - frontierMarginPx).coerceIn(margin + 1, beforeBitmap.width)
    val bgBefore = estimateWaveformStripBackground(beforeBitmap)
    val bgAfter = estimateWaveformStripBackground(afterBitmap)
    val occBefore = waveformColumnOccupancy(beforeBitmap, margin, end, bgBefore)
    val occAfter = waveformColumnOccupancy(afterBitmap, margin, end, bgAfter)
    val flips = mutableListOf<Int>()
    for (x in margin until end) {
        if (occBefore[x] != occAfter[x]) flips.add(x)
    }
    check(flips.isEmpty()) {
        "interior column occupancy flickered at ${flips.size} x-positions " +
            "(first: ${flips.take(8)}, interior end=$end): " +
            "background bleeding through recorded audio is a stability bug"
    }
}

/** Every interior column must stay occupied across frames (continuous tone). */
fun assertRecordedInteriorStaysFilled(
    before: ImageBitmap,
    after: ImageBitmap,
    interiorEndX: Int,
    frontierMarginPx: Int = 10,
) {
    val beforeBitmap = before.asAndroidBitmap()
    val afterBitmap = after.asAndroidBitmap()
    val margin = waveformInteriorXMargin(beforeBitmap)
    val end = (interiorEndX - frontierMarginPx).coerceIn(margin + 1, beforeBitmap.width)
    val bgBefore = estimateWaveformStripBackground(beforeBitmap)
    val bgAfter = estimateWaveformStripBackground(afterBitmap)
    val occBefore = waveformColumnOccupancy(beforeBitmap, margin, end, bgBefore)
    val occAfter = waveformColumnOccupancy(afterBitmap, margin, end, bgAfter)
    for (x in margin until end) {
        check(occBefore[x]) { "column $x was background before growth (x<$end)" }
        check(occAfter[x]) { "column $x became background after growth (x<$end)" }
    }
}

fun sampleMiddleWaveformPixel(bitmap: Bitmap, second: Int, capacitySlots: Int, peaksPerSec: Int): Int {
    val x = timelineSecondCenterX(second, capacitySlots, peaksPerSec, bitmap.width)
    return bitmap.getPixel(x, bitmap.height / 2)
}

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

fun assertLeftToRightWaveformGrowth(
    framesByElapsedSec: Map<Int, ImageBitmap>,
    capacitySlots: Int,
    peaksPerSec: Int,
    windowSec: Float = 15f,
    maxSecond: Int = 9,
    /** Synthetic uniform-tone frames: interior must stay dense and filled. */
    requireDenseInterior: Boolean = true,
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
    var previousElapsed = 0
    for (elapsed in 1..maxSecond) {
        val current = framesByElapsedSec[elapsed]
            ?: error("missing frame at $elapsed s")
        val currentBitmap = current.asAndroidBitmap()
        val prevBitmap = previous.asAndroidBitmap()
        val interiorEndX = recordedRegionEndX(
            prevBitmap.width,
            (elapsed - 1).toFloat(),
            windowSec,
        )
        if (interiorEndX > 12) {
            assertRecordedInteriorOccupancyStable(
                before = previous,
                after = current,
                interiorEndX = interiorEndX,
            )
            if (requireDenseInterior) {
                assertRecordedInteriorStaysFilled(
                    before = previous,
                    after = current,
                    interiorEndX = interiorEndX,
                )
            }
        }
        if (requireDenseInterior && elapsed >= 2) {
            val denseEnd = recordedRegionEndX(currentBitmap.width, elapsed.toFloat(), windowSec)
            if (denseEnd > 20) {
                assertRecordedRegionDense(currentBitmap, denseEnd)
            }
        }
        for (second in 1..maxSecond) {
            val prevProbe = probeSecondMarker(
                prevBitmap,
                second,
                capacitySlots,
                peaksPerSec,
                elapsedSec = (elapsed - 1).toFloat(),
                windowSec = windowSec,
            )
            val curProbe = probeSecondMarker(
                currentBitmap,
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
                    check(curProbe.isBar || !pixelsMatch(prevProbe.pixel, curProbe.pixel, tolerance = 4)) {
                        "second $second should gain waveform pixels at frontier ${frontierSecond(elapsed)}s"
                    }
                }
                second < elapsed -> {
                    check(prevProbe.isBar) {
                        "second $second should already be drawn at ${elapsed - 1}s"
                    }
                    check(curProbe.isBar) {
                        "second $second lost its bar at ${elapsed}s — background bleeding through"
                    }
                }
            }
        }
        previous = current
        previousElapsed = elapsed
    }

    val finalFrame = framesByElapsedSec.getValue(maxSecond)
    val finalBitmap = finalFrame.asAndroidBitmap()
    if (requireDenseInterior) {
        val finalEnd = recordedRegionEndX(finalBitmap.width, maxSecond.toFloat(), windowSec)
        assertRecordedRegionDense(finalBitmap, finalEnd)
    }
    for (second in 1..maxSecond) {
        val probe = probeSecondMarker(
            finalBitmap,
            second,
            capacitySlots,
            peaksPerSec,
            elapsedSec = maxSecond.toFloat(),
            windowSec = windowSec,
        )
        check(probe.isBar) { "second $second should be drawn at ${maxSecond}s" }
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
