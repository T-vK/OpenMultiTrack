package org.openmultitrack.app.audio

import kotlin.math.log10

/**
 * Maps a linear PCM peak (0..1) to a VU display level.
 *
 * Uses a log curve so quiet channels stay near zero while active channels
 * spread across the meter range. Avoids boosting noise floors to full scale.
 */
internal fun scaleCaptureVuPeak(raw: Float): Float {
    if (raw <= 1e-5f) return 0f
    // -54 dBFS .. 0 dBFS → 0 .. 1
    val db = 20f * log10(raw.coerceAtLeast(1e-6f))
    return ((db + 54f) / 54f).coerceIn(0f, 1f)
}

/** Absorbs a raw interleaved PCM chunk into per-channel peak hold values. */
internal fun absorbInterleavedPeaks(
    hold: FloatArray,
    rawPeaks: FloatArray,
    scratch: FloatArray,
    frames: Int,
    channels: Int,
) {
    for (ch in 0 until channels) {
        var peak = 0f
        for (frame in 0 until frames) {
            val sample = scratch[frame * channels + ch]
            peak = maxOf(peak, kotlin.math.abs(sample))
        }
        rawPeaks[ch] = maxOf(rawPeaks[ch], peak)
        if (peak > 0f) {
            hold[ch] = maxOf(scaleCaptureVuPeak(peak), hold[ch])
        }
    }
}
