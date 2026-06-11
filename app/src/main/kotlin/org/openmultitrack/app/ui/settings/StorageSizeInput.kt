package org.openmultitrack.app.ui.settings

/** Parses and formats human-readable storage sizes (e.g. "500 MB", "1.5 GB"). */
object StorageSizeInput {
    fun format(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes % (1024L * 1024 * 1024) == 0L ->
            "${bytes / (1024L * 1024 * 1024)} GB"
        bytes % (1024L * 1024) == 0L ->
            "${bytes / (1024L * 1024)} MB"
        else -> "$bytes B"
    }

    fun parse(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "0" || trimmed.equals("off", ignoreCase = true)) {
            return 0L
        }
        val match = Regex(
            """^(\d+(?:\.\d+)?)\s*(b|kb|mb|gb)?$""",
            RegexOption.IGNORE_CASE,
        ).find(trimmed.replace(" ", "")) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].lowercase().ifEmpty { "mb" }
        val multiplier = when (unit) {
            "b" -> 1L
            "kb" -> 1024L
            "mb" -> 1024L * 1024
            "gb" -> 1024L * 1024 * 1024
            else -> return null
        }
        return (amount * multiplier).toLong().coerceAtLeast(0L)
    }
}
