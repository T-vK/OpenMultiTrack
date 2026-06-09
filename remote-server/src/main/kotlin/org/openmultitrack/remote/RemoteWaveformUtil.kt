package org.openmultitrack.remote

import org.openmultitrack.domain.remote.RemoteProtocol
import kotlin.math.ceil

object RemoteWaveformUtil {
    fun quantizeTail(peaks: FloatArray, tailCount: Int = RemoteProtocol.LIVE_WAVEFORM_TAIL): ByteArray {
        val start = (peaks.size - tailCount).coerceAtLeast(0)
        val slice = peaks.copyOfRange(start, peaks.size)
        return ByteArray(slice.size) { i ->
            (slice[i].coerceIn(0f, 1f) * 255f).toInt().toByte()
        }
    }

    fun decodeTail(u8: ByteArray): FloatArray =
        FloatArray(u8.size) { (u8[it].toInt() and 0xFF) / 255f }

    /**
     * Merges a rolling tail update into an existing live waveform buffer by finding
     * the longest suffix/prefix overlap and appending only new samples.
     */
    fun mergeLiveWaveformTail(
        existingPeaks: FloatArray?,
        newTail: FloatArray,
        capacity: Int,
    ): FloatArray {
        if (newTail.isEmpty()) return existingPeaks?.copyOf() ?: FloatArray(0)
        val existing = existingPeaks
        if (existing == null || existing.isEmpty()) {
            return if (newTail.size <= capacity) {
                newTail
            } else {
                newTail.copyOfRange(newTail.size - capacity, newTail.size)
            }
        }
        var overlap = 0
        val maxOverlap = minOf(existing.size, newTail.size)
        for (len in maxOverlap downTo 1) {
            var matches = true
            for (i in 0 until len) {
                if (existing[existing.size - len + i] != newTail[i]) {
                    matches = false
                    break
                }
            }
            if (matches) {
                overlap = len
                break
            }
        }
        val toAppend = if (overlap < newTail.size) {
            newTail.copyOfRange(overlap, newTail.size)
        } else {
            FloatArray(0)
        }
        val combined = if (toAppend.isEmpty()) existing else existing + toAppend
        return if (combined.size <= capacity) {
            combined
        } else {
            combined.copyOfRange(combined.size - capacity, combined.size)
        }
    }

    fun downsamplePeaksMax(source: FloatArray, targetCount: Int): FloatArray {
        if (source.isEmpty() || targetCount <= 0) return FloatArray(0)
        if (source.size <= targetCount) return source.copyOf()
        val out = FloatArray(targetCount)
        val bucket = source.size.toFloat() / targetCount
        for (i in 0 until targetCount) {
            val from = (i * bucket).toInt()
            val to = ceil((i + 1) * bucket).toInt().coerceAtMost(source.size)
            var max = 0f
            for (j in from until to) {
                if (source[j] > max) max = source[j]
            }
            out[i] = max
        }
        return out
    }

    fun slicePeaks(
        peaks: FloatArray,
        peaksPerSec: Float,
        startSec: Float,
        windowSec: Float,
        maxPoints: Int = RemoteProtocol.MAX_WAVEFORM_POINTS,
    ): FloatArray {
        if (peaks.isEmpty() || peaksPerSec <= 0f) return FloatArray(0)
        val startIdx = (startSec * peaksPerSec).toInt().coerceIn(0, peaks.lastIndex)
        val endIdx = ((startSec + windowSec) * peaksPerSec).toInt().coerceIn(startIdx, peaks.size)
        val slice = peaks.copyOfRange(startIdx, endIdx)
        return downsamplePeaksMax(slice, maxPoints)
    }
}
