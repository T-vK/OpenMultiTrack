package org.openmultitrack.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLogBuffer {
    private const val MAX_LINES = 2000
    private val lines = CopyOnWriteArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun append(level: String, tag: String, message: String) {
        val line = "${fmt.format(Date())} $level/$tag: $message"
        lines.add(line)
        while (lines.size > MAX_LINES) lines.removeAt(0)
    }

    fun allText(): String = lines.joinToString("\n")

    fun clear() {
        lines.clear()
    }
}
