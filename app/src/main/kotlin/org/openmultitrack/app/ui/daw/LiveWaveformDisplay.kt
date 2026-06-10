package org.openmultitrack.app.ui.daw

/**
 * Maps live recording peaks into per-pixel columns for strip rendering.
 *
 * Growth (elapsed ≤ window): waveform grows **left to right** — 1 s of a 15 s window
 * fills the left 1/15 of the container, 2 s fills 2/15, etc.
 *
 * Rolling (elapsed > window): the last [window] of audio spans the full width,
 * oldest sample on the left, newest on the right.
 *
 * [peaks] must be oldest → newest. [elapsedSec] is the authority for how much
 * horizontal space is used during growth, so a bloated peak buffer cannot zoom the view.
 */
internal fun liveWaveformColumnsForPixels(
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
    val activeSlots = if (rolling) capacitySlots else elapsedSlots.coerceAtMost(capacitySlots)

    val samples = if (rolling) {
        val count = minOf(peaks.size, capacitySlots)
        peaks.copyOfRange(peaks.size - count, peaks.size)
    } else {
        val count = minOf(peaks.size, activeSlots)
        peaks.copyOfRange(0, count)
    }

    for (px in 0 until pixelCount) {
        val slotLo = px * capacitySlots / pixelCount
        val slotHi = (px + 1) * capacitySlots / pixelCount
        var maxPeak = 0f
        for (slot in slotLo until slotHi) {
            if (!rolling && slot >= activeSlots) continue
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
