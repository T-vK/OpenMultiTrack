package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pixel-color tests: scan every column in the recorded region for background bleed,
 * gap size, and occupancy flicker while the recording frontier advances.
 */
@RunWith(AndroidJUnit4::class)
class LiveWaveformPixelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val windowSec = 15f
    private val peaksPerSec = 30
    private val capacitySlots = (windowSec * peaksPerSec).toInt()
    private val stripWidthDp = 900.dp
    private val stripHeightDp = 64.dp

    private fun captureStrip(): ImageBitmap =
        composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage()

    private class LiveStripHandle(
        private val composeRule: androidx.compose.ui.test.junit4.ComposeContentTestRule,
        private val peaksState: androidx.compose.runtime.MutableState<FloatArray>,
        private val elapsedState: androidx.compose.runtime.MutableState<Float>,
        private val normalizedState: androidx.compose.runtime.MutableState<Boolean>,
    ) {
        fun update(peaks: FloatArray, elapsed: Float, normalized: Boolean = normalizedState.value) {
            peaksState.value = peaks
            elapsedState.value = elapsed
            normalizedState.value = normalized
            composeRule.waitForIdle()
        }
    }

    private fun mountLiveStrip(
        peaks: FloatArray,
        elapsed: Float,
        normalized: Boolean = false,
        color: Color = Color(0xFF22CC44),
    ): LiveStripHandle {
        val peaksState = mutableStateOf(peaks)
        val elapsedState = mutableStateOf(elapsed)
        val normalizedState = mutableStateOf(normalized)
        composeRule.setContent {
            val livePeaks by peaksState
            val liveElapsed by elapsedState
            val liveNormalized by normalizedState
            LiveWaveformStrip(
                peaks = livePeaks,
                windowSec = windowSec,
                elapsedSec = liveElapsed,
                peaksPerSec = peaksPerSec,
                color = color,
                normalized = liveNormalized,
                modifier = Modifier
                    .width(stripWidthDp)
                    .height(stripHeightDp),
            )
        }
        composeRule.waitForIdle()
        return LiveStripHandle(composeRule, peaksState, elapsedState, normalizedState)
    }

    @Test
    fun recordedRegion_continuousTone_hasNoBackgroundGaps() {
        mountLiveStrip(
            peaks = FloatArray(5 * peaksPerSec) { 0.85f },
            elapsed = 5f,
        )
        val bitmap = captureStrip().asAndroidBitmap()
        val endX = recordedRegionEndX(bitmap.width, 5f, windowSec)
        assertRecordedRegionDense(bitmap, endX, minOccupiedFraction = 0.92f, maxGapPx = 4)
    }

    @Test
    fun recordedRegion_interiorColumnsDoNotFlicker_asFrontierAdvances() {
        val strip = mountLiveStrip(
            peaks = FloatArray(2 * peaksPerSec) { 0.85f },
            elapsed = 2f,
        )
        var previous = captureStrip()
        for (second in listOf(3, 4, 5, 6, 7, 8)) {
            val interiorEndX = recordedRegionEndX(
                previous.asAndroidBitmap().width,
                (second - 1).toFloat(),
                windowSec,
            )
            strip.update(
                peaks = FloatArray(second * peaksPerSec) { 0.85f },
                elapsed = second.toFloat(),
            )
            val current = captureStrip()
            if (interiorEndX > 12) {
                assertRecordedInteriorOccupancyStable(previous, current, interiorEndX)
                assertRecordedInteriorStaysFilled(previous, current, interiorEndX)
            }
            previous = current
        }
    }

    @Test
    fun recordedRegion_captureTicks_interiorNeverFlickers() {
        val strip = mountLiveStrip(
            peaks = FloatArray(peaksPerSec) { 0.85f },
            elapsed = 1f,
        )
        var previous = captureStrip()
        repeat(45) { tick ->
            val peakCount = peaksPerSec + tick + 1
            strip.update(
                peaks = FloatArray(peakCount) { 0.85f },
                elapsed = peakCount / peaksPerSec.toFloat(),
            )
            val current = captureStrip()
            val interiorEndX = recordedRegionEndX(
                previous.asAndroidBitmap().width,
                (peakCount - 1) / peaksPerSec.toFloat(),
                windowSec,
            )
            if (interiorEndX > 12) {
                assertRecordedInteriorOccupancyStable(previous, current, interiorEndX)
                assertRecordedInteriorStaysFilled(previous, current, interiorEndX)
            }
            previous = current
        }
    }

    @Test
    fun recordedRegion_normalizationVaryingMax_interiorColumnsStable() {
        val strip = mountLiveStrip(
            peaks = FloatArray(60) { if (it % 2 == 0) 0.8f else 0.04f },
            elapsed = 2f,
            normalized = true,
        )
        var previous = captureStrip()
        repeat(25) { step ->
            val n = 60 + step * 3
            val elapsed = n / peaksPerSec.toFloat()
            strip.update(
                peaks = FloatArray(n) { i ->
                    when {
                        i % 7 == 0 -> 0.95f
                        i % 2 == 0 -> 0.75f
                        else -> 0.03f
                    }
                },
                elapsed = elapsed,
                normalized = true,
            )
            val current = captureStrip()
            val interiorEndX = recordedRegionEndX(
                previous.asAndroidBitmap().width,
                elapsed - 3f / peaksPerSec,
                windowSec,
            )
            if (interiorEndX > 20) {
                assertRecordedInteriorOccupancyStable(previous, current, interiorEndX)
            }
            previous = current
        }
    }

    @Test
    fun recordedRegion_peakBufferLag_noPhantomInteriorChange() {
        val strip = mountLiveStrip(
            peaks = FloatArray(40) { 0.8f },
            elapsed = 3f,
        )
        var previous = captureStrip()
        repeat(15) { step ->
            val elapsed = 3f + step * 0.1f
            strip.update(
                peaks = FloatArray(40 + step * 2) { 0.8f },
                elapsed = elapsed,
            )
            val current = captureStrip()
            val interiorEndX = recordedRegionEndX(
                previous.asAndroidBitmap().width,
                elapsed - 0.15f,
                windowSec,
            )
            if (interiorEndX > 15) {
                assertRecordedInteriorOccupancyStable(previous, current, interiorEndX)
                assertRecordedInteriorStaysFilled(previous, current, interiorEndX)
            }
            previous = current
        }
    }

    @Test
    fun growth_leftToRight_secondMarkersAppearThenLock() {
        var peaks by mutableStateOf(floatArrayOf())
        var elapsed by mutableStateOf(0f)

        composeRule.setContent {
            LiveWaveformStrip(
                peaks = peaks,
                windowSec = windowSec,
                elapsedSec = elapsed,
                peaksPerSec = peaksPerSec,
                color = Color(0xFF3366CC),
                normalized = true,
                modifier = Modifier
                    .width(stripWidthDp)
                    .height(stripHeightDp),
            )
        }

        val frames = linkedMapOf<Int, ImageBitmap>()
        for (second in 0..9) {
            if (second == 0) {
                peaks = floatArrayOf()
                elapsed = 0f
            } else {
                peaks = FloatArray(second * peaksPerSec) { 0.85f }
                elapsed = second.toFloat()
            }
            composeRule.waitForIdle()
            frames[second] = captureStrip()
        }

        assertLeftToRightWaveformGrowth(
            framesByElapsedSec = frames,
            capacitySlots = capacitySlots,
            peaksPerSec = peaksPerSec,
            windowSec = windowSec,
            maxSecond = 9,
            requireDenseInterior = true,
        )
    }

    @Test
    fun emptyStrip_showsVisibleContainerBeforeRecording() {
        mountLiveStrip(peaks = floatArrayOf(), elapsed = 0f)
        val bitmap = captureStrip().asAndroidBitmap()
        assertThat(hasWaveformStripContainer(bitmap)).isTrue()
        assertThat(interiorWaveformBarPixelCount(bitmap)).isEqualTo(0)
    }
}
