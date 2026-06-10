package org.openmultitrack.app.ui.daw

/**
 * Maps live recording peaks into one value per timeline slot for strip rendering.
 *
 * Growth (elapsed ≤ window): slots 0, 1, 2… are anchored at fixed positions across the
 * window (slot 0 at the left edge). New peaks only append higher slot indices on the right.
 *
 * Rolling (elapsed > window): the last [window] of audio occupies all slots, oldest left.
 *
 * [peaks] must be oldest → newest. During growth, [elapsedSec] only caps a bloated buffer.
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
    val elapsedCap = (elapsedSec * peaksPerSec).toInt().coerceIn(0, capacitySlots)
    if (elapsedCap <= 0) return FloatArray(capacitySlots)

    val rolling = elapsedSec > windowSec
    val out = FloatArray(capacitySlots)
    if (rolling) {
        val count = minOf(peaks.size, capacitySlots)
        val start = peaks.size - count
        for (i in 0 until count) {
            out[i] = peaks[start + i]
        }
    } else {
        val count = minOf(peaks.size, elapsedCap)
        for (i in 0 until count) {
            out[i] = peaks[i]
        }
    }
    return out
}

/** Counts how many pixel columns would be touched by [slotPeaks] (for tests). */
internal fun liveWaveformFilledPixelCount(
    slotPeaks: FloatArray,
    pixelCount: Int,
): Int {
    if (slotPeaks.isEmpty() || pixelCount <= 0) return 0
    val capacity = slotPeaks.size
    val filled = BooleanArray(pixelCount)
    slotPeaks.forEachIndexed { slot, peak ->
        if (peak <= 0f) return@forEachIndexed
        val px = (slot.toLong() * pixelCount / capacity).toInt().coerceIn(0, pixelCount - 1)
        filled[px] = true
    }
    return filled.count { it }
}
