package org.openmultitrack.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import org.openmultitrack.domain.channel.ChannelStripState

class ResilientSessionWriterTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun writesToSpillWhenPrimaryBelowMinFreeSpace() {
        val primaryRoot = temp.newFolder("primary")
        val sessionDir = temp.newFolder("session")
        val spillDir = temp.newFolder("spill")
        val strips = listOf(
            ChannelStripState(index = 0, label = "Ch1", armed = true),
        )
        val writer = ResilientSessionWriter(
            primarySessionDir = sessionDir,
            mirrorSessionDirs = emptyList(),
            spillSessionDir = spillDir,
            channelStrips = strips,
            sampleRate = 48_000,
            minFreeBytes = Long.MAX_VALUE / 2,
            primaryRoot = primaryRoot,
        )
        val samples = FloatArray(2) { 0.25f }
        writer.writeInterleavedMultiChannel(samples, frames = 1, sourceChannelCount = 1)
        assertFalse(writer.isPrimaryHealthy())
        val spillWav = spillDir.listFiles()?.firstOrNull { it.extension == "wav" }
        assertTrue(spillWav != null && spillWav.length() > 44)
        writer.close()
    }
}
