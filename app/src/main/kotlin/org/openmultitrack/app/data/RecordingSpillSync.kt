package org.openmultitrack.app.data

import org.openmultitrack.audio.OmtLog
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

/**
 * Copies session WAV tails from the on-device spill buffer back to primary/mirror roots
 * after removable media reconnects or free space returns.
 */
object RecordingSpillSync {
    private const val MIN_SYNC_HEADROOM_BYTES = 8L * 1024 * 1024

    fun syncAll(resolver: RecordingStorageResolver, settings: AppSettingsStore) {
        if (!settings.localSpillBufferEnabled) return
        val spillRoot = resolver.localSpillRoot()
        if (!spillRoot.isDirectory) return
        val primaryRoot = resolver.defaultRecordingRoot()
        if (primaryRoot.usableSpace < MIN_SYNC_HEADROOM_BYTES) {
            OmtLog.w(
                "SpillSync",
                "Skipping spill sync — only ${primaryRoot.usableSpace / 1_048_576} MB free on " +
                    primaryRoot.absolutePath,
            )
            return
        }
        spillRoot.listFiles()?.forEach { mixerDir ->
            if (!mixerDir.isDirectory) return@forEach
            mixerDir.listFiles()?.forEach { spillSession ->
                if (!spillSession.isDirectory) return@forEach
                runCatching {
                    syncSession(spillSession, resolver, settings)
                }.onFailure { e ->
                    OmtLog.w("SpillSync", "Session sync failed ${spillSession.name}: ${e.message}")
                }
            }
        }
    }

    private fun syncSession(
        spillSessionDir: File,
        resolver: RecordingStorageResolver,
        settings: AppSettingsStore,
    ) {
        val meta = SessionMetadata.read(spillSessionDir) ?: return
        val primaryRoot = resolver.defaultRecordingRoot()
        val targets = buildList {
            add(File(primaryRoot, "${meta.mixerFolderName}/${spillSessionDir.name}"))
            settings.redundantRecordingRoots
                .map { File(it) }
                .filter { it.absolutePath != primaryRoot.absolutePath }
                .forEach { root ->
                    add(File(root, "${meta.mixerFolderName}/${spillSessionDir.name}"))
                }
        }
        spillSessionDir.listFiles { f -> f.extension.equals("wav", ignoreCase = true) }
            ?.forEach { spillWav ->
                targets.forEach { targetDir ->
                    if (!ensureDirectory(targetDir)) return@forEach
                    val targetWav = File(targetDir, spillWav.name)
                    copyIfSpillAhead(spillWav, targetWav)
                }
            }
        if (meta.incomplete) {
            targets.forEach { targetDir ->
                runCatching { meta.writeTo(targetDir) }
                    .onFailure { e ->
                        OmtLog.w(
                            "SpillSync",
                            "Failed writing metadata to ${targetDir.absolutePath}: ${e.message}",
                        )
                    }
            }
        }
    }

    private fun ensureDirectory(dir: File): Boolean {
        if (dir.isDirectory) return true
        return runCatching {
            if (dir.mkdirs() || dir.isDirectory) {
                true
            } else {
                OmtLog.w("SpillSync", "Could not create ${dir.absolutePath}")
                false
            }
        }.getOrElse { e ->
            OmtLog.w("SpillSync", "Could not create ${dir.absolutePath}: ${e.message}")
            false
        }
    }

    private fun copyIfSpillAhead(spill: File, target: File) {
        if (!spill.isFile) return
        val spillLen = spill.length()
        val targetLen = if (target.isFile) target.length() else 0L
        if (spillLen <= targetLen + 44) return
        runCatching {
            if (!target.isFile) {
                spill.copyTo(target, overwrite = false)
            } else {
                target.outputStream().use { out ->
                    spill.inputStream().use { input ->
                        input.skip(targetLen)
                        input.copyTo(out)
                    }
                }
            }
            OmtLog.i("SpillSync", "Synced ${spill.name} → ${target.absolutePath}")
        }.onFailure { e ->
            OmtLog.w("SpillSync", "Failed syncing ${spill.name}: ${e.message}")
        }
    }
}
