package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.sessionio.wav.SessionWaveformOverview

class SoundcheckWaveformTest {
    @Test
    fun visiblePeaksForViewport_downsamplesToPixelWidth() {
        val peaks = FloatArray(400) { (it % 10) / 10f }
        val overview = SessionWaveformOverview(
            peaksByChannel = mapOf(0 to peaks),
            peaksPerSec = 4f,
            durationSec = 100f,
        )
        val visible = visiblePeaksForViewport(
            overview = overview,
            channelIndex = 0,
            viewStartSec = 0f,
            viewWindowSec = 50f,
            pixelWidth = 64,
        )
        assertThat(visible.size).isEqualTo(64)
        assertThat(visible.max()).isAtLeast(0.8f)
    }

    @Test
    fun peakLevelAtTime_readsOverviewAtIndex() {
        val peaks = FloatArray(40) { 0.1f }
        peaks[20] = 0.8f
        val overview = SessionWaveformOverview(
            peaksByChannel = mapOf(0 to peaks),
            peaksPerSec = 4f,
            durationSec = 10f,
        )
        val level = peakLevelAtTime(overview, 0, 5f, normalized = false)
        assertThat(level).isWithin(0.01f).of(0.8f)
    }

    @Test
    fun scalePeaksForDisplay_boostsQuietPeaks() {
        val quiet = floatArrayOf(0.01f, 0.02f, 0.015f)
        val scaled = scalePeaksForDisplay(quiet, normalized = false)
        assertThat(scaled.max()).isWithin(0.01f).of(1f)
    }
}
