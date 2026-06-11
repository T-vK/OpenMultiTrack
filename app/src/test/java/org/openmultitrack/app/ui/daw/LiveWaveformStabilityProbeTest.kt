package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Column-level recording stability probes (10/s for 5s) mirroring the instrumented pixel tests.
 * Validates that once a timeline column has amplitude, it never returns to zero during growth.
 */
class LiveWaveformStabilityProbeTest {
    private val windowSec = 15f
    private val peaksPerSec = 30
    private val pixelCount = 90
    private val checksPerSec = 10
    private val probeSec = 5
    private val totalChecks = checksPerSec * probeSec

    private data class ProbeFrame(
        val checkIndex: Int,
        val elapsedSec: Float,
        val peakCount: Int,
        val columns: FloatArray,
    )

    private fun runProbe(
        normalized: Boolean = true,
        peakFactory: (peakIndex: Int) -> Float,
        peakCountForCheck: (checkIndex: Int, elapsedSec: Float) -> Int = { _, elapsed ->
            (elapsed * peaksPerSec).toInt().coerceAtLeast(1)
        },
        elapsedForCheck: (checkIndex: Int, elapsedSec: Float, peakCount: Int) -> Float =
            { _, elapsed, _ -> elapsed },
    ): List<ProbeFrame> {
        var ceiling = 0f
        return buildList(totalChecks) {
            repeat(totalChecks) { checkIndex ->
                val elapsedSec = (checkIndex + 1).toFloat() / checksPerSec
                val peakCount = peakCountForCheck(checkIndex, elapsedSec)
                val peaks = FloatArray(peakCount) { i -> peakFactory(i) }
                val displayElapsed = elapsedForCheck(checkIndex, elapsedSec, peakCount)
                val rawMax = peaks.maxOrNull() ?: 0f
                if (ceiling <= 1e-6f && rawMax > 1e-6f) ceiling = rawMax
                val scaled = scalePeaksForLiveDisplay(peaks, normalized, ceiling)
                val columns = liveWaveformColumnsForDisplay(
                    peaks = scaled,
                    windowSec = windowSec,
                    elapsedSec = displayElapsed,
                    peaksPerSec = peaksPerSec,
                    pixelCount = pixelCount,
                )
                add(ProbeFrame(checkIndex, displayElapsed, peakCount, columns))
            }
        }
    }

    private fun assertColumnStability(frames: List<ProbeFrame>) {
        val everFilled = BooleanArray(pixelCount)
        frames.forEach { frame ->
            val contentEnd = (pixelCount * (frame.elapsedSec / windowSec)).toInt()
                .coerceIn(0, pixelCount)
            val lockEnd = (pixelCount * ((frame.elapsedSec - 0.2f).coerceAtLeast(0f) / windowSec))
                .toInt()
                .coerceIn(0, pixelCount)
            for (px in 0 until lockEnd) {
                if (everFilled[px] && frame.columns[px] <= 0.01f) {
                    throw AssertionError(
                        "check ${frame.checkIndex} @ ${"%.2f".format(frame.elapsedSec)}s: " +
                            "column $px regressed to zero",
                    )
                }
            }
            for (px in 0 until lockEnd) {
                if (frame.columns[px] > 0.01f) everFilled[px] = true
            }
            if (contentEnd > 8) {
                val filled = frame.columns.take(lockEnd).count { it > 0.01f }
                val fraction = filled.toFloat() / lockEnd.coerceAtLeast(1)
                assertThat(fraction).isAtLeast(0.85f)
            }
        }
    }

    @Test
    fun normalizedTone_neverRegresses() {
        assertColumnStability(runProbe(normalized = true, peakFactory = { 0.85f }))
    }

    @Test
    fun rawTone_neverRegresses() {
        assertColumnStability(runProbe(normalized = false, peakFactory = { 0.12f }))
    }

    @Test
    fun varyingMax_neverRegresses() {
        assertColumnStability(
            runProbe(normalized = true, peakFactory = { i ->
                when {
                    i % 11 == 0 -> 0.98f
                    i % 3 == 0 -> 0.72f
                    else -> 0.04f
                }
            }),
        )
    }

    @Test
    fun peakBufferLag_neverRegresses() {
        assertColumnStability(
            runProbe(
                normalized = true,
                peakFactory = { 0.8f },
                peakCountForCheck = { checkIndex, elapsed ->
                    val expected = (elapsed * peaksPerSec).toInt()
                    (expected - 4 - (checkIndex % 3)).coerceAtLeast(1)
                },
            ),
        )
    }

    @Test
    fun elapsedAheadOfPeaks_neverRegresses() {
        assertColumnStability(
            runProbe(
                normalized = true,
                peakFactory = { 0.8f },
                peakCountForCheck = { _, elapsed ->
                    ((elapsed - 0.25f).coerceAtLeast(0.05f) * peaksPerSec).toInt().coerceAtLeast(1)
                },
            ),
        )
    }
}
