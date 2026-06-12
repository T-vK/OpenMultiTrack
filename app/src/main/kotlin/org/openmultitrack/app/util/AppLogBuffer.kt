package org.openmultitrack.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLogBuffer {
    private const val MAX_LINES = 2000
    private const val LOG_DIR = "logs"
    private const val AUTO_PERSIST_FILE = "auto-current.txt"
    private const val AUTO_FLUSH_MS = 2_000L
    private val lines = CopyOnWriteArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val sessionFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val timestampPrefix = Regex("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} ")

    @Volatile
    var revision: Int = 0
        private set

    @Volatile
    private var autoPersistEnabled = false

    private var appContext: Context? = null
    private var flushHandler: Handler? = null
    private val flushRunnable = Runnable {
        appContext?.let(::flushAutoPersist)
        if (autoPersistEnabled) scheduleAutoFlush()
    }

    fun lineCount(): Int = lines.size

    fun setAutoPersist(context: Context, enabled: Boolean) {
        autoPersistEnabled = enabled
        appContext = context.applicationContext
        if (enabled) {
            scheduleAutoFlush()
        } else {
            flushHandler?.removeCallbacks(flushRunnable)
        }
    }

    fun restoreFromAutoPersist(context: Context) {
        val file = autoPersistFile(context)
        if (!file.isFile) return
        val restored = file.readLines().filter { it.isNotBlank() }
        if (restored.isEmpty()) return
        if (lines.isNotEmpty()) return
        lines.addAll(restored)
        revision++
    }

    fun append(level: String, tag: String, message: String) {
        val line = "${fmt.format(Date())} $level/$tag: $message"
        lines.add(line)
        while (lines.size > MAX_LINES) lines.removeAt(0)
        revision++
        if (autoPersistEnabled) scheduleAutoFlush()
    }

    fun appendThrowable(level: String, tag: String, throwable: Throwable, maxStackLines: Int = 80) {
        append(level, tag, "${throwable.javaClass.name}: ${throwable.message}")
        throwable.stackTraceToString()
            .lineSequence()
            .take(maxStackLines)
            .forEach { line -> append(level, tag, line) }
    }

    fun currentSessionText(hideTimestamps: Boolean = false): String =
        formatLines(lines, hideTimestamps)

    fun clearCurrentSession(context: Context? = null) {
        lines.clear()
        revision++
        context?.let { autoPersistFile(it).delete() }
        if (autoPersistEnabled && context != null) scheduleAutoFlush()
    }

    fun persistSession(context: Context): Boolean {
        val snapshot = currentSessionText()
        if (snapshot.isBlank()) return false
        val dir = logDir(context)
        dir.mkdirs()
        val stamp = sessionFmt.format(Date()).replace(':', '-')
        val file = File(dir, "session-$stamp.txt")
        file.writeText(snapshot)
        flushAutoPersist(context)
        return true
    }

    fun persistedSessionsText(context: Context, hideTimestamps: Boolean = false): String {
        val dir = logDir(context)
        if (!dir.isDirectory) return ""
        return dir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.extension == "txt" &&
                    file.name != AUTO_PERSIST_FILE
            }
            ?.sortedByDescending { it.lastModified() }
            ?.joinToString(separator = "\n\n──── previous session ────\n\n") { file ->
                val header = "Saved ${sessionFmt.format(Date(file.lastModified()))} (${file.name})"
                val body = formatLines(file.readLines(), hideTimestamps)
                "$header\n$body"
            }
            .orEmpty()
    }

    fun displayText(
        context: Context,
        includePersisted: Boolean,
        hideTimestamps: Boolean = false,
    ): String {
        val current = currentSessionText(hideTimestamps)
        if (!includePersisted) {
            return current.ifBlank { "(empty)" }
        }
        val persisted = persistedSessionsText(context, hideTimestamps)
        return when {
            persisted.isBlank() && current.isBlank() -> "(empty)"
            persisted.isBlank() -> current
            current.isBlank() -> persisted
            else -> "$persisted\n\n──── current session ────\n\n$current"
        }
    }

    fun flushAutoPersist(context: Context) {
        if (!autoPersistEnabled) return
        val snapshot = currentSessionText()
        val dir = logDir(context)
        dir.mkdirs()
        val file = autoPersistFile(context)
        if (snapshot.isBlank()) {
            file.delete()
        } else {
            file.writeText(snapshot)
        }
    }

    private fun formatLines(source: List<String>, hideTimestamps: Boolean): String {
        if (!hideTimestamps) return source.joinToString("\n")
        return source.joinToString("\n") { line ->
            timestampPrefix.replaceFirst(line, "")
        }
    }

    private fun scheduleAutoFlush() {
        val handler = flushHandler ?: Handler(Looper.getMainLooper()).also { flushHandler = it }
        handler.removeCallbacks(flushRunnable)
        handler.postDelayed(flushRunnable, AUTO_FLUSH_MS)
    }

    private fun autoPersistFile(context: Context): File = File(logDir(context), AUTO_PERSIST_FILE)

    private fun logDir(context: Context): File = File(context.filesDir, LOG_DIR)
}
