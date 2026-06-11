package org.openmultitrack.app.data

import org.openmultitrack.audio.OmtLog
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

/**
 * Copies session WAV tails from the on-device spill buffer back to primary/mirror roots
 * after removable media reconnects or free space returns.
 */
object RecordingSpillSync {
    fun syncAll(resolver: RecordingStorageResolver, settings: AppSettingsStore) {
        if (!settings.localSpillBufferEnabled) return
        val spillRoot = resolver.localSpillRoot()
        if (!spillRoot.isDirectory) return
        spillRoot.listFiles()?.forEach { mixerDir ->
            if (!mixerDir.isDirectory) return@forEach
            mixerDir.listFiles()?.forEach { spillSession ->
                if (!spillSession.isDirectory) return@forEach
                syncSession(spillSession, resolver, settings)
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
                    if (!targetDir.isDirectory) targetDir.mkdirs()
                    val targetWav = File(targetDir, spillWav.name)
                    copyIfSpillAhead(spillWav, targetWav)
                }
            }
        if (meta.incomplete) {
            SessionMetadata.read(spillSessionDir)?.writeTo(spillSessionDir)
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
