package org.openmultitrack.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLogBuffer {
    private const val MAX_LINES = 2000
    private const val LOG_DIR = "logs"
    private val lines = CopyOnWriteArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val sessionFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Volatile
    var revision: Int = 0
        private set

    fun lineCount(): Int = lines.size

    fun append(level: String, tag: String, message: String) {
        val line = "${fmt.format(Date())} $level/$tag: $message"
        lines.add(line)
        while (lines.size > MAX_LINES) lines.removeAt(0)
        revision++
    }

    fun appendThrowable(level: String, tag: String, throwable: Throwable, maxStackLines: Int = 80) {
        append(level, tag, "${throwable.javaClass.name}: ${throwable.message}")
        throwable.stackTraceToString()
            .lineSequence()
            .take(maxStackLines)
            .forEach { line -> append(level, tag, line) }
    }

    fun currentSessionText(): String = lines.joinToString("\n")

    fun clearCurrentSession() {
        lines.clear()
    }

    fun persistSession(context: Context): Boolean {
        val snapshot = currentSessionText()
        if (snapshot.isBlank()) return false
        val dir = logDir(context)
        dir.mkdirs()
        val stamp = sessionFmt.format(Date()).replace(':', '-')
        val file = File(dir, "session-$stamp.txt")
        file.writeText(snapshot)
        return true
    }

    fun persistedSessionsText(context: Context): String {
        val dir = logDir(context)
        if (!dir.isDirectory) return ""
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            ?.joinToString(separator = "\n\n──── previous session ────\n\n") { file ->
                val header = "Saved ${sessionFmt.format(Date(file.lastModified()))} (${file.name})"
                "$header\n${file.readText()}"
            }
            .orEmpty()
    }

    fun displayText(context: Context, includePersisted: Boolean): String {
        val current = currentSessionText()
        if (!includePersisted) {
            return current.ifBlank { "(empty)" }
        }
        val persisted = persistedSessionsText(context)
        return when {
            persisted.isBlank() && current.isBlank() -> "(empty)"
            persisted.isBlank() -> current
            current.isBlank() -> persisted
            else -> "$persisted\n\n──── current session ────\n\n$current"
        }
    }

    private fun logDir(context: Context): File = File(context.filesDir, LOG_DIR)
}
