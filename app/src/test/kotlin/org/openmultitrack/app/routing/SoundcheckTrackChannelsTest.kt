package org.openmultitrack.app.routing

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.SessionFormat
import org.openmultitrack.sessionio.session.ChannelMetadata
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

class SoundcheckTrackChannelsTest {
    @Test
    fun indicesWithTracks_onlyIncludesExistingWav() {
        val dir = File.createTempFile("omt", null).apply { delete(); mkdirs() }
        File(dir, "ch01.wav").writeBytes(ByteArray(100))
        File(dir, "ch02.wav").writeBytes(ByteArray(10))
        val meta = SessionMetadata(
            mixerId = "m",
            mixerFolderName = "f",
            customTitle = null,
            sampleRate = 48_000,
            format = SessionFormat.PER_CHANNEL_WAV,
            channels = listOf(
                ChannelMetadata(0, "ch01.wav", "Kick", 0),
                ChannelMetadata(1, "ch02.wav", "Snare", 0),
                ChannelMetadata(2, "ch03.wav", "Hat", 0),
            ),
        )
        val indices = SoundcheckTrackChannels.indicesWithTracks(dir, meta)
        assertThat(indices).containsExactly(0)
        dir.deleteRecursively()
    }
}
