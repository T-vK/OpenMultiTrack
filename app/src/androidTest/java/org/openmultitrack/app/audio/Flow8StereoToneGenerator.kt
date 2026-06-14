package org.openmultitrack.app.audio

import kotlin.math.PI
import kotlin.math.sin

/** Stereo sine on USB playback returns U01 (L) and U02 (R); other channels silent. */
class Flow8StereoToneGenerator(
    private val channelCount: Int,
    val sampleRate: Int,
    private val leftHz: Double = 440.0,
    private val rightHz: Double = 554.0,
    private val amplitude: Float = 0.45f,
) {
    private var frameIndex: Long = 0L

    fun fill(dest: FloatArray, maxFrames: Int): Int {
        if (channelCount < 2 || maxFrames <= 0) return 0
        val needed = maxFrames * channelCount
        require(dest.size >= needed) { "buffer too small: ${dest.size} < $needed" }
        for (f in 0 until maxFrames) {
            val t = (frameIndex + f).toDouble() / sampleRate.toDouble()
            for (ch in 0 until channelCount) {
                dest[f * channelCount + ch] = when (ch) {
                    0 -> (amplitude * sin(2.0 * PI * leftHz * t)).toFloat()
                    1 -> (amplitude * sin(2.0 * PI * rightHz * t)).toFloat()
                    else -> 0f
                }
            }
        }
        frameIndex += maxFrames
        return maxFrames
    }
}
