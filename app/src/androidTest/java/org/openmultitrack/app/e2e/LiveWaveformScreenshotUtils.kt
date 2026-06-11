package org.openmultitrack.app.e2e

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import org.openmultitrack.app.ui.daw.estimateWaveformStripBackground
import org.openmultitrack.app.ui.daw.isWaveformBarPixel
import org.openmultitrack.app.ui.daw.measureBarCentroidX
import org.openmultitrack.app.ui.daw.waveformRightEdgeX

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
    val x1 = (screen.width * 0.93f).toInt().coerceIn(x0 + 1, screen.width - 1)
    val height = (yRange.last - yRange.first + 1).coerceAtLeast(1)
    return Bitmap.createBitmap(screen, x0, yRange.first, x1 - x0, height)
}

private fun findFirstWaveformRowYRange(screen: Bitmap): IntRange {
    val waveStartX = (screen.width * 0.16f).toInt()
    val waveEndX = (screen.width * 0.92f).toInt()
    val bg = screen.getPixel(waveStartX, (screen.height * 0.2f).toInt().coerceIn(0, screen.height - 1))
    var firstY = -1
    var lastY = -1
    for (y in (screen.height * 0.15f).toInt() until (screen.height * 0.88f).toInt() step 2) {
        if (barPixelsInRow(screen, y, waveStartX, waveEndX, bg) >= 1) {
            if (firstY < 0) firstY = y
            lastY = y
        } else if (firstY >= 0) {
            break
        }
    }
    check(firstY >= 0) { "no live waveform row visible on screen capture" }
    val pad = 8
    return (firstY - pad).coerceAtLeast(0)..(lastY + pad).coerceAtMost(screen.height - 1)
}

private fun barPixelsInRow(bitmap: Bitmap, y: Int, x0: Int, x1: Int, backgroundArgb: Int): Int {
    var count = 0
    for (x in x0..x1) {
        if (isWaveformBarPixel(bitmap.getPixel(x, y), backgroundArgb)) count++
    }
    return count
}

fun waveformStripRightEdge(strip: Bitmap): Int {
    val bg = estimateWaveformStripBackground(strip)
    return waveformRightEdgeX(strip, bg)
}

fun waveformStripSlotCentroid(strip: Bitmap, slot: Int, capacitySlots: Int): Float? {
    val bg = estimateWaveformStripBackground(strip)
    val expected = (slot + 0.5f) / capacitySlots * strip.width
    val tolerance = (strip.width.toFloat() / capacitySlots * 1.5f).toInt().coerceAtLeast(4)
    return measureBarCentroidX(strip, expected, tolerance, bg)
}

/** Waits until the cropped waveform strip shows at least one bar pixel. */
suspend fun awaitWaveformStripWithBars(timeoutMs: Long = 45_000): Bitmap {
    val deadline = System.currentTimeMillis() + timeoutMs
    var lastStrip: Bitmap? = null
    while (System.currentTimeMillis() < deadline) {
        val strip = cropFirstWaveformStrip(captureDeviceScreen())
        lastStrip = strip
        if (waveformStripRightEdge(strip) >= 0) return strip
        delay(250)
    }
    val edge = lastStrip?.let { waveformStripRightEdge(it) } ?: -1
    error("waveform bars not visible on screen (last rightEdge=$edge)")
}
