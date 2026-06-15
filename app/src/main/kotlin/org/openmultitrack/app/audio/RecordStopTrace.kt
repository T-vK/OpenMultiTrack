package org.openmultitrack.app.audio

import android.os.SystemClock
import org.openmultitrack.audio.OmtLog

/**
 * Step-by-step timing for record stop (engine, writers, session controller).
 *
 * Filter while profiling slow stops:
 * ```
 * adb logcat -s RecordStop:I TransportTrace:I
 * ```
 */
class RecordStopTrace(private val label: String) {
    private val originMs = SystemClock.elapsedRealtime()
    private var lastMs = originMs
    private var step = 0

    fun mark(phase: String) {
        val now = SystemClock.elapsedRealtime()
        step++
        val total = now - originMs
        val delta = now - lastMs
        lastMs = now
        OmtLog.i(TAG, "[$label] #$step +${total}ms (Δ${delta}ms) $phase")
    }

    fun timed(phase: String, block: () -> Unit) {
        val t0 = SystemClock.elapsedRealtime()
        block()
        val elapsed = SystemClock.elapsedRealtime() - t0
        mark("$phase (${elapsed}ms)")
    }

    suspend fun timedSuspend(phase: String, block: suspend () -> Unit) {
        val t0 = SystemClock.elapsedRealtime()
        block()
        val elapsed = SystemClock.elapsedRealtime() - t0
        mark("$phase (${elapsed}ms)")
    }

    fun finish(note: String = "done") {
        val total = SystemClock.elapsedRealtime() - originMs
        OmtLog.i(TAG, "[$label] FINISH +${total}ms $note")
    }

    companion object {
        const val TAG = "RecordStop"
    }
}
