package org.openmultitrack.app.e2e

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.openmultitrack.app.ui.daw.measureBarCentroidX
import org.openmultitrack.app.ui.daw.waveformRightEdgeX
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Captures the device screen via UiAutomation (works with a running MainActivity). */
fun captureDeviceScreen(): Bitmap =
    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        ?: error("UiAutomation.takeScreenshot() returned null")

/**
 * Crops the first visible channel strip waveform area from a full-screen capture.
 * Layout: [label][VU][waveform…] left → right.
 */
fun cropFirstWaveformStrip(screen: Bitmap): Bitmap {
    val yRange = findFirstWaveformRowYRange(screen)
    val x0 = (screen.width * 0.14f).toInt().coerceIn(0, screen.width - 2)
    val x1 = (screen.width * 0.93f).toInt().coerceIn(x0 + 1, screen.width)
    val height = (yRange.last - yRange.first + 1).coerceAtLeast(1)
    return Bitmap.createBitmap(screen, x0, yRange.first, x1 - x0, height)
}

private fun findFirstWaveformRowYRange(screen: Bitmap): IntRange {
    val waveStartX = (screen.width * 0.16f).toInt()
    val waveEndX = (screen.width * 0.92f).toInt()
    var firstY = -1
    var lastY = -1
    for (y in (screen.height * 0.18f).toInt() until (screen.height * 0.85f).toInt() step 3) {
        if (barPixelsInRow(screen, y, waveStartX, waveEndX) >= 4) {
            if (firstY < 0) firstY = y
            lastY = y
        } else if (firstY >= 0) {
            break
        }
    }
    check(firstY >= 0) { "no live waveform row visible on screen capture" }
    val pad = 6
    return (firstY - pad).coerceAtLeast(0)..(lastY + pad).coerceAtMost(screen.height - 1)
}

private fun barPixelsInRow(bitmap: Bitmap, y: Int, x0: Int, x1: Int): Int {
    var count = 0
    for (x in x0..x1) {
        if (isWaveformBarPixel(bitmap.getPixel(x, y))) count++
    }
    return count
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

fun Bitmap.asComposeImage(): ImageBitmap = asImageBitmap()

fun waveformStripRightEdge(strip: Bitmap): Int = waveformRightEdgeX(strip.asComposeImage())

fun waveformStripSlotCentroid(strip: Bitmap, slot: Int, capacitySlots: Int): Float? {
    val expected = (slot + 0.5f) / capacitySlots * strip.width
    val tolerance = (strip.width.toFloat() / capacitySlots * 1.5f).toInt().coerceAtLeast(4)
    return measureBarCentroidX(strip.asComposeImage(), expected, tolerance)
}
