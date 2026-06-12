package org.openmultitrack.app.audio

import kotlin.math.log10

/** dBFS floor for VU display — below this reads as zero. */
private const val VU_DISPLAY_FLOOR_DB = -66f

/** dBFS where the meter reaches full scale (headroom above typical line level). */
private const val VU_DISPLAY_CEIL_DB = -6f

private const val METER_ATTACK = 0.55f
private const val METER_RELEASE = 0.38f

/**
 * Maps a linear PCM peak (0..1) to a VU display level.
 *
 * Uses a log curve between [VU_DISPLAY_FLOOR_DB] and [VU_DISPLAY_CEIL_DB] so
 * medium line-level signals land mid-scale instead of pegging the meter.
 */
internal fun scaleCaptureVuPeak(raw: Float): Float {
    if (raw <= 1e-6f) return 0f
    val db = 20f * log10(raw.coerceAtLeast(1e-8f))
    if (db <= VU_DISPLAY_FLOOR_DB) return 0f
    return ((db - VU_DISPLAY_FLOOR_DB) / (VU_DISPLAY_CEIL_DB - VU_DISPLAY_FLOOR_DB))
        .coerceIn(0f, 1f)
}

/** Absorbs per-channel peaks (already computed) into VU hold values. */
internal fun absorbChannelPeaks(
    hold: FloatArray,
    rawPeaks: FloatArray,
    peaks: FloatArray,
    channels: Int,
) {
    for (ch in 0 until channels) {
        val peak = peaks[ch]
        rawPeaks[ch] = peak
        val target = scaleCaptureVuPeak(peak)
        val coeff = if (target > hold[ch]) METER_ATTACK else METER_RELEASE
        hold[ch] += (target - hold[ch]) * coeff
    }
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
        rawPeaks[ch] = peak
        val target = scaleCaptureVuPeak(peak)
        val coeff = if (target > hold[ch]) METER_ATTACK else METER_RELEASE
        hold[ch] += (target - hold[ch]) * coeff
    }
}
