package org.openmultitrack.app.audio

/** Maps a raw PCM peak (0..1) to a display level for strip VU meters. */
internal fun scaleCaptureVuPeak(raw: Float): Float {
    if (raw <= 1e-6f) return 0f
    val gain = if (raw < 0.15f) 1f / raw else 1f
    return (raw * gain).coerceIn(0f, 1f)
}

/** Absorbs a raw interleaved PCM chunk into per-channel peak hold values. */
internal fun absorbInterleavedPeaks(
    hold: FloatArray,
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
        if (peak > 0f) {
            hold[ch] = maxOf(scaleCaptureVuPeak(peak), hold[ch])
        }
    }
}
