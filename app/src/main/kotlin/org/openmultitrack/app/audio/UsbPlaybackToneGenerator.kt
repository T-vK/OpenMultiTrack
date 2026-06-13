package org.openmultitrack.app.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates a continuous sine on one USB playback return; other channels stay silent.
 * Distinct frequencies per channel (220 Hz × channel index) make returns easy to identify.
 */
class UsbPlaybackToneGenerator(
    private val channelCount: Int,
    private val activeChannel: Int,
    val sampleRate: Int,
    private val amplitude: Float = 0.5f,
) {
    private var frameIndex: Long = 0L

    val frequencyHz: Double = 220.0 * (activeChannel + 1)

    /** Fills interleaved [dest] with up to [maxFrames]; returns frames written. */
    fun fill(dest: FloatArray, maxFrames: Int): Int {
        if (channelCount <= 0 || maxFrames <= 0 || activeChannel !in 0 until channelCount) return 0
        val needed = maxFrames * channelCount
        require(dest.size >= needed) { "buffer too small: ${dest.size} < $needed" }
        for (f in 0 until maxFrames) {
            val t = (frameIndex + f).toDouble() / sampleRate.toDouble()
            for (ch in 0 until channelCount) {
                dest[f * channelCount + ch] = if (ch == activeChannel) {
                    (amplitude * sin(2.0 * PI * frequencyHz * t)).toFloat()
                } else {
                    0f
                }
            }
        }
        frameIndex += maxFrames
        return maxFrames
    }

    companion object {
        fun frequencyHz(usbChannelIndex: Int): Double = 220.0 * (usbChannelIndex + 1)
    }
}
