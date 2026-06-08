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

    private val scratch = FloatArray(4096)
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
            val n = remaining.coerceAtMost(scratch.size)
            for (cw in channelWriters) {
                val ch = cw.strip.index
                if (ch >= sourceChannelCount) continue
                for (f in 0 until n) {
                    scratch[f] = samples[(offset + f) * sourceChannelCount + ch]
                }
                cw.writer.writeInterleavedFloat(scratch, n)
            }
            framesWritten += n
            remaining -= n
            offset += n
        }
    }

    fun writeSilence(frames: Int) {
        var remaining = frames
        while (remaining > 0) {
            val n = remaining.coerceAtMost(scratch.size)
            for (f in 0 until n) scratch[f] = 0f
            for (cw in channelWriters) {
                cw.writer.writeInterleavedFloat(scratch, n)
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
