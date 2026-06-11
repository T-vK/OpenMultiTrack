package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveWaveformDisplayTest {
    private val pixels = 90
    private val windowSec = 15f
    private val peaksPerSec = 30

    private fun columns(
        peaks: FloatArray,
        elapsedSec: Float,
        pixelCount: Int = pixels,
    ): FloatArray = liveWaveformColumnsForDisplay(
        peaks = peaks,
        bufferWindowSec = windowSec,
        elapsedSec = elapsedSec,
        peaksPerSec = peaksPerSec,
        pixelCount = pixelCount,
        viewStartSec = if (elapsedSec > windowSec) elapsedSec - windowSec else 0f,
        viewWindowSec = windowSec,
    )

    private fun filledCount(cols: FloatArray, elapsedSec: Float): Int =
        liveWaveformFilledPixelCount(
            cols,
            elapsedSec = elapsedSec,
            viewStartSec = if (elapsedSec > windowSec) elapsedSec - windowSec else 0f,
            viewWindowSec = windowSec,
        )

    @Test
    fun growthPhase_fillsFromLeft_oneSecondIsOneFifteenth() {
        val cols = columns(peaks = FloatArray(30) { 0.8f }, elapsedSec = 1f)
        assertThat(filledCount(cols, elapsedSec = 1f)).isEqualTo(6)
    }

    @Test
    fun growthPhase_twoSecondsIsTwoFifteenths() {
        val cols = columns(peaks = FloatArray(60) { 0.5f }, elapsedSec = 2f)
        assertThat(filledCount(cols, elapsedSec = 2f)).isEqualTo(12)
    }

    @Test
    fun growthPhase_fullWindowUsesEntireContainer() {
        val cols = columns(peaks = FloatArray(450) { 0.7f }, elapsedSec = 15f)
        assertThat(filledCount(cols, elapsedSec = 15f))
            .isEqualTo(pixels)
    }

    @Test
    fun growthPhase_clampsBloatedPeakBufferToElapsedTime() {
        val cols = columns(peaks = FloatArray(300) { 0.5f }, elapsedSec = 2f)
        assertThat(filledCount(cols, elapsedSec = 2f)).isEqualTo(12)
    }

    @Test
    fun growthPhase_existingColumnsStayAtFixedIndicesWhenNewPeakArrives() {
        val before = columns(peaks = floatArrayOf(0.4f), elapsedSec = 1f / 30f)
        val laterPeaks = FloatArray(31)
        laterPeaks[0] = 0.4f
        laterPeaks[30] = 0.9f
        val after = columns(peaks = laterPeaks, elapsedSec = 31f / 30f)
        val col0 = 0
        assertThat(after[col0]).isWithin(0.001f).of(before[col0])
        assertThat(liveWaveformRightmostFilledColumn(after)).isGreaterThan(liveWaveformRightmostFilledColumn(before))
    }

    @Test
    fun growthPhase_pixelColumnsDoNotShiftAsRecordingGrows() {
        assertThat(findGrowthColumnStabilityViolation()).isEqualTo(-1)
    }

    @Test
    fun growthPhase_rightmostColumnOnlyMovesRight() {
        var previousRightmost = -1
        for (frame in 1..90) {
            val cols = columns(peaks = FloatArray(frame) { 0.6f }, elapsedSec = frame / 30f)
            val rightmost = liveWaveformRightmostFilledColumn(cols)
            if (previousRightmost >= 0) {
                assertThat(rightmost).isAtLeast(previousRightmost)
            }
            previousRightmost = rightmost
        }
    }

    @Test
    fun growthPhase_slotAnchorDoesNotDependOnPeakCount() {
        val width = 400f
        val anchor = liveWaveformSlotAnchorX(slot = 15, capacitySlots = 450, widthPx = width)
        assertThat(anchor).isWithin(0.01f).of(15.5f / 450f * width)
    }

    @Test
    fun growthPhase_peakBufferLag_leavesTrailingColumnsEmpty() {
        val cols = columns(peaks = FloatArray(60) { 0.5f }, elapsedSec = 3f)
        assertThat(filledCount(cols, elapsedSec = 3f)).isEqualTo(12)
    }

    @Test
    fun growthPhase_elapsedAheadOfPeaks_onlyDrawsAvailableSamples() {
        val cols = columns(peaks = FloatArray(60) { 0.5f }, elapsedSec = 3f)
        val contentEnd = (pixels * (3f / windowSec)).toInt()
        val filledBeyondPeaks = cols.drop((60f / peaksPerSec / windowSec * pixels).toInt())
            .take(contentEnd)
            .count { it > 0.01f }
        assertThat(filledBeyondPeaks).isEqualTo(0)
    }

    @Test
    fun rollingPhase_usesFullWidth() {
        val cols = columns(peaks = FloatArray(600) { 0.6f }, elapsedSec = 30f)
        assertThat(filledCount(cols, elapsedSec = 30f))
            .isEqualTo(pixels)
    }
}
