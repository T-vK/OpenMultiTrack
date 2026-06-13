package org.openmultitrack.app.data

import android.content.Context
import org.openmultitrack.sessionio.session.SessionDirectory
import java.io.File

/** Resolved recording destinations for one take (primary, mirrors, spill). */
data class RecordingWritePlan(
    val primarySessionDir: File,
    val mirrorSessionDirs: List<File>,
    val spillSessionDir: File?,
    val primaryRoot: File,
    val minFreeBytes: Long,
    /** Fast internal-storage staging file for native UAC2 PCM capture (8+ ch). */
    val liveCaptureStagingFile: File?,
) {
    companion object {
        fun create(
            resolver: RecordingStorageResolver,
            settings: AppSettingsStore,
            mixerFolderName: String,
        ): RecordingWritePlan {
            val primaryRoot = resolver.defaultRecordingRoot()
            resolver.ensureRecordingTree(primaryRoot, mixerFolderName)
            val primarySessionDir = SessionDirectory.createSessionDir(primaryRoot, mixerFolderName)
            val mirrors = resolver.redundantRecordingRoots()
                .filter { it.absolutePath != primaryRoot.absolutePath }
                .map { root ->
                    resolver.ensureRecordingTree(root, mixerFolderName)
                    File(root, "${mixerFolderName}/${primarySessionDir.name}").apply { mkdirs() }
                }
            val spill = if (settings.localSpillBufferEnabled) {
                val spillRoot = resolver.localSpillRoot()
                resolver.ensureRecordingTree(spillRoot, mixerFolderName)
                File(spillRoot, "${mixerFolderName}/${primarySessionDir.name}").apply { mkdirs() }
            } else {
                null
            }
            val stagingDir = File(
                resolver.localSpillRoot(),
                "$mixerFolderName/${primarySessionDir.name}",
            ).apply { mkdirs() }
            val liveCaptureStagingFile = File(stagingDir, ".capture_interleaved.raw")
            return RecordingWritePlan(
                primarySessionDir = primarySessionDir,
                mirrorSessionDirs = mirrors,
                spillSessionDir = spill,
                primaryRoot = primaryRoot,
                minFreeBytes = settings.minFreeStorageBytes,
                liveCaptureStagingFile = liveCaptureStagingFile,
            )
        }
    }
}
