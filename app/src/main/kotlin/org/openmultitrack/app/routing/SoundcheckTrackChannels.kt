package org.openmultitrack.app.routing

import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

object SoundcheckTrackChannels {
    private const val MIN_WAV_BYTES = 44L

    fun indicesWithTracks(sessionDir: File, metadata: SessionMetadata): Set<Int> =
        metadata.channels.mapNotNull { ch ->
            val file = File(sessionDir, ch.fileName)
            if (file.isFile && file.length() >= MIN_WAV_BYTES) ch.index else null
        }.toSet()
}
