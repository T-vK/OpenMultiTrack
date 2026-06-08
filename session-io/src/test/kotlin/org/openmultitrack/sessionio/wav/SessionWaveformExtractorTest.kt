package org.openmultitrack.sessionio.wav

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.SessionFormat
import org.openmultitrack.sessionio.session.ChannelMetadata
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

class SessionWaveformExtractorTest {
    @Test
    fun extractsLowResolutionPeaksPerChannel() {
        val dir = createTempDir()
        try {
            val ch0 = File(dir, "channel01.wav")
            val ch1 = File(dir, "channel02.wav")
            WavWriter(ch0, 1, 48_000).use { w ->
                w.writeInterleavedFloat(floatArrayOf(0.8f, 0.1f, 0.5f, 0.2f), frames = 4)
            }
            WavWriter(ch1, 1, 48_000).use { w ->
                w.writeInterleavedFloat(floatArrayOf(0.2f, 0.9f, 0.1f, 0.6f), frames = 4)
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
            val overview = SessionWaveformExtractor.extract(dir, meta, peaksPerSec = 2f)
            assertThat(overview.peaksByChannel).hasSize(2)
            assertThat(overview.peakCount).isAtLeast(1)
            assertThat(overview.peaksByChannel[0]!![0]).isWithin(0.05f).of(0.8f)
            assertThat(overview.peaksByChannel[1]!![0]).isWithin(0.05f).of(0.9f)
        } finally {
            dir.deleteRecursively()
        }
    }
}
