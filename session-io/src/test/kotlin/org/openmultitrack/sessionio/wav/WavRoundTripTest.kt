package org.openmultitrack.sessionio.wav

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class WavRoundTripTest {
    @Test
    fun writerReader_roundTrip24BitFourChannels() {
        val file = File.createTempFile("omt", ".wav")
        try {
            WavWriter(file, channelCount = 4, sampleRate = 48000).use { writer ->
                val samples = FloatArray(4 * 2) { i -> if (i % 4 == 0) 0.5f else -0.25f }
                writer.writeInterleavedFloat(samples, frames = 2)
            }
            WavReader(file).use { reader ->
                assertThat(reader.format.channelCount).isEqualTo(4)
                val out = FloatArray(8)
                assertThat(reader.readInterleavedFloat(out, frames = 2)).isEqualTo(2)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun openForAppend_continuesExistingPcm() {
        val file = File.createTempFile("omt", ".wav")
        try {
            WavWriter(file, channelCount = 2, sampleRate = 48_000).use { writer ->
                writer.writeInterleavedFloat(floatArrayOf(0.5f, -0.5f), frames = 1)
            }
            WavWriter.openForAppend(file, channelCount = 2, sampleRate = 48_000).use { writer ->
                writer.writeInterleavedFloat(floatArrayOf(-0.25f, 0.25f), frames = 1)
            }
            WavReader(file).use { reader ->
                assertThat(reader.format.frameCount).isEqualTo(2)
                val out = FloatArray(4)
                assertThat(reader.readInterleavedFloat(out, frames = 2)).isEqualTo(2)
                assertThat(out[0]).isWithin(0.01f).of(0.5f)
                assertThat(out[2]).isWithin(0.01f).of(-0.25f)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun writerReader_roundTrip24Bit() {
        val file = File.createTempFile("omt", ".wav")
        try {
            WavWriter(file, channelCount = 2, sampleRate = 48000).use { writer ->
                val samples = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f)
                writer.writeInterleavedFloat(samples, frames = 2)
            }
            WavReader(file).use { reader ->
                assertThat(reader.format.channelCount).isEqualTo(2)
                assertThat(reader.format.sampleRate).isEqualTo(48000)
                val out = FloatArray(4)
                val frames = reader.readInterleavedFloat(out, frames = 2)
                assertThat(frames).isEqualTo(2)
                assertThat(out[0]).isWithin(0.01f).of(0.5f)
                assertThat(out[1]).isWithin(0.01f).of(-0.5f)
            }
        } finally {
            file.delete()
        }
    }
}
