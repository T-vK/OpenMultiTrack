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
            ?.mapNotNull { dir -> sessionSummaryIfComplete(dir, mixerId) }
            ?.toList()
            ?: emptyList()
    }

    fun listCompletedSessionsFromRoots(
        storageRoots: List<File>,
        mixerFolderName: String,
        mixerId: String,
    ): List<SessionSummary> {
        val seen = linkedSetOf<String>()
        return storageRoots
            .flatMap { listCompletedSessions(it, mixerFolderName, mixerId) }
            .filter { seen.add(it.dir.absolutePath) }
            .sortedByDescending { it.metadata.startedAtEpochMs }
    }

    private fun sessionSummaryIfComplete(dir: File, mixerId: String): SessionSummary? {
        val meta = SessionMetadata.read(dir) ?: return null
        if (meta.incomplete || meta.mixerId != mixerId) return null
        return SessionSummary(
            dir = dir,
            metadata = meta,
            durationFrames = durationFrames(dir, meta),
        )
    }

    private fun durationFrames(dir: File, meta: SessionMetadata): Long =
        SessionPlaybackDuration.durationFrames(dir, meta)
}
