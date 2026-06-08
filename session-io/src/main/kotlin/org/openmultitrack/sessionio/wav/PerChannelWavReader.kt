package org.openmultitrack.sessionio.wav

import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

/**
 * Reads per-channel mono WAV files as one synchronized interleaved multichannel stream.
 */
class PerChannelWavReader private constructor(
    private val readers: List<WavReader>,
) : AutoCloseable {
    val channelCount: Int = readers.size
    val sampleRate: Int = readers.firstOrNull()?.format?.sampleRate ?: 48_000
    val frameCount: Long = readers.minOfOrNull { it.format.frameCount } ?: 0L

    private val monoScratch = FloatArray(4096)

    fun seekFrame(frame: Long) {
        readers.forEach { it.seekFrame(frame) }
    }

    fun readInterleavedFloat(buffer: FloatArray, frames: Int): Int {
        if (readers.isEmpty()) return 0
        val ch = readers.size
        val expected = frames * ch
        require(buffer.size >= expected) { "buffer too small: ${buffer.size} < $expected" }
        var minFrames = frames
        val planes = Array(ch) { FloatArray(frames) }
        for (i in readers.indices) {
            val got = readers[i].readInterleavedFloat(monoScratch, frames)
            if (got <= 0) return 0
            minFrames = minOf(minFrames, got)
            System.arraycopy(monoScratch, 0, planes[i], 0, got)
        }
        for (f in 0 until minFrames) {
            for (c in 0 until ch) {
                buffer[f * ch + c] = planes[c][f]
            }
        }
        return minFrames
    }

    override fun close() {
        readers.forEach { it.close() }
    }

    companion object {
        fun open(sessionDir: File, metadata: SessionMetadata): PerChannelWavReader {
            val readers = metadata.channels.sortedBy { it.index }.map { ch ->
                val file = File(sessionDir, ch.fileName)
                require(file.isFile) { "Missing channel file: ${file.absolutePath}" }
                WavReader(file)
            }
            return PerChannelWavReader(readers)
        }
    }
}
