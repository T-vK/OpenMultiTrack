package org.openmultitrack.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.mixer.DemoBandChannels
import kotlin.math.abs

class SyntheticCaptureGeneratorTest {
    @Test
    fun fill_producesExpectedSineAmplitudePerChannel() {
        val gen = SyntheticCaptureGenerator(channelCount = 4, sampleRate = 48_000, amplitude = 0.35f)
        val scratch = FloatArray(480 * 4)
        val frames = gen.fill(scratch, 480)
        assertThat(frames).isEqualTo(480)
        for (ch in 0 until 4) {
            var peak = 0f
            for (f in 0 until frames) {
                peak = maxOf(peak, abs(scratch[f * 4 + ch]))
            }
            assertThat(peak).isWithin(0.02f).of(0.35f)
        }
    }

    @Test
    fun channelsUseDistinctFrequencies() {
        val gen = SyntheticCaptureGenerator(channelCount = 2, sampleRate = 48_000)
        val scratch = FloatArray(96 * 2)
        gen.fill(scratch, 96)
        val ch0 = FloatArray(96) { f -> scratch[f * 2] }
        val ch1 = FloatArray(96) { f -> scratch[f * 2 + 1] }
        assertThat(ch0.contentEquals(ch1)).isFalse()
    }

    @Test
    fun demoBand_usesPerChannelLevels() {
        val gen = SyntheticCaptureGenerator.fromDemoBand()
        assertThat(gen.channelCount).isEqualTo(DemoBandChannels.specs.size)
        val scratch = FloatArray(480 * gen.channelCount)
        gen.fill(scratch, 480)
        val kickPeak = peakForChannel(scratch, gen.channelCount, 7, 480)
        val hihatPeak = peakForChannel(scratch, gen.channelCount, 9, 480)
        assertThat(kickPeak).isWithin(0.03f).of(0.48f)
        assertThat(hihatPeak).isWithin(0.03f).of(0.14f)
        assertThat(kickPeak).isGreaterThan(hihatPeak)
    }

    private fun peakForChannel(scratch: FloatArray, channelCount: Int, channel: Int, frames: Int): Float {
        var peak = 0f
        for (f in 0 until frames) {
            peak = maxOf(peak, abs(scratch[f * channelCount + channel]))
        }
        return peak
    }
}
