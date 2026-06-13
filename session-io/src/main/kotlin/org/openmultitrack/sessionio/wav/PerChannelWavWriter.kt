package org.openmultitrack.sessionio.wav

import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.session.ChannelMetadata
import org.openmultitrack.sessionio.session.ChannelFileNaming
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

/**
 * One WAV file per armed channel, synchronized frame clock.
 */
class PerChannelWavWriter private constructor(
    private val channelWriters: List<ChannelWriter>,
    private val sampleRateHz: Int,
    initialFramesWritten: Long,
) : AutoCloseable {
    private data class ChannelWriter(val strip: ChannelStripState, val file: File, val writer: WavWriter)

    private val channelScratch: List<FloatArray> = channelWriters.map { FloatArray(SCRATCH_FRAMES) }
    private val channelPcmScratch: List<ByteArray> = channelWriters.map { ByteArray(SCRATCH_FRAMES * 3) }
    private var framesWritten: Long = initialFramesWritten

    constructor(
        sessionDir: File,
        channelStrips: List<ChannelStripState>,
        sampleRate: Int,
    ) : this(
        channelStrips.filter { it.armed }.map { strip ->
            val file = File(sessionDir, ChannelFileNaming.fileName(strip.index, strip.label))
            ChannelWriter(strip, file, WavWriter(file, 1, sampleRate))
        },
        sampleRateHz = sampleRate,
        initialFramesWritten = 0,
    )

    fun writeInterleavedMultiChannel(samples: FloatArray, frames: Int, sourceChannelCount: Int) {
        var remaining = frames
        var offset = 0
        while (remaining > 0) {
            val n = remaining.coerceAtMost(SCRATCH_FRAMES)
            for (f in 0 until n) {
                val base = (offset + f) * sourceChannelCount
                for (ci in channelWriters.indices) {
                    val ch = channelWriters[ci].strip.index
                    channelScratch[ci][f] = if (ch < sourceChannelCount) samples[base + ch] else 0f
                }
            }
            for (ci in channelWriters.indices) {
                channelWriters[ci].writer.writeInterleavedFloat(channelScratch[ci], n)
            }
            framesWritten += n
            remaining -= n
            offset += n
        }
    }

    fun writeInterleavedPcm24(samples: ByteArray, frames: Int, sourceChannelCount: Int, bytesPerFrame: Int) {
        val subframeBytes = bytesPerFrame / sourceChannelCount.coerceAtLeast(1)
        val channelPacked = ByteArray(SCRATCH_FRAMES * subframeBytes)
        var remaining = frames
        var offset = 0
        while (remaining > 0) {
            val n = remaining.coerceAtMost(SCRATCH_FRAMES)
            for (ci in channelWriters.indices) {
                val ch = channelWriters[ci].strip.index
                if (ch >= sourceChannelCount) {
                    channelPcmScratch[ci].fill(0, 0, n * 3)
                } else {
                    for (f in 0 until n) {
                        System.arraycopy(
                            samples,
                            (offset + f) * bytesPerFrame + ch * subframeBytes,
                            channelPacked,
                            f * subframeBytes,
                            subframeBytes,
                        )
                    }
                    PcmFormatConversion.interleavedToWav24(
                        channelPacked,
                        n,
                        1,
                        subframeBytes,
                        channelPcmScratch[ci],
                    )
                }
                channelWriters[ci].writer.writePcm24(channelPcmScratch[ci], n)
            }
            framesWritten += n
            remaining -= n
            offset += n
        }
    }

    fun writeSilence(frames: Int) {
        var remaining = frames
        while (remaining > 0) {
            val n = remaining.coerceAtMost(SCRATCH_FRAMES)
            for (ci in channelWriters.indices) {
                channelScratch[ci].fill(0f, 0, n)
                channelWriters[ci].writer.writeInterleavedFloat(channelScratch[ci], n)
            }
            framesWritten += n
            remaining -= n
        }
    }

    fun filePaths(): List<String> = channelWriters.map { it.file.absolutePath }

    fun channelStrips(): List<ChannelStripState> = channelWriters.map { it.strip }

    override fun close() {
        channelWriters.forEach { it.writer.close() }
    }

    fun totalFramesWritten(): Long = framesWritten

    companion object {
        private const val SCRATCH_FRAMES = 4096

        /** Reopen per-channel WAV files from an incomplete session and continue appending. */
        fun openForResume(sessionDir: File, metadata: SessionMetadata): PerChannelWavWriter {
            val writers = metadata.channels.map { ch ->
                val strip = ChannelStripState(
                    index = ch.index,
                    label = labelFromFileName(ch.fileName),
                    displayName = ch.displayName,
                    colorArgb = ch.colorArgb,
                    armed = true,
                )
                val file = File(sessionDir, ch.fileName)
                ChannelWriter(strip, file, WavWriter.openForAppend(file, 1, metadata.sampleRate))
            }
            val initialFrames = writers.minOfOrNull { it.writer.framesWritten } ?: 0L
            return PerChannelWavWriter(writers, metadata.sampleRate, initialFrames)
        }

        private fun labelFromFileName(fileName: String): String {
            val withoutExt = fileName.removeSuffix(".wav")
            val dash = withoutExt.indexOf(" - ")
            return if (dash >= 0) withoutExt.substring(dash + 3) else ""
        }

        fun framesOnDisk(file: File, channelCount: Int = 1): Long =
            runCatching { WavReader.parseHeader(file).frameCount }
                .getOrElse {
                    val dataBytes = (file.length() - 44).coerceAtLeast(0)
                    dataBytes / (channelCount * 3)
                }
    }
}
