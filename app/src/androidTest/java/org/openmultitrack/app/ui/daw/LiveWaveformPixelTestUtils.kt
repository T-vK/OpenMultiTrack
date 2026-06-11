package org.openmultitrack.app.ui.daw

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs

fun expectedSlotCenterX(slot: Int, capacitySlots: Int, imageWidth: Int): Float =
    (slot + 0.5f) / capacitySlots * imageWidth

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
    val yStep = (bitmap.height / 8).coerceAtLeast(1)
    for (x in bitmap.width - 1 downTo 0) {
        var y = yStep
        while (y < bitmap.height - yStep) {
            if (isWaveformBarPixel(bitmap.getPixel(x, y), bg)) return x
            y += yStep
        }
    }
    return -1
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

private fun colorDistance(a: Int, b: Int): Int {
    val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
    val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
    val db = (a and 0xFF) - (b and 0xFF)
    return kotlin.math.abs(dr) + kotlin.math.abs(dg) + kotlin.math.abs(db)
}
