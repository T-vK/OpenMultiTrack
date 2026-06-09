package org.openmultitrack.sessionio.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.SessionFormat
import org.openmultitrack.sessionio.wav.WavWriter
import java.io.File

class SessionPlaybackDurationTest {
    @Test
    fun durationUsesShortestChannelFile_notTimelineMetadata() {
        val dir = createTempDir()
        try {
            val ch0 = File(dir, "channel01.wav")
            val ch1 = File(dir, "channel02.wav")
            WavWriter(ch0, 1, 48_000).use { w ->
                w.writeInterleavedFloat(FloatArray(48_000) { 0.1f }, frames = 48_000)
            }
            WavWriter(ch1, 1, 48_000).use { w ->
                w.writeInterleavedFloat(FloatArray(24_000) { 0.1f }, frames = 24_000)
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
                timelineFramesWritten = 120_000,
            )
            val frames = SessionPlaybackDuration.durationFrames(dir, meta)
            assertThat(frames).isEqualTo(24_000L)
            assertThat(SessionPlaybackDuration.durationSec(dir, meta)).isWithin(0.01f).of(0.5f)
        } finally {
            dir.deleteRecursively()
        }
    }
}
