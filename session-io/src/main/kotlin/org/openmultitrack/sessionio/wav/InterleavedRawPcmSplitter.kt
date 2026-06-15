package org.openmultitrack.sessionio.wav

import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.session.ChannelFileNaming
import java.io.File
import java.io.RandomAccessFile

/** Splits raw interleaved 32-bit PCM into per-channel mono 24-bit WAV files. */
object InterleavedRawPcmSplitter {
    private const val SCRATCH_FRAMES = 4096

    fun splitToPerChannel(
        rawFile: File,
        sessionDir: File,
        channelStrips: List<ChannelStripState>,
        sourceChannelCount: Int,
        sampleRate: Int,
        bytesPerFrame: Int,
        frameCount: Long,
        sourceFrameOffset: Long = 0L,
    ) {
        val armed = channelStrips.filter { it.armed }
        val writers = armed.associate { strip ->
            strip.index to WavWriter(
                File(sessionDir, ChannelFileNaming.fileName(strip.index, strip.label)),
                1,
                sampleRate,
            )
        }
        appendRangeToWriters(
            rawFile = rawFile,
            writers = writers,
            sourceChannelCount = sourceChannelCount,
            bytesPerFrame = bytesPerFrame,
            sourceFrameOffset = sourceFrameOffset,
            frameCount = frameCount,
        )
        writers.values.forEach { it.close() }
    }

    /** Appends a frame range from [rawFile] into already-open per-channel [writers]. */
    fun appendRangeToWriters(
        rawFile: File,
        writers: Map<Int, WavWriter>,
        sourceChannelCount: Int,
        bytesPerFrame: Int,
        sourceFrameOffset: Long,
        frameCount: Long,
    ) {
        require(bytesPerFrame == sourceChannelCount * 4) {
            "expected 32-bit interleaved PCM bytesPerFrame=$bytesPerFrame"
        }
        require(sourceFrameOffset >= 0L) { "sourceFrameOffset must be non-negative" }
        if (frameCount <= 0L || writers.isEmpty()) return
        val scratch = ByteArray(SCRATCH_FRAMES * bytesPerFrame)
        val channelScratch = ByteArray(SCRATCH_FRAMES * 3)
        var framesRemaining = frameCount
        RandomAccessFile(rawFile, "r").use { input ->
            input.seek(sourceFrameOffset * bytesPerFrame)
            while (framesRemaining > 0) {
                val chunkFrames = minOf(framesRemaining, SCRATCH_FRAMES.toLong()).toInt()
                val chunkBytes = chunkFrames * bytesPerFrame
                input.readFully(scratch, 0, chunkBytes)
                for ((index, writer) in writers) {
                    if (index >= sourceChannelCount) continue
                    PcmFormatConversion.interleavedToWav24(
                        scratch,
                        chunkFrames,
                        sourceChannelCount,
                        bytesPerFrame,
                        channelScratch,
                        destChannel = index,
                    )
                    writer.writePcm24(channelScratch, chunkFrames)
                }
                framesRemaining -= chunkFrames
            }
        }
    }
}
