package org.openmultitrack.app.data

import android.content.Context
import android.os.Environment
import java.io.File

data class StorageVolumeOption(
    val label: String,
    val path: String,
)

/** Resolves where recordings are written and which roots are scanned for playback libraries. */
class RecordingStorageResolver(
    private val context: Context,
    private val settings: AppSettingsStore,
) {
    fun appDefaultRecordingRoot(): File =
        File(context.getExternalFilesDir(null), "OpenMultiTrack")

    fun defaultRecordingRoot(): File {
        val custom = settings.storageRootPath?.let { File(it) }
        if (custom != null) {
            if (!custom.isDirectory) custom.mkdirs()
            return custom
        }
        return appDefaultRecordingRoot()
    }

    fun additionalLibraryRoots(): List<File> =
        settings.additionalLibraryRoots
            .map { File(it) }
            .filter { it.isDirectory }

    fun discoverRemovableRecordingRoots(): List<File> {
        if (!settings.autoScanRemovableMedia) return emptyList()
        val found = linkedSetOf<String>()
        fun consider(volume: File) {
            if (!volume.isDirectory || !volume.canRead()) return
            val name = volume.name
            if (name == "emulated" || name == "self") return
            val candidate = File(volume, "OpenMultiTrack/Recordings")
            if (candidate.isDirectory) found.add(candidate.absolutePath)
        }
        File("/storage").listFiles()?.forEach(::consider)
        File("/mnt/media_rw").listFiles()?.forEach(::consider)
        return found.map { File(it) }
    }

    /** All roots searched when listing completed sessions for playback. */
    fun allLibraryRoots(): List<File> {
        val roots = linkedSetOf<String>()
        fun addRoot(dir: File) {
            if (dir.isDirectory) roots.add(dir.absolutePath)
        }
        addRoot(defaultRecordingRoot())
        additionalLibraryRoots().forEach(::addRoot)
        discoverRemovableRecordingRoots().forEach(::addRoot)
        return roots.map { File(it) }
    }

    fun discoverVolumeOptions(): List<StorageVolumeOption> {
        val options = mutableListOf<StorageVolumeOption>()
        val appDefault = appDefaultRecordingRoot()
        options.add(StorageVolumeOption("App storage", appDefault.absolutePath))
        val primary = File(Environment.getExternalStorageDirectory(), "OpenMultiTrack")
        if (primary.absolutePath != appDefault.absolutePath) {
            options.add(StorageVolumeOption("Shared storage", primary.absolutePath))
        }
        File("/storage").listFiles()?.forEach { volume ->
            if (!volume.isDirectory || !volume.canRead()) return@forEach
            val name = volume.name
            if (name == "emulated" || name == "self") return@forEach
            val recordings = File(volume, "OpenMultiTrack/Recordings")
            val openMt = File(volume, "OpenMultiTrack")
            when {
                recordings.isDirectory ->
                    options.add(StorageVolumeOption("SD/USB · $name (Recordings)", recordings.absolutePath))
                openMt.isDirectory || openMt.mkdirs() ->
                    options.add(StorageVolumeOption("SD/USB · $name", openMt.absolutePath))
            }
        }
        File("/mnt/media_rw").listFiles()?.forEach { volume ->
            if (!volume.isDirectory || !volume.canRead()) return@forEach
            val recordings = File(volume, "OpenMultiTrack/Recordings")
            val openMt = File(volume, "OpenMultiTrack")
            when {
                recordings.isDirectory ->
                    options.add(
                        StorageVolumeOption("Media · ${volume.name} (Recordings)", recordings.absolutePath),
                    )
                openMt.isDirectory || openMt.mkdirs() ->
                    options.add(StorageVolumeOption("Media · ${volume.name}", openMt.absolutePath))
            }
        }
        settings.storageRootPath?.let { custom ->
            if (options.none { it.path == custom }) {
                options.add(StorageVolumeOption("Custom", custom))
            }
        }
        return options.distinctBy { it.path }
    }
}
