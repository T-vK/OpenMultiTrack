package org.openmultitrack.sessionio.wav

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Streaming PCM WAV writer (24-bit integer, interleaved).
 * Header is finalized on [close].
 */
class WavWriter(
    private val file: File,
    private val channelCount: Int,
    private val sampleRate: Int,
) : AutoCloseable {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytesWritten: Long = 0
    private var closed = false

    init {
        require(channelCount in 1..32) { "channelCount out of range: $channelCount" }
        require(sampleRate > 0) { "sampleRate must be positive" }
        writePlaceholderHeader()
    }

    fun writeInterleavedFloat(samples: FloatArray, frames: Int) {
        check(!closed) { "Writer closed" }
        val expected = frames * channelCount
        require(samples.size >= expected) { "samples too short: ${samples.size} < $expected" }
        val bytes = ByteArray(frames * channelCount * 3)
        var bi = 0
        for (i in 0 until expected) {
            val clamped = max(-1f, min(1f, samples[i]))
            val int24 = (clamped * 8388607f).toInt()
            bytes[bi++] = (int24 and 0xFF).toByte()
            bytes[bi++] = ((int24 shr 8) and 0xFF).toByte()
            bytes[bi++] = ((int24 shr 16) and 0xFF).toByte()
        }
        raf.write(bytes)
        dataBytesWritten += bytes.size
    }

    override fun close() {
        if (closed) return
        closed = true
        finalizeHeader()
        raf.close()
    }

    val framesWritten: Long
        get() = if (channelCount == 0) 0 else dataBytesWritten / (channelCount * 3)

    private fun writePlaceholderHeader() {
        raf.setLength(0)
        raf.write(ByteArray(RIFF_HEADER_SIZE))
    }

    private fun finalizeHeader() {
        val blockAlign = channelCount * 3
        val byteRate = sampleRate * blockAlign
        val buf = ByteBuffer.allocate(RIFF_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt((36 + dataBytesWritten).toInt())
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(channelCount.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(24)
        buf.put("data".toByteArray())
        buf.putInt(dataBytesWritten.toInt())
        buf.flip()
        raf.seek(0)
        raf.write(buf.array())
    }

    companion object {
        private const val RIFF_HEADER_SIZE = 44
    }
}
