package org.openmultitrack.app.audio

import androidx.compose.runtime.Immutable
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.abs
import kotlin.math.max

@Immutable
data class LiveWaveformSnapshot(
    /** Oldest→newest peaks for display (owned copy; length equals [peakCount]). */
    val peaks: FloatArray,
    val peakCount: Int,
    /** Total time slots in the rolling window (e.g. 120 s × 30 Hz). */
    val capacity: Int,
    val generation: Long = 0L,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LiveWaveformSnapshot) return false
        return peakCount == other.peakCount &&
            capacity == other.capacity &&
            generation == other.generation &&
            peaks.contentEquals(other.peaks)
    }

    override fun hashCode(): Int {
        var result = peaks.contentHashCode()
        result = 31 * result + peakCount
        result = 31 * result + capacity
        result = 31 * result + generation.hashCode()
        return result
    }
}

/** Thread-safe rolling peak buffer for live waveform display (one channel). */
class LiveWaveformRing(
    private val capacityPeaks: Int,
) {
    val capacity: Int get() = capacityPeaks

    private val peaks = FloatArray(capacityPeaks)
    private val uiPeaks = FloatArray(capacityPeaks)
    private var writeIndex = 0
    private var filled = 0
    private var uiFilled = 0
    private var generation = 0L
    private val lock = ReentrantReadWriteLock()

    fun pushPeak(sample: Float) {
        lock.write {
            peaks[writeIndex] = abs(sample)
            writeIndex = (writeIndex + 1) % capacityPeaks
            filled = minOf(filled + 1, capacityPeaks)
        }
    }

    /** Copies the ring into the UI buffer — call once per capture emit tick. */
    fun publishUiSnapshot() {
        lock.write {
            uiFilled = filled
            if (filled == 0) {
                generation++
                return
            }
            val start = if (filled < capacityPeaks) 0 else writeIndex
            for (i in 0 until filled) {
                uiPeaks[i] = peaks[(start + i) % capacityPeaks]
            }
            generation++
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
            publishUiSnapshotLocked()
        }
    }

    /** Returns an owned copy of the last published UI buffer for Compose. */
    fun uiSnapshot(normalize: Boolean = false): LiveWaveformSnapshot = lock.read {
        copyUiPeaksLocked(normalize)
    }

    /** Returns peaks oldest→newest for UI. */
    fun snapshot(normalize: Boolean = false): LiveWaveformSnapshot = lock.read {
        val out = FloatArray(filled)
        val start = if (filled < capacityPeaks) 0 else writeIndex
        for (i in 0 until filled) {
            out[i] = peaks[(start + i) % capacityPeaks]
        }
        normalizePeaksInPlace(out, normalize)
        LiveWaveformSnapshot(out, filled, capacityPeaks, generation)
    }

    private fun copyUiPeaksLocked(normalize: Boolean): LiveWaveformSnapshot {
        val out = FloatArray(uiFilled)
        for (i in 0 until uiFilled) {
            out[i] = uiPeaks[i]
        }
        normalizePeaksInPlace(out, normalize)
        return LiveWaveformSnapshot(out, uiFilled, capacityPeaks, generation)
    }

    private fun normalizePeaksInPlace(out: FloatArray, normalize: Boolean) {
        if (!normalize || out.isEmpty()) return
        val peak = out.max()
        if (peak > 1e-6f) {
            for (i in out.indices) out[i] /= peak
        }
    }

    private fun publishUiSnapshotLocked() {
        uiFilled = filled
        if (filled == 0) {
            generation++
            return
        }
        val start = if (filled < capacityPeaks) 0 else writeIndex
        for (i in 0 until filled) {
            uiPeaks[i] = peaks[(start + i) % capacityPeaks]
        }
        generation++
    }

    companion object {
        fun forWindow(sampleRate: Int, windowSec: Float, peaksPerSecond: Int = 50): LiveWaveformRing {
            val cap = max(10, (windowSec * peaksPerSecond).toInt())
            return LiveWaveformRing(cap)
        }
    }
}
