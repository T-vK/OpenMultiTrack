package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Captures Compose waveform pixels while simulating growth and asserts that existing bars
 * stay at fixed horizontal positions (no shaking).
 */
@RunWith(AndroidJUnit4::class)
class LiveWaveformPixelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val windowSec = 15f
    private val peaksPerSec = 30
    private val capacitySlots = (windowSec * peaksPerSec).toInt()

    @Test
    fun growth_slotCentroidStaysFixedAsRecordingGrows() {
        val probeSlot = 10
        var peaks by mutableStateOf(FloatArray(30) { 0.75f })
        var elapsed by mutableStateOf(30f / peaksPerSec)

        composeRule.setContent {
            LiveWaveformStrip(
                peaks = peaks,
                windowSec = windowSec,
                elapsedSec = elapsed,
                peaksPerSec = peaksPerSec,
                color = Color.Red,
                normalized = false,
                modifier = Modifier
                    .width(450.dp)
                    .height(56.dp),
            )
        }
        composeRule.waitForIdle()
        val baseline = composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage()

        peaks = FloatArray(90) { 0.75f }
        elapsed = 90f / peaksPerSec
        composeRule.waitForIdle()
        val grown = composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage()

        assertSlotCentroidStable(baseline, grown, probeSlot, capacitySlots)
    }

    @Test
    fun growth_frontierMovesRightWithoutShiftingInterior() {
        var peaks by mutableStateOf(FloatArray(5) { 0.75f })
        var elapsed by mutableStateOf(5f / peaksPerSec)

        composeRule.setContent {
            LiveWaveformStrip(
                peaks = peaks,
                windowSec = windowSec,
                elapsedSec = elapsed,
                peaksPerSec = peaksPerSec,
                color = Color(0xFF00AA00),
                normalized = false,
                modifier = Modifier
                    .width(450.dp)
                    .height(56.dp),
            )
        }
        composeRule.waitForIdle()

        val probeSlot = 2
        var previous = composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage()
        checkNotNull(measureSlotBarCentroidX(previous, probeSlot, capacitySlots))

        var frontierCentroids = mutableListOf(
            checkNotNull(measureSlotBarCentroidX(previous, peaks.size - 1, capacitySlots)),
        )
        for (target in listOf(12, 20, 35, 60)) {
            peaks = FloatArray(target) { 0.75f }
            elapsed = target / peaksPerSec.toFloat()
            composeRule.waitForIdle()
            val frame = composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage()
            assertSlotCentroidStable(previous, frame, probeSlot, capacitySlots)
            val frontier = checkNotNull(
                measureSlotBarCentroidX(frame, target - 1, capacitySlots),
            ) { "rightmost slot ${target - 1} bar not visible at peaks=$target" }
            assertThat(frontier).isGreaterThan(frontierCentroids.last())
            frontierCentroids.add(frontier)
            previous = frame
        }
        assertThat(frontierCentroids.distinct().size).isGreaterThan(1)
    }

    @Test
    fun emptyStrip_showsVisibleContainerBeforeRecording() {
        composeRule.setContent {
            LiveWaveformStrip(
                peaks = floatArrayOf(),
                windowSec = windowSec,
                elapsedSec = 0f,
                peaksPerSec = peaksPerSec,
                color = Color.Red,
                normalized = true,
                modifier = Modifier
                    .width(450.dp)
                    .height(56.dp),
            )
        }
        composeRule.waitForIdle()
        val image = composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage()
        val bitmap = image.asAndroidBitmap()
        assertThat(hasWaveformStripContainer(bitmap)).isTrue()
        assertThat(interiorWaveformBarPixelCount(bitmap)).isEqualTo(0)
    }

    @Test
    fun growth_rightEdgeExpandsWhileLeftEdgeStaysNearZero() {
        var peaks by mutableStateOf(FloatArray(8) { 0.8f })
        var elapsed by mutableStateOf(8f / peaksPerSec)

        composeRule.setContent {
            LiveWaveformStrip(
                peaks = peaks,
                windowSec = windowSec,
                elapsedSec = elapsed,
                peaksPerSec = peaksPerSec,
                color = Color.Blue,
                normalized = false,
                modifier = Modifier
                    .width(360.dp)
                    .height(48.dp),
            )
        }
        composeRule.waitForIdle()

        val baseline = composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage()
        val firstFrontier = checkNotNull(
            measureSlotBarCentroidX(baseline, peaks.size - 1, capacitySlots),
        )
        val leftAnchor = checkNotNull(measureSlotBarCentroidX(baseline, 0, capacitySlots))

        peaks = FloatArray(45) { 0.8f }
        elapsed = 45f / peaksPerSec
        composeRule.waitForIdle()

        val grown = composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage()
        val secondFrontier = checkNotNull(
            measureSlotBarCentroidX(grown, peaks.size - 1, capacitySlots),
        )
        assertThat(secondFrontier).isGreaterThan(firstFrontier)
        val leftAfterGrowth = checkNotNull(measureSlotBarCentroidX(grown, 0, capacitySlots))
        assertThat(kotlin.math.abs(leftAfterGrowth - leftAnchor)).isLessThan(4f)
    }
}
