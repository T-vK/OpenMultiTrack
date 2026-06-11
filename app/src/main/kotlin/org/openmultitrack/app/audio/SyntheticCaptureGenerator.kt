package org.openmultitrack.app.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates continuous sine tones for the virtual test mixer.
 * Each channel uses a distinct frequency so waveforms are predictable and distinguishable.
 */
class SyntheticCaptureGenerator(
    val channelCount: Int,
    val sampleRate: Int,
    private val amplitude: Float = 0.35f,
) {
    private var frameIndex: Long = 0

    /** Fills [dest] with interleaved float PCM; returns frames written (≤ [maxFrames]). */
    fun fill(dest: FloatArray, maxFrames: Int): Int {
        if (channelCount <= 0 || maxFrames <= 0) return 0
        val needed = maxFrames * channelCount
        require(dest.size >= needed) { "buffer too small: ${dest.size} < $needed" }
        for (f in 0 until maxFrames) {
            val t = (frameIndex + f).toDouble() / sampleRate.toDouble()
            for (ch in 0 until channelCount) {
                val freqHz = 110.0 + ch * 37.0
                dest[f * channelCount + ch] = (amplitude * sin(2.0 * PI * freqHz * t)).toFloat()
            }
        }
        frameIndex += maxFrames
        return maxFrames
    }

    /** Expected peak magnitude for [channel] (same as [amplitude] for a pure sine). */
    fun expectedPeak(channel: Int): Float = amplitude.coerceIn(0f, 1f)

    companion object {
        fun frequencyHz(channel: Int): Double = 110.0 + channel * 37.0
    }
}
