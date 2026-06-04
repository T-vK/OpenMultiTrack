package org.openmultitrack.sessionio.wav

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavFormat(
    val channelCount: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val dataSize: Long,
) {
    val blockAlign: Int get() = channelCount * (bitsPerSample / 8)
    val frameCount: Long get() = if (blockAlign == 0) 0 else dataSize / blockAlign
}

/** Reads 24-bit PCM WAV files produced by [WavWriter]. */
class WavReader(private val file: File) : AutoCloseable {
    val format: WavFormat = parseHeader(file)
    private val raf = RandomAccessFile(file, "r")

    init {
        raf.seek(format.dataOffset)
    }

    fun seekFrame(frame: Long) {
        val byteOffset = format.dataOffset + frame * format.blockAlign
        raf.seek(byteOffset)
    }

    fun readInterleavedFloat(buffer: FloatArray, frames: Int): Int {
        val samplesToRead = frames * format.channelCount
        val bytesNeeded = frames * format.blockAlign
        val chunk = ByteArray(bytesNeeded)
        val read = raf.read(chunk)
        if (read <= 0) return 0
        val framesRead = read / format.blockAlign
        val samplesRead = framesRead * format.channelCount
        var bi = 0
        for (i in 0 until samplesRead) {
            if (bi + 2 >= read) break
            val b0 = chunk[bi].toInt() and 0xFF
            val b1 = chunk[bi + 1].toInt() and 0xFF
            val b2 = chunk[bi + 2].toInt() and 0xFF
            bi += 3
            var v = b0 or (b1 shl 8) or (b2 shl 16)
            if (v and 0x800000 != 0) v = v or -0x1000000
            buffer[i] = v / 8388608f
        }
        return framesRead
    }

    override fun close() {
        raf.close()
    }

    companion object {
        fun parseHeader(file: File): WavFormat {
            RandomAccessFile(file, "r").use { r ->
                val header = ByteArray(44)
                r.readFully(header)
                val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val riff = String(header, 0, 4)
                val wave = String(header, 8, 4)
                require(riff == "RIFF" && wave == "WAVE") { "Not a WAV file" }
                buf.position(20)
                val audioFormat = buf.short.toInt()
                val channels = buf.short.toInt()
                val sampleRate = buf.int
                buf.int // byteRate
                buf.short // align
                val bits = buf.short.toInt()
                require(audioFormat == 1) { "Only PCM supported, got $audioFormat" }
                require(bits == 24) { "Only 24-bit supported, got $bits" }
                val dataSize = r.length() - 44
                return WavFormat(
                    channelCount = channels,
                    sampleRate = sampleRate,
                    bitsPerSample = bits,
                    dataOffset = 44,
                    dataSize = dataSize,
                )
            }
        }
    }
}
