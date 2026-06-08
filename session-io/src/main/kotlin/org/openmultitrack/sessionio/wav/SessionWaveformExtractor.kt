package org.openmultitrack.sessionio.wav

import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Low-resolution peak overview for an entire multitrack session. */
data class SessionWaveformOverview(
    val peaksByChannel: Map<Int, FloatArray>,
    /** Peaks per second in each channel array. */
    val peaksPerSec: Float,
    val durationSec: Float,
) {
    val peakCount: Int get() = peaksByChannel.values.firstOrNull()?.size ?: 0
}

object SessionWaveformExtractor {
    const val DEFAULT_PEAKS_PER_SEC = 4f

    fun extract(
        sessionDir: File,
        metadata: SessionMetadata,
        peaksPerSec: Float = DEFAULT_PEAKS_PER_SEC,
    ): SessionWaveformOverview {
        val resolved = metadata.withResolvedChannels(sessionDir)
        val sampleRate = resolved.sampleRate.coerceAtLeast(1)
        val durationFrames = durationFrames(sessionDir, resolved)
        val durationSec = durationFrames.toFloat() / sampleRate
        val peakCount = max(1, (durationSec * peaksPerSec).toInt())
        val blockFrames = max(1, (sampleRate / peaksPerSec).toInt())
        val channels = resolved.channels.sortedBy { it.index }
        val peaksByChannel = linkedMapOf<Int, FloatArray>()
        for (ch in channels) {
            val file = File(sessionDir, ch.fileName)
            require(file.isFile) { "Missing channel file: ${file.absolutePath}" }
            peaksByChannel[ch.index] = extractChannelPeaks(file, peakCount, blockFrames)
        }
        return SessionWaveformOverview(
            peaksByChannel = peaksByChannel,
            peaksPerSec = peaksPerSec,
            durationSec = durationSec,
        )
    }

    private fun durationFrames(sessionDir: File, metadata: SessionMetadata): Long {
        if (metadata.timelineFramesWritten > 0) return metadata.timelineFramesWritten
        return metadata.channels.minOfOrNull { ch ->
            PerChannelWavWriter.framesOnDisk(File(sessionDir, ch.fileName))
        } ?: 0L
    }

    private fun extractChannelPeaks(file: File, peakCount: Int, blockFrames: Int): FloatArray {
        val peaks = FloatArray(peakCount)
        val scratch = FloatArray(blockFrames)
        WavReader(file).use { reader ->
            var peakIdx = 0
            while (peakIdx < peakCount) {
                val read = reader.readInterleavedFloat(scratch, blockFrames)
                if (read <= 0) break
                var blockMax = 0f
                for (f in 0 until read) {
                    blockMax = max(blockMax, abs(scratch[f]))
                }
                peaks[peakIdx++] = blockMax
            }
        }
        return peaks
    }
}
