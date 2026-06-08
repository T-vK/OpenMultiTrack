package org.openmultitrack.sessionio.session

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object SessionDirectory {
    private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")

    fun createSessionDir(storageRoot: File, mixerFolderName: String): File {
        val parent = File(storageRoot, mixerFolderName).apply { mkdirs() }
        val dir = File(parent, LocalDateTime.now().format(TIMESTAMP))
        check(dir.mkdirs() || dir.isDirectory) { "Could not create session dir: ${dir.absolutePath}" }
        return dir
    }
}
