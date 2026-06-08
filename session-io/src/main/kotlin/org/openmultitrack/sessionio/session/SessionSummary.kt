package org.openmultitrack.sessionio.session

import java.io.File

/** A finished recording session available for virtual soundcheck playback. */
data class SessionSummary(
    val dir: File,
    val metadata: SessionMetadata,
    val durationFrames: Long,
) {
    val displayTitle: String
        get() = metadata.customTitle?.takeIf { it.isNotBlank() } ?: dir.name

    val durationSec: Float
        get() = if (metadata.sampleRate > 0) durationFrames.toFloat() / metadata.sampleRate else 0f

    val channelCount: Int
        get() = metadata.resolvedChannels(dir).size
}

object SessionLibrary {
    fun listCompletedSessions(
        storageRoot: File,
        mixerFolderName: String,
        mixerId: String,
    ): List<SessionSummary> {
        val parent = File(storageRoot, mixerFolderName)
        if (!parent.isDirectory) return emptyList()
        return parent.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && File(it, "session.json").isFile }
            ?.mapNotNull { dir ->
                val meta = SessionMetadata.read(dir) ?: return@mapNotNull null
                if (meta.incomplete || meta.mixerId != mixerId) return@mapNotNull null
                SessionSummary(
                    dir = dir,
                    metadata = meta,
                    durationFrames = durationFrames(dir, meta),
                )
            }
            ?.sortedByDescending { it.metadata.startedAtEpochMs }
            ?.toList()
            ?: emptyList()
    }

    private fun durationFrames(dir: File, meta: SessionMetadata): Long {
        if (meta.timelineFramesWritten > 0) return meta.timelineFramesWritten
        return meta.channels.minOfOrNull { ch ->
            org.openmultitrack.sessionio.wav.PerChannelWavWriter.framesOnDisk(File(dir, ch.fileName))
        } ?: 0L
    }
}
