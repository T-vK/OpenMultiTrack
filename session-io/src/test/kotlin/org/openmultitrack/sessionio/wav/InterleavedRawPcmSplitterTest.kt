package org.openmultitrack.sessionio.wav

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.session.ChannelFileNaming
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InterleavedRawPcmSplitterTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun splitHonorsSourceFrameOffset() {
        val sessionDir = temp.newFolder("session")
        val raw = File(sessionDir, ".capture_interleaved.raw")
        val channels = 2
        val bpf = channels * 4
        val totalFrames = 8
        RandomAccessFile(raw, "rw").use { out ->
            for (frame in 0 until totalFrames) {
                for (ch in 0 until channels) {
                    val sample = (frame * 1000 + ch).shl(8)
                    val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sample).array()
                    out.write(bytes)
                }
            }
        }
        val strips = listOf(
            ChannelStripState(index = 0, label = "L", armed = true),
            ChannelStripState(index = 1, label = "R", armed = true),
        )
        InterleavedRawPcmSplitter.splitToPerChannel(
            rawFile = raw,
            sessionDir = sessionDir,
            channelStrips = strips,
            sourceChannelCount = channels,
            sampleRate = 48_000,
            bytesPerFrame = bpf,
            frameCount = 4,
            sourceFrameOffset = 2,
        )
        val left = File(sessionDir, "channel01 - L.wav")
        val right = File(sessionDir, "channel02 - R.wav")
        assertTrue(left.isFile && left.length() > 44)
        assertTrue(right.isFile && right.length() > 44)
    }

    @Test
    fun appendRangeToWritersSupportsIncrementalPump() {
        val sessionDir = temp.newFolder("session")
        val raw = File(sessionDir, ".capture_interleaved.raw")
        val channels = 2
        val bpf = channels * 4
        val totalFrames = 10
        RandomAccessFile(raw, "rw").use { out ->
            for (frame in 0 until totalFrames) {
                for (ch in 0 until channels) {
                    val sample = (frame * 1000 + ch).shl(8)
                    val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sample).array()
                    out.write(bytes)
                }
            }
        }
        val strips = listOf(
            ChannelStripState(index = 0, label = "L", armed = true),
            ChannelStripState(index = 1, label = "R", armed = true),
        )
        val writers = strips.associate { strip ->
            strip.index to WavWriter(
                File(sessionDir, ChannelFileNaming.fileName(strip.index, strip.label)),
                1,
                48_000,
            )
        }
        InterleavedRawPcmSplitter.appendRangeToWriters(
            rawFile = raw,
            writers = writers,
            sourceChannelCount = channels,
            bytesPerFrame = bpf,
            sourceFrameOffset = 0,
            frameCount = 4,
        )
        InterleavedRawPcmSplitter.appendRangeToWriters(
            rawFile = raw,
            writers = writers,
            sourceChannelCount = channels,
            bytesPerFrame = bpf,
            sourceFrameOffset = 4,
            frameCount = 6,
        )
        writers.values.forEach { it.close() }
        val left = File(sessionDir, "channel01 - L.wav")
        val right = File(sessionDir, "channel02 - R.wav")
        val expectedPcmBytes = totalFrames * 3L
        assertTrue(left.length() == 44 + expectedPcmBytes)
        assertTrue(right.length() == 44 + expectedPcmBytes)
    }
}
