package org.openmultitrack.app.ui.daw

import kotlin.math.roundToInt

/** Formats seconds as M:SS or H:MM:SS for transport clocks. */
fun formatTransportTime(sec: Float): String {
    val total = sec.roundToInt().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return "%.1f GB free".format(gb)
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    if (mb >= 1.0) return "%.0f MB free".format(mb)
    val kb = bytes.toDouble() / 1024.0
    return "%.0f KB free".format(kb)
}

/**
 * Recording clock that widens field width as elapsed time grows:
 * M:SS (<10 min), MM:SS (<1 h), H:MM:SS (<10 h), HH:MM:SS (10+ h).
 */
fun formatAdaptiveTransportTime(sec: Float): String {
    val total = sec.roundToInt().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return when {
        total >= 36_000 -> "%02d:%02d:%02d".format(hours, minutes, seconds)
        total >= 3_600 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        total >= 600 -> "%02d:%02d".format(minutes, seconds)
        else -> "%d:%02d".format(minutes, seconds)
    }
}

fun formatRecordingStorageInfo(freeBytes: Long, estimateSec: Float): String =
    "${formatStorageBytes(freeBytes)}\n${formatRecordRemainingEstimate(estimateSec)}"

fun formatRecordRemainingEstimate(sec: Float): String {
    if (sec <= 0f) return "estimate unavailable"
    val total = sec.roundToInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return when {
        hours >= 2 -> "~${hours}h ${minutes}m left"
        hours == 1 -> "~1h ${minutes}m left"
        minutes >= 2 -> "~${minutes} min left"
        minutes == 1 -> "~1 min left"
        else -> "~${total}s left"
    }
}
