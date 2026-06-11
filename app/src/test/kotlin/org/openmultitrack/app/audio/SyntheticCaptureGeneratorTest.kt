package org.openmultitrack.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test
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
}
