package org.openmultitrack.sessionio.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class SessionMetadataParseTest {
    @Test
    fun parsesChannelsWithNegativeColorArgb() {
        val dir = createTempDir()
        try {
            val json = """
                {
                  "mixerId": "mixer-a",
                  "mixerFolderName": "flow8",
                  "customTitle": null,
                  "sampleRate": 48000,
                  "format": "PER_CHANNEL_WAV",
                  "startedAtEpochMs": 1710000000000,
                  "incomplete": false,
                  "timelineFramesWritten": 48000,
                  "channels": [
                    {"index":0,"fileName":"channel01 - Kick.wav","displayName":"#1 Kick","colorArgb":-16777216},
                    {"index":1,"fileName":"channel02.wav","displayName":"#2","colorArgb":-65536}
                  ]
                }
            """.trimIndent()
            File(dir, "session.json").writeText(json)
            val meta = SessionMetadata.read(dir)
            assertThat(meta).isNotNull()
            assertThat(meta!!.channels).hasSize(2)
            assertThat(meta.channels[0].colorArgb).isEqualTo(-16777216)
            assertThat(meta.channels[1].fileName).isEqualTo("channel02.wav")
        } finally {
            dir.deleteRecursively()
        }
    }
}
