package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.sessionio.wav.SessionWaveformOverview

class SoundcheckWaveformTest {
    @Test
    fun upsamplePeaksLinear_fillsTargetWidth() {
        val source = floatArrayOf(0f, 1f)
        val upsampled = upsamplePeaksLinear(source, targetCount = 5)
        assertThat(upsampled.size).isEqualTo(5)
        assertThat(upsampled.first()).isWithin(0.001f).of(0f)
        assertThat(upsampled.last()).isWithin(0.001f).of(1f)
    }

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

    @Test
    fun scalePeaksForLiveDisplay_usesFrozenCeiling() {
        val loud = floatArrayOf(0.9f, 0.5f, 0.4f)
        val quiet = floatArrayOf(0.3f, 0.2f, 0.1f)
        val afterLoud = scalePeaksForLiveDisplay(loud, normalized = true, peakCeiling = 0f)
        val afterQuiet = scalePeaksForLiveDisplay(quiet, normalized = true, peakCeiling = 0.9f)
        assertThat(afterQuiet[0]).isWithin(0.01f).of(0.3f / 0.9f)
        assertThat(afterQuiet[1]).isGreaterThan(0.15f)
    }

    @Test
    fun scalePeaksForLiveDisplay_laterLoudPeakDoesNotShrinkEarlierPeaks() {
        val early = floatArrayOf(0.2f, 0.18f)
        val scaledEarly = scalePeaksForLiveDisplay(early, normalized = true, peakCeiling = 0.2f)
        val later = floatArrayOf(0.2f, 0.18f, 0.95f)
        val scaledLater = scalePeaksForLiveDisplay(later, normalized = true, peakCeiling = 0.2f)
        assertThat(scaledLater[0]).isWithin(0.001f).of(scaledEarly[0])
        assertThat(scaledLater[1]).isWithin(0.001f).of(scaledEarly[1])
        assertThat(scaledLater[2]).isWithin(0.001f).of(1f)
    }
}
