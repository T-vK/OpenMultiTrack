package org.openmultitrack.sessionio.session

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object SessionDirectory {
    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
    private const val LOW_SPACE_THRESHOLD_BYTES = 50L * 1024 * 1024

    fun createSessionDir(storageRoot: File, mixerFolderName: String): File {
        val parent = ensureDirectory(File(storageRoot, mixerFolderName))
        val dir = ensureDirectory(File(parent, LocalDateTime.now().format(TIMESTAMP)))
        return dir
    }

    private fun ensureDirectory(dir: File): File {
        if (dir.isDirectory) return dir
        if (dir.mkdirs() || dir.isDirectory) return dir
        val freeMb = dir.usableSpace / 1_048_576
        val message = if (dir.usableSpace < LOW_SPACE_THRESHOLD_BYTES) {
            "Not enough free storage (${freeMb} MB free at ${dir.absolutePath}). " +
                "Free space or choose another storage location."
        } else {
            "Could not create session folder: ${dir.absolutePath}"
        }
        throw IllegalStateException(message)
    }
}
