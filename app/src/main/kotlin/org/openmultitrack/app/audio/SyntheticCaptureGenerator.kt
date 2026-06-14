package org.openmultitrack.app.audio

import org.openmultitrack.domain.mixer.DemoBandChannels
import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates continuous tones for the virtual demo mixer.
 * Demo-band mode uses amplitude envelopes so live and recorded waveforms are easy to verify.
 */
class SyntheticCaptureGenerator(
    private val channelTones: List<ChannelTone>,
    val sampleRate: Int,
) {
    val channelCount: Int get() = channelTones.size

    private var frameIndex: Long = 0

    data class ChannelTone(
        val frequencyHz: Double,
        val maxAmplitude: Float,
        val envelope: Envelope = Envelope.Constant,
    )

    sealed class Envelope {
        data object Constant : Envelope()

        data object Silent : Envelope()

        /** Smooth 0→max→0 cycles at [hz] (e.g. 2 Hz = twice per second). */
        data class Pump(
            val hz: Double = 2.0,
            val phaseRadians: Double = 0.0,
        ) : Envelope()
    }

    constructor(channelCount: Int, sampleRate: Int, amplitude: Float = 0.35f) : this(
        (0 until channelCount).map { ch ->
            ChannelTone(frequencyHz = 110.0 + ch * 37.0, maxAmplitude = amplitude)
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
                dest[f * channelCount + ch] = sampleForTone(channelTones[ch], t)
            }
        }
        frameIndex += maxFrames
        return maxFrames
    }

    /** Expected peak magnitude for [channel] (envelope max × carrier peak). */
    fun expectedPeak(channel: Int): Float {
        val tone = channelTones.getOrNull(channel) ?: return 0f
        val envelopeMax = when (tone.envelope) {
            Envelope.Constant -> 1f
            Envelope.Silent -> 0f
            is Envelope.Pump -> 1f
        }
        return (tone.maxAmplitude * envelopeMax).coerceIn(0f, 1f)
    }

    companion object {
        private const val DEMO_PUMP_HZ = 2.0

        fun fromDemoBand(sampleRate: Int = 48_000): SyntheticCaptureGenerator {
            val lastIndex = DemoBandChannels.specs.lastIndex
            val tones = DemoBandChannels.specs.mapIndexed { index, _ ->
                when (index) {
                    0 -> ChannelTone(
                        frequencyHz = 0.0,
                        maxAmplitude = 1f,
                        envelope = Envelope.Constant,
                    )
                    lastIndex -> ChannelTone(
                        frequencyHz = 0.0,
                        maxAmplitude = 0f,
                        envelope = Envelope.Silent,
                    )
                    else -> ChannelTone(
                        frequencyHz = 0.0,
                        maxAmplitude = 1f,
                        envelope = Envelope.Pump(
                            hz = DEMO_PUMP_HZ,
                            phaseRadians = index * PI / 4.0,
                        ),
                    )
                }
            }
            return SyntheticCaptureGenerator(channelTones = tones, sampleRate = sampleRate)
        }

        fun frequencyHz(channel: Int): Double = 110.0 + channel * 37.0

        private fun sampleForTone(tone: ChannelTone, timeSec: Double): Float = when (tone.envelope) {
            Envelope.Silent -> 0f
            is Envelope.Pump -> {
                val gain = envelopeGainAt(tone.envelope, timeSec)
                (tone.maxAmplitude * gain).toFloat()
            }
            Envelope.Constant -> {
                if (tone.frequencyHz <= 0.0) {
                    tone.maxAmplitude
                } else {
                    (tone.maxAmplitude * sin(2.0 * PI * tone.frequencyHz * timeSec)).toFloat()
                }
            }
        }

        private fun envelopeGainAt(envelope: Envelope.Pump, timeSec: Double): Double {
            val lfo = sin(2.0 * PI * envelope.hz * timeSec + envelope.phaseRadians)
            return (1.0 + lfo) / 2.0
        }
    }
}
