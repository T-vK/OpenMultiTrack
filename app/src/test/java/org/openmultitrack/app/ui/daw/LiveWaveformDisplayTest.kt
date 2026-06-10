package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveWaveformDisplayTest {
    @Test
    fun binLiveWaveform_fillsGraduallyByElapsedTime() {
        val peaks = FloatArray(600) { 0.8f }
        val pixels = 90
        val atOneSecond = binLiveWaveformToPixels(peaks, windowSec = 15f, elapsedSec = 1f, peaksPerSec = 30, pixelCount = pixels)
        val atFifteenSeconds = binLiveWaveformToPixels(peaks, windowSec = 15f, elapsedSec = 15f, peaksPerSec = 30, pixelCount = pixels)
        assertThat(atOneSecond.count { it > 0f }).isEqualTo(6)
        assertThat(atFifteenSeconds.count { it > 0f }).isEqualTo(90)
        assertThat(atOneSecond.take(83).all { it == 0f }).isTrue()
    }

    @Test
    fun binLiveWaveform_ignoresExcessPeaksBeyondElapsedTime() {
        val peaks = FloatArray(300) { 0.5f }
        val columns = binLiveWaveformToPixels(peaks, windowSec = 15f, elapsedSec = 2f, peaksPerSec = 30, pixelCount = 90)
        assertThat(columns.count { it > 0f }).isEqualTo(12)
    }
}
