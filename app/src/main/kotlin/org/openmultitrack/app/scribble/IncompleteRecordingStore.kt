package org.openmultitrack.app.scribble

import android.content.Context
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

/** Detects session folders left incomplete after an unexpected app exit during recording. */
object IncompleteRecordingStore {
    fun hasIncompleteRecording(context: Context, settings: AppSettingsStore, mixerId: String): Boolean =
        findIncompleteSessions(context, settings, mixerId).isNotEmpty()

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
