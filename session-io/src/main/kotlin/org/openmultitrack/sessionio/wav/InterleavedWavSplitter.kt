package org.openmultitrack.sessionio.wav

import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.session.ChannelFileNaming
import java.io.File
import java.io.RandomAccessFile

/** Splits a multi-channel interleaved PCM WAV into per-channel mono 24-bit WAVs. */
object InterleavedWavSplitter {
    private const val RIFF_HEADER_SIZE = 44
    private const val SCRATCH_FRAMES = 4096

    fun splitToPerChannel(
        interleavedFile: File,
        sessionDir: File,
        channelStrips: List<ChannelStripState>,
        sourceChannelCount: Int,
        sampleRate: Int,
    ) {
        val format = WavReader.parseHeader(interleavedFile)
        require(format.channelCount == sourceChannelCount) {
            "interleaved channel count ${format.channelCount} != $sourceChannelCount"
        }
        val armed = channelStrips.filter { it.armed }
        val writers = armed.associate { strip ->
            strip.index to WavWriter(
                File(sessionDir, ChannelFileNaming.fileName(strip.index, strip.label)),
                1,
                sampleRate,
            )
        }
        val srcBytesPerFrame = sourceChannelCount * (format.bitsPerSample / 8)
        val scratch = ByteArray(SCRATCH_FRAMES * srcBytesPerFrame)
        val channelScratch = ByteArray(SCRATCH_FRAMES * 3)
        RandomAccessFile(interleavedFile, "r").use { input ->
            input.seek(format.dataOffset)
            var framesRemaining = format.frameCount
            while (framesRemaining > 0) {
                val chunkFrames = minOf(framesRemaining, SCRATCH_FRAMES.toLong()).toInt()
                val chunkBytes = chunkFrames * srcBytesPerFrame
                input.readFully(scratch, 0, chunkBytes)
                for ((index, writer) in writers) {
                    if (index >= sourceChannelCount) continue
                    if (format.bitsPerSample == 32) {
                        PcmFormatConversion.interleavedToWav24(
                            scratch,
                            chunkFrames,
                            sourceChannelCount,
                            srcBytesPerFrame,
                            channelScratch,
                            destChannel = index,
                        )
                    } else {
                        var dst = 0
                        val chOffset = index * 3
                        for (f in 0 until chunkFrames) {
                            val src = f * srcBytesPerFrame + chOffset
                            channelScratch[dst++] = scratch[src]
                            channelScratch[dst++] = scratch[src + 1]
                            channelScratch[dst++] = scratch[src + 2]
                        }
                    }
                    writer.writePcm24(channelScratch, chunkFrames)
                }
                framesRemaining -= chunkFrames
            }
        }
        writers.values.forEach { it.close() }
    }
}
