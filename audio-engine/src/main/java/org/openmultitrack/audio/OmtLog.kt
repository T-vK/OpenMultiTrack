package org.openmultitrack.audio

import android.util.Log

/**
 * Debug-only logcat helper shared across OpenMultiTrack modules.
 * Tags follow the pattern `OpenMultiTrack/<subtag>`.
 */
object OmtLog {
    private const val ROOT = "OpenMultiTrack"

    fun d(subtag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag(subtag), message)
    }

    fun i(subtag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag(subtag), message)
    }

    fun w(subtag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (throwable != null) Log.w(tag(subtag), message, throwable) else Log.w(tag(subtag), message)
    }

    fun e(subtag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (throwable != null) Log.e(tag(subtag), message, throwable) else Log.e(tag(subtag), message)
    }

    private fun tag(subtag: String): String = "$ROOT/$subtag"
}
