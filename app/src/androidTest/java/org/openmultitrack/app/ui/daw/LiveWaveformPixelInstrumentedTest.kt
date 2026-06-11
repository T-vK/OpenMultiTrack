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
 * 10 pixel-color checks per second for the first 5 seconds of recording.
 * Any column that has ever shown bar pixels in the interior must never become background again.
 */
@RunWith(AndroidJUnit4::class)
class LiveWaveformPixelInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val windowSec = 15f
    private val peaksPerSec = 30
    private val stripWidthDp = 900.dp
    private val stripHeightDp = 64.dp
    private val checksPerSec = RECORDING_STABILITY_CHECKS_PER_SEC
    private val probeSec = RECORDING_STABILITY_PROBE_SEC
    private val totalChecks = checksPerSec * probeSec

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
        normalized: Boolean = true,
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

    private fun captureProbeFrame(checkIndex: Int, elapsedSec: Float, peakCount: Int): RecordingProbeFrame =
        RecordingProbeFrame(
            checkIndex = checkIndex,
            elapsedSec = elapsedSec,
            peakCount = peakCount,
            image = composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage(),
        )

    private fun runRecordingProbe(
        normalized: Boolean = true,
        peakFactory: (peakIndex: Int) -> Float,
        peakCountForCheck: (checkIndex: Int, elapsedSec: Float) -> Int = { _, elapsed ->
            (elapsed * peaksPerSec).toInt().coerceAtLeast(1)
        },
        elapsedForCheck: (checkIndex: Int, elapsedSec: Float, peakCount: Int) -> Float =
            { _, elapsed, _ -> elapsed },
    ): List<RecordingProbeFrame> {
        val strip = mountLiveStrip(
            peaks = floatArrayOf(0.01f),
            elapsed = 1f / checksPerSec,
            normalized = normalized,
        )
        val frames = ArrayList<RecordingProbeFrame>(totalChecks)
        repeat(totalChecks) { checkIndex ->
            val elapsedSec = (checkIndex + 1).toFloat() / checksPerSec
            val peakCount = peakCountForCheck(checkIndex, elapsedSec)
            val peaks = FloatArray(peakCount) { i -> peakFactory(i) }
            val displayElapsed = elapsedForCheck(checkIndex, elapsedSec, peakCount)
            strip.update(peaks, displayElapsed, normalized)
            frames.add(captureProbeFrame(checkIndex, displayElapsed, peakCount))
        }
        return frames
    }

    @Test
    fun recording_firstFiveSeconds_tenChecksPerSecond_normalizedTone_neverRegresses() {
        val frames = runRecordingProbe(
            normalized = true,
            peakFactory = { 0.85f },
        )
        assertThat(frames).hasSize(totalChecks)
        assertRecordingStabilityProbe(frames, windowSec)
    }

    @Test
    fun recording_firstFiveSeconds_tenChecksPerSecond_rawTone_neverRegresses() {
        val frames = runRecordingProbe(
            normalized = false,
            peakFactory = { 0.12f },
        )
        assertRecordingStabilityProbe(frames, windowSec)
    }

    @Test
    fun recording_firstFiveSeconds_tenChecksPerSecond_varyingMax_neverRegresses() {
        val frames = runRecordingProbe(
            normalized = true,
            peakFactory = { i ->
                when {
                    i % 11 == 0 -> 0.98f
                    i % 3 == 0 -> 0.72f
                    else -> 0.04f
                }
            },
        )
        assertRecordingStabilityProbe(
            frames = frames,
            windowSec = windowSec,
            requireDenseInterior = false,
        )
    }

    @Test
    fun recording_firstFiveSeconds_tenChecksPerSecond_bassLikeAttacks_neverRegresses() {
        val frames = runRecordingProbe(
            normalized = true,
            peakFactory = { i ->
                if (i % 15 == 0) 0.92f else if (i % 5 == 0) 0.18f else 0.02f
            },
        )
        assertRecordingStabilityProbe(
            frames = frames,
            windowSec = windowSec,
            requireDenseInterior = false,
        )
    }

    @Test
    fun recording_firstFiveSeconds_tenChecksPerSecond_peakBufferLag_neverRegresses() {
        val frames = runRecordingProbe(
            normalized = true,
            peakFactory = { 0.8f },
            peakCountForCheck = { checkIndex, elapsed ->
                val expected = (elapsed * peaksPerSec).toInt()
                (expected - 4 - (checkIndex % 3)).coerceAtLeast(1)
            },
            elapsedForCheck = { _, elapsed, _ -> elapsed },
        )
        assertRecordingStabilityProbe(frames, windowSec)
    }

    @Test
    fun recording_firstFiveSeconds_tenChecksPerSecond_windowMaxDrops_neverRegresses() {
        val strip = mountLiveStrip(
            peaks = FloatArray(30) { 0.95f },
            elapsed = 1f,
            normalized = true,
        )
        val frames = ArrayList<RecordingProbeFrame>(totalChecks)
        repeat(totalChecks) { checkIndex ->
            val elapsedSec = (checkIndex + 1).toFloat() / checksPerSec
            val peakCount = (elapsedSec * peaksPerSec).toInt().coerceAtLeast(1)
            val peaks = if (checkIndex < totalChecks / 2) {
                FloatArray(peakCount) { 0.95f }
            } else {
                FloatArray(peakCount) { 0.04f }
            }
            strip.update(peaks, elapsedSec, normalized = true)
            frames.add(captureProbeFrame(checkIndex, elapsedSec, peakCount))
        }
        assertRecordingStabilityProbe(
            frames = frames,
            windowSec = windowSec,
            requireDenseInterior = false,
        )
    }

    @Test
    fun recording_firstFiveSeconds_tenChecksPerSecond_elapsedAheadOfPeaks_neverRegresses() {
        val frames = runRecordingProbe(
            normalized = true,
            peakFactory = { 0.8f },
            peakCountForCheck = { _, elapsed ->
                ((elapsed - 0.25f).coerceAtLeast(0.05f) * peaksPerSec).toInt().coerceAtLeast(1)
            },
        )
        assertRecordingStabilityProbe(frames, windowSec)
    }

    @Test
    fun emptyStrip_showsVisibleContainerBeforeRecording() {
        mountLiveStrip(peaks = floatArrayOf(), elapsed = 0f)
        val bitmap = composeRule.onNodeWithTag(LIVE_WAVEFORM_TEST_TAG).captureToImage().asAndroidBitmap()
        assertThat(hasWaveformStripContainer(bitmap)).isTrue()
        assertThat(interiorWaveformBarPixelCount(bitmap)).isEqualTo(0)
    }
}
