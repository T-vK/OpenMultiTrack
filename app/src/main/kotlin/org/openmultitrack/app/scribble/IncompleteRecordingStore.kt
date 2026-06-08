package org.openmultitrack.app.scribble

import android.content.Context
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

/** Detects session folders left incomplete after an unexpected app exit during recording. */
object IncompleteRecordingStore {
    fun hasIncompleteRecording(context: Context, settings: AppSettingsStore, mixerId: String): Boolean =
        findIncompleteSessions(context, settings, mixerId).isNotEmpty()

    fun latestIncompleteSession(
        context: Context,
        settings: AppSettingsStore,
        mixerId: String,
    ): File? = findIncompleteSessions(context, settings, mixerId)
        .maxByOrNull { SessionMetadata.read(it)?.startedAtEpochMs ?: 0L }

    /**
     * Returns an incomplete session only when the app was actively recording it before exit.
     * Orphan incomplete folders (no persisted active recording) are ignored for auto-recovery.
     */
    fun recoverableSession(
        context: Context,
        settings: AppSettingsStore,
        mixerId: String,
    ): File? {
        val dirPath = settings.activeRecordingSessionDir ?: return null
        if (settings.activeRecordingMixerId != mixerId) return null
        val dir = File(dirPath)
        if (!dir.isDirectory) return null
        val meta = SessionMetadata.read(dir) ?: return null
        if (!meta.incomplete || meta.mixerId != mixerId) return null
        val root = settings.storageRootPath?.let { File(it) }
            ?: File(context.getExternalFilesDir(null), "OpenMultiTrack")
        if (!dir.absolutePath.startsWith(root.absolutePath)) return null
        return dir
    }

    fun findIncompleteSessions(
        context: Context,
        settings: AppSettingsStore,
        mixerId: String,
    ): List<File> {
        val root = settings.storageRootPath?.let { File(it) }
            ?: File(context.getExternalFilesDir(null), "OpenMultiTrack")
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .maxDepth(4)
            .filter { it.isDirectory && File(it, "session.json").isFile }
            .mapNotNull { dir ->
                val meta = SessionMetadata.read(dir) ?: return@mapNotNull null
                if (meta.incomplete && meta.mixerId == mixerId) dir else null
            }
            .toList()
    }
}
