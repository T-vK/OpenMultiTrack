package org.openmultitrack.sessionio.wav

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class WavRoundTripTest {
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
