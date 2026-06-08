package org.openmultitrack.sessionio.wav

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.SessionFormat
import org.openmultitrack.sessionio.session.ChannelMetadata
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

class SessionWaveformCacheTest {
    @Test
    fun roundTripsOverviewCache() {
        val dir = createTempDir()
        try {
            val meta = SessionMetadata(
                mixerId = "test",
                mixerFolderName = "test",
                customTitle = null,
                sampleRate = 48_000,
                format = SessionFormat.PER_CHANNEL_WAV,
                channels = listOf(ChannelMetadata(0, "channel01.wav", "#1", 0)),
            )
            meta.writeTo(dir)
            val overview = SessionWaveformOverview(
                peaksByChannel = mapOf(0 to floatArrayOf(0.1f, 0.5f)),
                peaksPerSec = 4f,
                durationSec = 2f,
            )
            SessionWaveformCache.save(dir, overview)
            val loaded = SessionWaveformCache.load(dir, meta)
            assertThat(loaded).isNotNull()
            assertThat(loaded!!.durationSec).isWithin(0.01f).of(2f)
            assertThat(loaded.peaksByChannel[0]).hasLength(2)
            assertThat(loaded.peaksByChannel[0]!![1]).isWithin(0.01f).of(0.5f)
        } finally {
            dir.deleteRecursively()
        }
    }
}
