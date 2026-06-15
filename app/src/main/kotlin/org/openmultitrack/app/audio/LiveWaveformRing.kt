package org.openmultitrack.app.audio

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.abs
import kotlin.math.max

data class LiveWaveformSnapshot(
    val peaks: FloatArray,
    /** Total time slots in the rolling window (e.g. 15 s × 30 Hz = 450). */
    val capacity: Int,
)

/** Thread-safe rolling peak buffer for live waveform display (one channel). */
class LiveWaveformRing(
    private val capacityPeaks: Int,
) {
    val capacity: Int get() = capacityPeaks

    private val peaks = FloatArray(capacityPeaks)
    private var writeIndex = 0
    private var filled = 0
    private val lock = ReentrantReadWriteLock()

    fun pushPeak(sample: Float) {
        lock.write {
            peaks[writeIndex] = abs(sample)
            writeIndex = (writeIndex + 1) % capacityPeaks
            filled = minOf(filled + 1, capacityPeaks)
        }
    }

    /** Bulk-load peaks oldest→newest (e.g. when resuming an interrupted recording). */
    fun seedPeaks(samples: FloatArray) {
        if (samples.isEmpty()) return
        lock.write {
            for (sample in samples) {
                peaks[writeIndex] = abs(sample)
                writeIndex = (writeIndex + 1) % capacityPeaks
                filled = minOf(filled + 1, capacityPeaks)
            }
        }
    }

    /** Returns peaks oldest→newest for UI (may be shorter than [capacity] until the window fills). */
    fun snapshot(normalize: Boolean = false): LiveWaveformSnapshot =
        snapshotPeaks(maxCount = filled, normalize = normalize)

    /** Returns the most recent [maxCount] peaks oldest→newest (for viewport-sized UI copies). */
    fun snapshotRecent(maxCount: Int, normalize: Boolean = false): LiveWaveformSnapshot =
        snapshotPeaks(maxCount = maxCount.coerceAtLeast(0), normalize = normalize)

    private fun snapshotPeaks(maxCount: Int, normalize: Boolean): LiveWaveformSnapshot = lock.read {
        val count = minOf(filled, maxCount)
        val out = FloatArray(count)
        val start = when {
            count <= 0 -> 0
            filled < capacityPeaks -> maxOf(0, filled - count)
            else -> (writeIndex - count + capacityPeaks) % capacityPeaks
        }
        for (i in 0 until count) {
            out[i] = peaks[(start + i) % capacityPeaks]
        }
        if (normalize && out.isNotEmpty()) {
            val peak = out.max()
            if (peak > 1e-6f) {
                for (i in out.indices) out[i] /= peak
            }
        }
        LiveWaveformSnapshot(out, capacityPeaks)
    }

    companion object {
        fun forWindow(sampleRate: Int, windowSec: Float, peaksPerSecond: Int = 50): LiveWaveformRing {
            val cap = max(10, (windowSec * peaksPerSecond).toInt())
            return LiveWaveformRing(cap)
        }
    }
}
