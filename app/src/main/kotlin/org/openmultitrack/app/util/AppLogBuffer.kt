package org.openmultitrack.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object DevLogLevelMask {
    const val DEBUG = 1 shl 0
    const val INFO = 1 shl 1
    const val WARN = 1 shl 2
    const val ERROR = 1 shl 3
    const val ALL = DEBUG or INFO or WARN or ERROR

    fun maskFor(level: Char): Int = when (level.uppercaseChar()) {
        'D' -> DEBUG
        'I' -> INFO
        'W' -> WARN
        'E' -> ERROR
        else -> ALL
    }

    fun isEnabled(mask: Int, level: Char): Boolean = mask and maskFor(level) != 0
}

data class ParsedLogLine(
    val timestamp: String,
    val level: Char,
    val tag: String,
    val message: String,
)

sealed class LogDisplayEntry {
    data class Section(val text: String) : LogDisplayEntry()
    data class Line(val parsed: ParsedLogLine) : LogDisplayEntry()
}

object AppLogBuffer {
    private const val MAX_LINES = 2000
    private const val LOG_DIR = "logs"
    private const val AUTO_PERSIST_FILE = "auto-current.txt"
    private const val AUTO_FLUSH_MS = 2_000L
    private val lines = CopyOnWriteArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val sessionFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val timestampPrefix = Regex("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} ")
    private val logLinePattern = Regex(
        "^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3}) ([DIWE])/([^:]+): (.*)$",
    )

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

    fun parseLogLine(raw: String): ParsedLogLine? {
        val match = logLinePattern.matchEntire(raw) ?: return null
        return ParsedLogLine(
            timestamp = match.groupValues[1],
            level = match.groupValues[2][0],
            tag = match.groupValues[3],
            message = match.groupValues[4],
        )
    }

    fun formatPlainLine(
        parsed: ParsedLogLine,
        hideTimestamps: Boolean,
        coloredLevels: Boolean,
    ): String {
        val body = if (coloredLevels) {
            "${parsed.tag}: ${parsed.message}"
        } else {
            "${parsed.level}/${parsed.tag}: ${parsed.message}"
        }
        return if (hideTimestamps) body else "${parsed.timestamp} $body"
    }

    fun collectDisplayEntries(
        context: Context,
        includePersisted: Boolean,
        levelMask: Int = DevLogLevelMask.ALL,
    ): List<LogDisplayEntry> {
        val rawLines = rawDisplayLines(context, includePersisted)
        if (rawLines.isEmpty()) return emptyList()
        return rawLines.mapNotNull { raw ->
            when {
                raw.isBlank() -> null
                else -> {
                    val parsed = parseLogLine(raw)
                    if (parsed != null && DevLogLevelMask.isEnabled(levelMask, parsed.level)) {
                        LogDisplayEntry.Line(parsed)
                    } else if (parsed != null) {
                        null
                    } else {
                        LogDisplayEntry.Section(raw)
                    }
                }
            }
        }
    }

    fun displayPlainText(
        context: Context,
        includePersisted: Boolean,
        hideTimestamps: Boolean = false,
        coloredLevels: Boolean = false,
        levelMask: Int = DevLogLevelMask.ALL,
    ): String {
        val entries = collectDisplayEntries(context, includePersisted, levelMask)
        if (entries.isEmpty()) return "(empty)"
        return entries.joinToString("\n") { entry ->
            when (entry) {
                is LogDisplayEntry.Section -> entry.text
                is LogDisplayEntry.Line -> formatPlainLine(entry.parsed, hideTimestamps, coloredLevels)
            }
        }
    }

    fun currentSessionText(hideTimestamps: Boolean = false): String =
        formatLines(lines, hideTimestamps)

    fun clearCurrentSession(context: Context? = null) {
        lines.clear()
        revision++
        context?.let { ctx ->
            val dir = logDir(ctx)
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension == "txt") {
                        file.delete()
                    }
                }
            }
        }
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
    ): String = displayPlainText(
        context = context,
        includePersisted = includePersisted,
        hideTimestamps = hideTimestamps,
    )

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

    private fun rawDisplayLines(context: Context, includePersisted: Boolean): List<String> {
        val current = lines.toList()
        if (!includePersisted) return current
        val persisted = persistedSessionRawLines(context)
        return when {
            persisted.isEmpty() -> current
            current.isEmpty() -> persisted
            else -> persisted + listOf("", "──── current session ────", "") + current
        }
    }

    private fun persistedSessionRawLines(context: Context): List<String> {
        val dir = logDir(context)
        if (!dir.isDirectory) return emptyList()
        val files = dir.listFiles()
            ?.filter { file ->
                file.isFile && file.extension == "txt" && file.name != AUTO_PERSIST_FILE
            }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        if (files.isEmpty()) return emptyList()
        return files.flatMapIndexed { index, file ->
            val header = listOf(
                "Saved ${sessionFmt.format(Date(file.lastModified()))} (${file.name})",
            )
            val body = file.readLines()
            val separator = if (index == 0) emptyList() else listOf("", "──── previous session ────", "")
            separator + header + body
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
