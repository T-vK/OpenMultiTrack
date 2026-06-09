package org.openmultitrack.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CaptureVuMeterMathTest {
    @Test
    fun scaleCaptureVuPeak_keepsSilentChannelsNearZero() {
        assertThat(scaleCaptureVuPeak(0f)).isEqualTo(0f)
        assertThat(scaleCaptureVuPeak(1e-6f)).isEqualTo(0f)
        assertThat(scaleCaptureVuPeak(0.0001f)).isLessThan(0.1f)
    }

    @Test
    fun scaleCaptureVuPeak_mapsMediumSignalBelowFullScale() {
        assertThat(scaleCaptureVuPeak(0.3f)).isWithin(0.05f).of(0.81f)
        assertThat(scaleCaptureVuPeak(0.5f)).isWithin(0.05f).of(0.89f)
    }

    @Test
    fun absorbInterleavedPeaks_tracksPerChannelMaxima() {
        val hold = FloatArray(4)
        val raw = FloatArray(4)
        val scratch = floatArrayOf(
            0.3f, 0.0001f, 0.0f, 0.0f,
            0.25f, 0.0f, 0.0f, 0.0f,
        )
        absorbInterleavedPeaks(hold, raw, scratch, frames = 2, channels = 4)
        assertThat(hold[0]).isGreaterThan(0.5f)
        assertThat(hold[1]).isLessThan(0.1f)
        assertThat(raw[0]).isWithin(0.01f).of(0.3f)
        assertThat(raw[1]).isWithin(1e-5f).of(0.0001f)
    }

    @Test
    fun activeChannelReadsHigherThanSilentNeighbor() {
        val hold = FloatArray(2)
        val raw = FloatArray(2)
        val scratch = floatArrayOf(
            0.4f, 0.00005f,
            0.35f, 0.00004f,
            0.38f, 0.00003f,
        )
        absorbInterleavedPeaks(hold, raw, scratch, frames = 3, channels = 2)
        assertThat(hold[0]).isGreaterThan(hold[1] + 0.2f)
    }
}
