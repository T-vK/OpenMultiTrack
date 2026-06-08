package org.openmultitrack.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureVuMeterMathTest {
    @Test
    fun scaleCaptureVuPeak_boostsQuietSignals() {
        assertThat(scaleCaptureVuPeak(0.01f)).isWithin(0.01f).of(1f)
        assertThat(scaleCaptureVuPeak(0.5f)).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun absorbInterleavedPeaks_tracksPerChannelMaxima() {
        val hold = FloatArray(4)
        val scratch = floatArrayOf(
            0.1f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.2f, 0.0f, 0.0f,
        )
        absorbInterleavedPeaks(hold, scratch, frames = 2, channels = 4)
        assertThat(hold[0]).isWithin(0.01f).of(1f)
        assertThat(hold[1]).isWithin(0.01f).of(0.2f)
        assertThat(hold[2]).isEqualTo(0f)
    }
}
