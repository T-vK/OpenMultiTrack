package org.openmultitrack.app.audio

import android.os.SystemClock
import org.openmultitrack.audio.OmtLog

/** Monotonic timestamps for play/stop latency diagnosis (logcat tag OpenMultiTrack/TransportTrace). */
class TransportTrace(private val label: String) {
    private val originMs = SystemClock.elapsedRealtime()

    fun mark(phase: String) {
        val elapsed = SystemClock.elapsedRealtime() - originMs
        OmtLog.i("TransportTrace", "$label +${elapsed}ms $phase")
    }

    companion object {
        fun instant(phase: String) {
            OmtLog.i("TransportTrace", phase)
        }
    }
}
