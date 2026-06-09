package org.openmultitrack.sessionio.wav

import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.session.SessionPlaybackDuration
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    private const val SAMPLES_PER_PEAK_READ = 2048

    fun extract(
        sessionDir: File,
        metadata: SessionMetadata,
        peaksPerSec: Float = DEFAULT_PEAKS_PER_SEC,
    ): SessionWaveformOverview {
        val plan = buildPlan(sessionDir, metadata, peaksPerSec)
        val peaksByChannel = linkedMapOf<Int, FloatArray>()
        for (ch in plan.channels) {
            val file = File(sessionDir, ch.fileName)
            require(file.isFile) { "Missing channel file: ${file.absolutePath}" }
            peaksByChannel[ch.index] = extractChannelPeaks(file, plan.peakCount)
        }
        return SessionWaveformOverview(
            peaksByChannel = peaksByChannel,
            peaksPerSec = plan.peaksPerSec,
            durationSec = plan.durationSec,
        )
    }

    /**
     * Extracts one channel at a time so the UI can show strips immediately and fill waveforms in.
     */
    fun extractIncremental(
        sessionDir: File,
        metadata: SessionMetadata,
        peaksPerSec: Float = DEFAULT_PEAKS_PER_SEC,
        onChannelComplete: (channelIndex: Int, peaks: FloatArray, completed: Int, total: Int) -> Unit,
    ): SessionWaveformOverview {
        val plan = buildPlan(sessionDir, metadata, peaksPerSec)
        val peaksByChannel = linkedMapOf<Int, FloatArray>()
        plan.channels.forEachIndexed { idx, ch ->
            val file = File(sessionDir, ch.fileName)
            require(file.isFile) { "Missing channel file: ${file.absolutePath}" }
            val peaks = extractChannelPeaks(file, plan.peakCount)
            peaksByChannel[ch.index] = peaks
            onChannelComplete(ch.index, peaks, idx + 1, plan.channels.size)
        }
        return SessionWaveformOverview(
            peaksByChannel = peaksByChannel,
            peaksPerSec = plan.peaksPerSec,
            durationSec = plan.durationSec,
        )
    }

    fun durationSec(sessionDir: File, metadata: SessionMetadata): Float =
        SessionPlaybackDuration.durationSec(sessionDir, metadata)

    /**
     * Peaks for the tail of [audioFrames] in [file], at [peaksPerSec] resolution.
     * Used to restore the live recording waveform window after resume.
     */
    fun extractTailPeaks(
        file: File,
        audioFrames: Long,
        sampleRate: Int,
        tailDurationSec: Float,
        peaksPerSec: Int,
    ): FloatArray {
        if (!file.isFile || audioFrames <= 0L || tailDurationSec <= 0f || peaksPerSec <= 0) {
            return FloatArray(0)
        }
        val rate = sampleRate.coerceAtLeast(1)
        val tailFrames = min(audioFrames, max(1L, (tailDurationSec * rate).toLong()))
        val startFrame = (audioFrames - tailFrames).coerceAtLeast(0)
        val peakCount = max(1, (tailFrames.toFloat() / rate * peaksPerSec).toInt())
        return extractChannelPeaksRange(file, startFrame, audioFrames, peakCount)
    }

    private data class ExtractionPlan(
        val channels: List<org.openmultitrack.sessionio.session.ChannelMetadata>,
        val peakCount: Int,
        val peaksPerSec: Float,
        val durationSec: Float,
    )

    private fun buildPlan(
        sessionDir: File,
        metadata: SessionMetadata,
        peaksPerSec: Float,
    ): ExtractionPlan {
        val resolved = metadata.withResolvedChannels(sessionDir)
        val sampleRate = resolved.sampleRate.coerceAtLeast(1)
        val durationFrames = durationFrames(sessionDir, resolved)
        val durationSec = durationFrames.toFloat() / sampleRate
        val peakCount = max(1, (durationSec * peaksPerSec).toInt())
        return ExtractionPlan(
            channels = resolved.channels.sortedBy { it.index },
            peakCount = peakCount,
            peaksPerSec = peaksPerSec,
            durationSec = durationSec,
        )
    }

    private fun durationFrames(sessionDir: File, metadata: SessionMetadata): Long =
        SessionPlaybackDuration.durationFrames(sessionDir, metadata)

    /** Sparse sampling: one short read per peak bucket instead of scanning the whole file. */
    private fun extractChannelPeaks(file: File, peakCount: Int): FloatArray {
        val peaks = FloatArray(peakCount)
        val scratch = FloatArray(SAMPLES_PER_PEAK_READ)
        WavReader(file).use { reader ->
            val totalFrames = reader.format.frameCount.coerceAtLeast(1)
            extractPeaksInto(
                reader = reader,
                scratch = scratch,
                peaks = peaks,
                rangeStartFrame = 0,
                rangeEndFrame = totalFrames,
            )
        }
        return peaks
    }

    private fun extractChannelPeaksRange(
        file: File,
        startFrame: Long,
        endFrame: Long,
        peakCount: Int,
    ): FloatArray {
        val peaks = FloatArray(peakCount)
        val scratch = FloatArray(SAMPLES_PER_PEAK_READ)
        WavReader(file).use { reader ->
            extractPeaksInto(
                reader = reader,
                scratch = scratch,
                peaks = peaks,
                rangeStartFrame = startFrame,
                rangeEndFrame = endFrame.coerceAtMost(reader.format.frameCount),
            )
        }
        return peaks
    }

    private fun extractPeaksInto(
        reader: WavReader,
        scratch: FloatArray,
        peaks: FloatArray,
        rangeStartFrame: Long,
        rangeEndFrame: Long,
    ) {
        val totalFrames = (rangeEndFrame - rangeStartFrame).coerceAtLeast(1)
        for (peakIdx in peaks.indices) {
            val startFrame = rangeStartFrame +
                (peakIdx.toLong() * totalFrames / peaks.size).coerceAtMost(totalFrames - 1)
            reader.seekFrame(startFrame)
            val framesToRead = min(
                SAMPLES_PER_PEAK_READ,
                max(
                    1,
                    (
                        rangeStartFrame + (peakIdx + 1).toLong() * totalFrames / peaks.size -
                            startFrame
                        ).toInt(),
                ),
            )
            val read = reader.readInterleavedFloat(scratch, framesToRead)
            if (read <= 0) break
            var blockMax = 0f
            for (f in 0 until read) {
                blockMax = max(blockMax, abs(scratch[f]))
            }
            peaks[peakIdx] = blockMax
        }
    }
}
