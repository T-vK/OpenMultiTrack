package org.openmultitrack.app.ui.daw

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.abs

internal fun expectedSlotCenterX(slot: Int, capacitySlots: Int, imageWidth: Int): Float =
    (slot + 0.5f) / capacitySlots * imageWidth

/**
 * Finds the horizontal centroid of bar pixels near [nearX]. Returns null when no bar is visible.
 */
internal fun measureBarCentroidX(
    image: ImageBitmap,
    nearX: Float,
    tolerancePx: Int,
): Float? {
    val bitmap = image.asAndroidBitmap()
    val x0 = (nearX - tolerancePx).toInt().coerceAtLeast(0)
    val x1 = (nearX + tolerancePx).toInt().coerceAtMost(bitmap.width - 1)
    var sumX = 0.0
    var count = 0
    val yStep = (bitmap.height / 8).coerceAtLeast(1)
    var y = yStep
    while (y < bitmap.height - yStep) {
        for (x in x0..x1) {
            if (isWaveformBarPixel(bitmap.getPixel(x, y))) {
                sumX += x
                count++
            }
        }
        y += yStep
    }
    return if (count > 0) (sumX / count).toFloat() else null
}

/** Rightmost x coordinate that contains waveform bar pixels. */
internal fun waveformRightEdgeX(image: ImageBitmap): Int {
    val bitmap = image.asAndroidBitmap()
    val yStep = (bitmap.height / 8).coerceAtLeast(1)
    for (x in bitmap.width - 1 downTo 0) {
        var y = yStep
        while (y < bitmap.height - yStep) {
            if (isWaveformBarPixel(bitmap.getPixel(x, y))) return x
            y += yStep
        }
    }
    return -1
}

internal fun assertSlotCentroidStable(
    before: ImageBitmap,
    after: ImageBitmap,
    slot: Int,
    capacitySlots: Int,
    tolerancePx: Float = 2.5f,
) {
    val width = before.asAndroidBitmap().width
    val expected = expectedSlotCenterX(slot, capacitySlots, width)
    val searchRadius = (width.toFloat() / capacitySlots * 1.5f).toInt().coerceAtLeast(4)
    val beforeX = measureBarCentroidX(before, expected, searchRadius)
    val afterX = measureBarCentroidX(after, expected, searchRadius)
    checkNotNull(beforeX) { "slot $slot bar not visible in baseline frame (expected ~$expected px)" }
    checkNotNull(afterX) { "slot $slot bar not visible after growth (expected ~$expected px)" }
    check(abs(afterX - beforeX) <= tolerancePx) {
        "slot $slot centroid moved from $beforeX to $afterX px (allowed ≤ $tolerancePx)"
    }
}

private fun isWaveformBarPixel(argb: Int): Boolean {
    val alpha = argb ushr 24
    if (alpha < 128) return false
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    val maxChannel = maxOf(red, green, blue)
    val minChannel = minOf(red, green, blue)
    return maxChannel > 80 && (maxChannel - minChannel) > 30
}
