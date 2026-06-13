package org.openmultitrack.sessionio.wav

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.openmultitrack.domain.audio.AudioConstants
import kotlin.math.max
import kotlin.math.min

/**
 * Streaming PCM WAV writer (24-bit integer, interleaved).
 * Header is finalized on [close].
 */
class WavWriter private constructor(
    val outputFile: File,
    private val channelCount: Int,
    private val sampleRate: Int,
    private val bitsPerSample: Int,
    append: Boolean,
) : AutoCloseable {
    private val raf = RandomAccessFile(outputFile, "rw")
    private var dataBytesWritten: Long = 0
    private var closed = false
    private var pcmBytes = ByteArray(0)
    private var pendingPcm = ByteArray(0)
    private var pendingLen = 0

    constructor(
        file: File,
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int = 24,
    ) : this(file, channelCount, sampleRate, bitsPerSample, append = false)

    init {
        require(channelCount in AudioConstants.MIN_CHANNELS..AudioConstants.MAX_CHANNELS) {
            "channelCount out of range: $channelCount"
        }
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(bitsPerSample == 24 || bitsPerSample == 32) {
            "bitsPerSample must be 24 or 32: $bitsPerSample"
        }
        if (append) {
            val format = WavReader.parseHeader(outputFile)
            require(format.channelCount == channelCount) {
                "channelCount mismatch: file=${format.channelCount} expected=$channelCount"
            }
            require(format.sampleRate == sampleRate) {
                "sampleRate mismatch: file=${format.sampleRate} expected=$sampleRate"
            }
            dataBytesWritten = format.dataSize
            raf.seek(format.dataOffset + format.dataSize)
        } else {
            writePlaceholderHeader()
        }
    }

    fun writeInterleavedFloat(samples: FloatArray, frames: Int) {
        check(!closed) { "Writer closed" }
        val expected = frames * channelCount
        require(samples.size >= expected) { "samples too short: ${samples.size} < $expected" }
        val byteLen = expected * 3
        if (pcmBytes.size < byteLen) {
            pcmBytes = ByteArray(byteLen)
        }
        var bi = 0
        for (i in 0 until expected) {
            val clamped = max(-1f, min(1f, samples[i]))
            val int24 = (clamped * 8388607f).toInt()
            pcmBytes[bi++] = (int24 and 0xFF).toByte()
            pcmBytes[bi++] = ((int24 shr 8) and 0xFF).toByte()
            pcmBytes[bi++] = ((int24 shr 16) and 0xFF).toByte()
        }
        appendPcm(pcmBytes, byteLen)
        dataBytesWritten += byteLen
    }

    fun writePcm24(samples: ByteArray, frames: Int) {
        check(!closed) { "Writer closed" }
        val byteLen = frames * channelCount * 3
        require(samples.size >= byteLen) { "samples too short: ${samples.size} < $byteLen" }
        appendPcm(samples, byteLen)
        dataBytesWritten += byteLen
    }

    /** Appends packed interleaved PCM when it already matches this writer's sample width. */
    fun writeRawInterleavedPcm(samples: ByteArray, frames: Int, bytesPerFrame: Int) {
        check(!closed) { "Writer closed" }
        val bytesPerSample = bitsPerSample / 8
        require(bytesPerFrame == channelCount * bytesPerSample) {
            "bytesPerFrame mismatch: $bytesPerFrame expected ${channelCount * bytesPerSample}"
        }
        val byteLen = frames * bytesPerFrame
        require(samples.size >= byteLen) { "samples too short: ${samples.size} < $byteLen" }
        appendPcm(samples, byteLen)
        dataBytesWritten += byteLen
    }

    /** Converts packed interleaved USB PCM (24- or 32-bit subframes) into WAV PCM. */
    fun writePackedInterleavedPcmAs24(
        samples: ByteArray,
        frames: Int,
        srcBytesPerFrame: Int,
    ) {
        check(!closed) { "Writer closed" }
        if (bitsPerSample == 32 && srcBytesPerFrame == channelCount * 4) {
            writeRawInterleavedPcm(samples, frames, srcBytesPerFrame)
            return
        }
        val wavByteLen = PcmFormatConversion.wav24ByteLength(frames, channelCount)
        if (pcmBytes.size < wavByteLen) {
            pcmBytes = ByteArray(wavByteLen)
        }
        PcmFormatConversion.interleavedToWav24(
            samples,
            frames,
            channelCount,
            srcBytesPerFrame,
            pcmBytes,
        )
        appendPcm(pcmBytes, wavByteLen)
        dataBytesWritten += wavByteLen
    }

    private fun appendPcm(source: ByteArray, byteLen: Int) {
        if (byteLen <= 0) return
        if (pendingLen > 0) {
            flushPendingPcm()
        }
        if (byteLen >= DIRECT_WRITE_BYTES) {
            raf.write(source, 0, byteLen)
            return
        }
        if (pendingLen + byteLen > pendingPcm.size) {
            val grown = ByteArray(max(pendingPcm.size * 2, pendingLen + byteLen))
            System.arraycopy(pendingPcm, 0, grown, 0, pendingLen)
            pendingPcm = grown
        }
        System.arraycopy(source, 0, pendingPcm, pendingLen, byteLen)
        pendingLen += byteLen
        if (pendingLen >= FLUSH_THRESHOLD_BYTES) {
            flushPendingPcm()
        }
    }

    private fun flushPendingPcm() {
        if (pendingLen <= 0) return
        raf.write(pendingPcm, 0, pendingLen)
        pendingLen = 0
    }

    override fun close() {
        if (closed) return
        closed = true
        flushPendingPcm()
        finalizeHeader()
        raf.close()
    }

    val framesWritten: Long
        get() {
            val bytesPerFrame = channelCount * (bitsPerSample / 8)
            return if (bytesPerFrame == 0) 0 else dataBytesWritten / bytesPerFrame
        }

    private fun writePlaceholderHeader() {
        raf.setLength(0)
        dataBytesWritten = 0
        finalizeHeader()
    }

    private fun finalizeHeader() {
        val blockAlign = channelCount * (bitsPerSample / 8)
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
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray())
        buf.putInt(dataBytesWritten.toInt())
        buf.flip()
        raf.seek(0)
        raf.write(buf.array())
    }

    companion object {
        private const val RIFF_HEADER_SIZE = 44
        private const val FLUSH_THRESHOLD_BYTES = 262_144
        /** Chunks at least this large bypass the pending buffer (Flow 8 ≈ 327 KB @ 8192 frames). */
        private const val DIRECT_WRITE_BYTES = 65_536

        /** Reopen an existing WAV and append PCM after any data already on disk. */
        fun openForAppend(file: File, channelCount: Int, sampleRate: Int): WavWriter =
            WavWriter(file, channelCount, sampleRate, bitsPerSample = 24, append = true)
    }
}
