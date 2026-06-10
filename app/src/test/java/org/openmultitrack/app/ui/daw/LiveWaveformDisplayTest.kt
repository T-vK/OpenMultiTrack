package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveWaveformDisplayTest {
    private val pixels = 90

    @Test
    fun growthPhase_fillsFromLeft_oneSecondIsOneFifteenth() {
        val peaks = FloatArray(600) { 0.8f }
        val columns = liveWaveformColumnsForPixels(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 1f,
            peaksPerSec = 30,
            pixelCount = pixels,
        )
        assertThat(columns.count { it > 0f }).isEqualTo(6)
        assertThat(columns.take(6).all { it > 0f }).isTrue()
        assertThat(columns.drop(6).all { it == 0f }).isTrue()
    }

    @Test
    fun growthPhase_twoSecondsIsTwoFifteenths() {
        val peaks = FloatArray(300) { 0.5f }
        val columns = liveWaveformColumnsForPixels(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 2f,
            peaksPerSec = 30,
            pixelCount = pixels,
        )
        assertThat(columns.count { it > 0f }).isEqualTo(12)
        assertThat(columns.take(12).all { it > 0f }).isTrue()
        assertThat(columns.drop(12).all { it == 0f }).isTrue()
    }

    @Test
    fun growthPhase_fullWindowUsesEntireContainer() {
        val peaks = FloatArray(450) { 0.7f }
        val columns = liveWaveformColumnsForPixels(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 15f,
            peaksPerSec = 30,
            pixelCount = pixels,
        )
        assertThat(columns.count { it > 0f }).isEqualTo(pixels)
    }

    @Test
    fun growthPhase_clampsBloatedPeakBufferToElapsedTime() {
        val peaks = FloatArray(300) { 0.5f }
        val columns = liveWaveformColumnsForPixels(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 2f,
            peaksPerSec = 30,
            pixelCount = pixels,
        )
        assertThat(columns.count { it > 0f }).isEqualTo(12)
    }

    @Test
    fun growthPhase_existingColumnsStayStableWhenNewPeakArrives() {
        val before = liveWaveformColumnsForPixels(
            peaks = floatArrayOf(0.4f),
            windowSec = 15f,
            elapsedSec = 1f / 30f,
            peaksPerSec = 30,
            pixelCount = pixels,
        )
        val laterPeaks = FloatArray(31)
        laterPeaks[0] = 0.4f
        laterPeaks[30] = 0.9f
        val after = liveWaveformColumnsForPixels(
            peaks = laterPeaks,
            windowSec = 15f,
            elapsedSec = 31f / 30f,
            peaksPerSec = 30,
            pixelCount = pixels,
        )
        assertThat(after[0]).isWithin(0.001f).of(before[0])
    }

    @Test
    fun scaleLivePeaksForDisplay_usesFrozenHoldSoOlderBarsDoNotJump() {
        val hold = 0.03f
        val first = scaleLivePeaksForDisplay(floatArrayOf(0.02f, 0.03f), normalized = false, peakHold = hold)
        val second = scaleLivePeaksForDisplay(floatArrayOf(0.02f, 0.03f, 0.08f), normalized = false, peakHold = hold)
        assertThat(first[0]).isWithin(0.001f).of(second[0])
        assertThat(first[1]).isWithin(0.001f).of(second[1])
    }

    @Test
    fun rollingPhase_usesFullWidth() {
        val peaks = FloatArray(600) { 0.6f }
        val columns = liveWaveformColumnsForPixels(
            peaks = peaks,
            windowSec = 15f,
            elapsedSec = 30f,
            peaksPerSec = 30,
            pixelCount = pixels,
        )
        assertThat(columns.count { it > 0f }).isEqualTo(pixels)
    }
}
