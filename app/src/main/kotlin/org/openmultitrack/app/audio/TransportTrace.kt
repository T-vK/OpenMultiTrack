package org.openmultitrack.app.audio

import android.os.SystemClock
import org.openmultitrack.audio.OmtLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Monotonic step-by-step transport timing for logcat.
 *
 * Filter while testing:
 * ```
 * adb logcat -s TransportTrace:I Xr18Routing:I
 * ```
 *
 * Each line: `[label] #step +totalMs (ΔdeltaMs) phase`
 */
class TransportTrace internal constructor(private val label: String) {
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

    fun finish(note: String = "done") {
        val total = SystemClock.elapsedRealtime() - originMs
        OmtLog.i(TAG, "[$label] FINISH +${total}ms $note")
    }

    companion object {
        const val TAG = "TransportTrace"

        fun instant(phase: String) {
            OmtLog.i(TAG, phase)
        }
    }
}

/** Correlates multi-layer transport work (ViewModel → session → OSC) under one trace per mixer. */
object TransportTraceHub {
    private val traces = ConcurrentHashMap<String, TransportTrace>()
    @Volatile
    var activeMixerId: String? = null

    fun start(mixerId: String, label: String): TransportTrace {
        val trace = TransportTrace(label)
        traces[mixerId] = trace
        activeMixerId = mixerId
        trace.mark("BEGIN mixer=$mixerId")
        return trace
    }

    fun trace(mixerId: String): TransportTrace? = traces[mixerId]

    fun mark(mixerId: String, phase: String) {
        traces[mixerId]?.mark(phase) ?: TransportTrace.instant(phase)
    }

    fun markActive(phase: String) {
        val id = activeMixerId
        if (id != null) {
            mark(id, phase)
        } else {
            TransportTrace.instant(phase)
        }
    }

    fun finish(mixerId: String, note: String = "done") {
        traces.remove(mixerId)?.finish(note)
        if (activeMixerId == mixerId) {
            activeMixerId = null
        }
    }
}
