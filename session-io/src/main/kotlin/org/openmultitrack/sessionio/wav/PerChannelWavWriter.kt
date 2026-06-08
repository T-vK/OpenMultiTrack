package org.openmultitrack.sessionio.wav

import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.session.ChannelFileNaming
import java.io.File

/**
 * One WAV file per armed channel, synchronized frame clock.
 */
class PerChannelWavWriter(
    private val sessionDir: File,
    channelStrips: List<ChannelStripState>,
    sampleRate: Int,
) : AutoCloseable {
    private data class ChannelWriter(val strip: ChannelStripState, val file: File, val writer: WavWriter)

    private val channelWriters: List<ChannelWriter> = channelStrips.filter { it.armed }.map { strip ->
        val file = File(sessionDir, ChannelFileNaming.fileName(strip.index, strip.label))
        ChannelWriter(strip, file, WavWriter(file, 1, sampleRate))
    }
    private val scratch = FloatArray(4096)
    private var framesWritten: Long = 0

    val sampleRateHz: Int = sampleRate

    fun writeInterleavedMultiChannel(samples: FloatArray, frames: Int, sourceChannelCount: Int) {
        val n = frames.coerceAtMost(scratch.size)
        for (cw in channelWriters) {
            val ch = cw.strip.index
            if (ch >= sourceChannelCount) continue
            for (f in 0 until n) {
                scratch[f] = samples[f * sourceChannelCount + ch]
            }
            cw.writer.writeInterleavedFloat(scratch, n)
        }
        framesWritten += n
    }

    fun writeSilence(frames: Int) {
        val n = frames.coerceAtMost(scratch.size)
        for (f in 0 until n) scratch[f] = 0f
        for (cw in channelWriters) {
            cw.writer.writeInterleavedFloat(scratch, n)
        }
        framesWritten += n
    }

    fun filePaths(): List<String> = channelWriters.map { it.file.absolutePath }

    override fun close() {
        channelWriters.forEach { it.writer.close() }
    }

    fun totalFramesWritten(): Long = framesWritten
}
