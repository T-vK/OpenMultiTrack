package org.openmultitrack.app.audio

import org.openmultitrack.domain.mixer.DemoBandChannels
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates continuous tones for the virtual demo mixer.
 * Each channel uses its own frequency and level so VU meters and waveforms are distinguishable.
 */
class SyntheticCaptureGenerator(
  private val channelTones: List<ChannelTone>,
  val sampleRate: Int,
) {
    val channelCount: Int get() = channelTones.size

    private var frameIndex: Long = 0

    data class ChannelTone(
        val frequencyHz: Double,
        val amplitude: Float,
    )

    constructor(channelCount: Int, sampleRate: Int, amplitude: Float = 0.35f) : this(
        (0 until channelCount).map { ch ->
            ChannelTone(frequencyHz = 110.0 + ch * 37.0, amplitude = amplitude)
        },
        sampleRate,
    )

    /** Fills [dest] with interleaved float PCM; returns frames written (≤ [maxFrames]). */
    fun fill(dest: FloatArray, maxFrames: Int): Int {
        if (channelCount <= 0 || maxFrames <= 0) return 0
        val needed = maxFrames * channelCount
        require(dest.size >= needed) { "buffer too small: ${dest.size} < $needed" }
        for (f in 0 until maxFrames) {
            val t = (frameIndex + f).toDouble() / sampleRate.toDouble()
            for (ch in channelTones.indices) {
                val tone = channelTones[ch]
                dest[f * channelCount + ch] =
                    (tone.amplitude * sin(2.0 * PI * tone.frequencyHz * t)).toFloat()
            }
        }
        frameIndex += maxFrames
        return maxFrames
    }

    /** Expected peak magnitude for [channel]. */
    fun expectedPeak(channel: Int): Float =
        channelTones.getOrNull(channel)?.amplitude?.coerceIn(0f, 1f) ?: 0f

    companion object {
        fun fromDemoBand(sampleRate: Int = 48_000): SyntheticCaptureGenerator =
            SyntheticCaptureGenerator(
                channelTones = DemoBandChannels.specs.map {
                    ChannelTone(frequencyHz = it.frequencyHz, amplitude = it.amplitude)
                },
                sampleRate = sampleRate,
            )

        fun frequencyHz(channel: Int): Double = 110.0 + channel * 37.0
    }
}
