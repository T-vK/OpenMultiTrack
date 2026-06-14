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
    fun demoBand_firstChannelFull_lastSilent_middleChannelsPump() {
        val gen = SyntheticCaptureGenerator.fromDemoBand()
        assertThat(gen.channelCount).isEqualTo(DemoBandChannels.specs.size)
        val sampleRate = gen.sampleRate
        val frames = sampleRate
        val scratch = FloatArray(frames * gen.channelCount)
        gen.fill(scratch, frames)

        val leadPeak = peakForChannel(scratch, gen.channelCount, 0, frames)
        val playbackPeak = peakForChannel(scratch, gen.channelCount, gen.channelCount - 1, frames)
        assertThat(leadPeak).isWithin(0.03f).of(1f)
        assertThat(playbackPeak).isLessThan(0.001f)

        // Live waveform buckets use ~30 peaks/sec — verify the 2 Hz pump is visible there.
        val pumpPeaks = peakSeriesForChannel(scratch, gen.channelCount, 1, frames, peaksPerSec = 30)
        assertThat(pumpPeaks.maxOrNull()!!).isGreaterThan(0.75f)
        assertThat(pumpPeaks.minOrNull()!!).isLessThan(0.15f)
        assertThat(pumpPeaks.maxOrNull()!! - pumpPeaks.minOrNull()!!).isGreaterThan(0.5f)
    }

    private fun peakForChannel(scratch: FloatArray, channelCount: Int, channel: Int, frames: Int): Float {
        var peak = 0f
        for (f in 0 until frames) {
            peak = maxOf(peak, abs(scratch[f * channelCount + channel]))
        }
        return peak
    }

    private fun peakSeriesForChannel(
        scratch: FloatArray,
        channelCount: Int,
        channel: Int,
        frames: Int,
        peaksPerSec: Int,
    ): List<Float> {
        val bucket = frames / peaksPerSec
        require(bucket > 0)
        return (0 until peaksPerSec).map { bucketIndex ->
            val start = bucketIndex * bucket
            val end = minOf(frames, start + bucket)
            var peak = 0f
            for (f in start until end) {
                peak = maxOf(peak, abs(scratch[f * channelCount + channel]))
            }
            peak
        }
    }
}
