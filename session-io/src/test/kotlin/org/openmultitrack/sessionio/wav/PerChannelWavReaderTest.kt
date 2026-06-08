package org.openmultitrack.sessionio.wav

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.SessionFormat
import org.openmultitrack.sessionio.session.ChannelMetadata
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

class PerChannelWavReaderTest {
    @Test
    fun readsPerChannelFilesAsInterleavedStream() {
        val dir = createTempDir()
        try {
            val ch0 = File(dir, "channel01.wav")
            val ch1 = File(dir, "channel02.wav")
            WavWriter(ch0, 1, 48_000).use { w ->
                w.writeInterleavedFloat(floatArrayOf(0.5f, -0.5f), frames = 2)
            }
            WavWriter(ch1, 1, 48_000).use { w ->
                w.writeInterleavedFloat(floatArrayOf(0.25f, -0.25f), frames = 2)
            }
            val meta = SessionMetadata(
                mixerId = "test",
                mixerFolderName = "test",
                customTitle = null,
                sampleRate = 48_000,
                format = SessionFormat.PER_CHANNEL_WAV,
                channels = listOf(
                    ChannelMetadata(0, ch0.name, "#1", 0),
                    ChannelMetadata(1, ch1.name, "#2", 0),
                ),
            )
            PerChannelWavReader.open(dir, meta).use { reader ->
                assertThat(reader.channelCount).isEqualTo(2)
                assertThat(reader.frameCount).isEqualTo(2)
                val out = FloatArray(4)
                assertThat(reader.readInterleavedFloat(out, frames = 2)).isEqualTo(2)
                assertThat(out[0]).isWithin(0.01f).of(0.5f)
                assertThat(out[1]).isWithin(0.01f).of(0.25f)
                assertThat(out[2]).isWithin(0.01f).of(-0.5f)
                assertThat(out[3]).isWithin(0.01f).of(-0.25f)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
