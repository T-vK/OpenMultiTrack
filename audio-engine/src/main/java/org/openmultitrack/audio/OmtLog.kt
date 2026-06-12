package org.openmultitrack.audio

import android.util.Log

/**
 * Debug-only logcat helper shared across OpenMultiTrack modules.
 * Tags follow the pattern `OpenMultiTrack/<subtag>`.
 */
object OmtLog {
    private const val ROOT = "OpenMultiTrack"

    /** Optional in-app log sink (debug builds — wired from the app module). */
    @Volatile
    var lineListener: ((level: String, subtag: String, message: String) -> Unit)? = null

    fun d(subtag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag(subtag), message)
            emit("D", subtag, message)
        }
    }

    fun i(subtag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag(subtag), message)
            emit("I", subtag, message)
        }
    }

    fun w(subtag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (throwable != null) Log.w(tag(subtag), message, throwable) else Log.w(tag(subtag), message)
        emit("W", subtag, message)
        throwable?.let { emit("W", subtag, "${it.javaClass.simpleName}: ${it.message}") }
    }

    fun e(subtag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (throwable != null) Log.e(tag(subtag), message, throwable) else Log.e(tag(subtag), message)
        emit("E", subtag, message)
        throwable?.let { emit("E", subtag, "${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun tag(subtag: String): String = "$ROOT/$subtag"

    private fun emit(level: String, subtag: String, message: String) {
        lineListener?.invoke(level, subtag, message)
    }
}
