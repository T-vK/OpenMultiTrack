package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveWaveformDisplayTest {
    private val pixels = 90

    @Test
    fun growthPhase_fillsFromLeft_oneSecondIsOneFifteenth() {
        val peaks = FloatArray(600) { 0.8f }
        val slots = liveWaveformSlotPeaks(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 1f,
            peaksPerSec = 30,
        )
        assertThat(slots.count { it > 0f }).isEqualTo(30)
        assertThat(liveWaveformFilledPixelCount(slots, pixels)).isEqualTo(6)
    }

    @Test
    fun growthPhase_twoSecondsIsTwoFifteenths() {
        val peaks = FloatArray(300) { 0.5f }
        val slots = liveWaveformSlotPeaks(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 2f,
            peaksPerSec = 30,
        )
        assertThat(slots.count { it > 0f }).isEqualTo(60)
        assertThat(liveWaveformFilledPixelCount(slots, pixels)).isEqualTo(12)
    }

    @Test
    fun growthPhase_fullWindowUsesEntireContainer() {
        val peaks = FloatArray(450) { 0.7f }
        val slots = liveWaveformSlotPeaks(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 15f,
            peaksPerSec = 30,
        )
        assertThat(slots.count { it > 0f }).isEqualTo(450)
        assertThat(liveWaveformFilledPixelCount(slots, pixels)).isEqualTo(pixels)
    }

    @Test
    fun growthPhase_clampsBloatedPeakBufferToElapsedTime() {
        val peaks = FloatArray(300) { 0.5f }
        val slots = liveWaveformSlotPeaks(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 2f,
            peaksPerSec = 30,
        )
        assertThat(slots.count { it > 0f }).isEqualTo(60)
    }

    @Test
    fun growthPhase_existingSlotsStayAtFixedIndicesWhenNewPeakArrives() {
        val before = liveWaveformSlotPeaks(
            peaks = floatArrayOf(0.4f),
            windowSec = 15f,
            elapsedSec = 1f / 30f,
            peaksPerSec = 30,
        )
        val laterPeaks = FloatArray(31)
        laterPeaks[0] = 0.4f
        laterPeaks[30] = 0.9f
        val after = liveWaveformSlotPeaks(
            peaks = laterPeaks,
            windowSec = 15f,
            elapsedSec = 31f / 30f,
            peaksPerSec = 30,
        )
        assertThat(after[0]).isWithin(0.001f).of(before[0])
        assertThat(after[30]).isWithin(0.001f).of(0.9f)
    }

    @Test
    fun rollingPhase_usesFullWidth() {
        val peaks = FloatArray(600) { 0.6f }
        val slots = liveWaveformSlotPeaks(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 30f,
            peaksPerSec = 30,
        )
        assertThat(slots.count { it > 0f }).isEqualTo(450)
        assertThat(liveWaveformFilledPixelCount(slots, pixels)).isEqualTo(pixels)
    }
}
