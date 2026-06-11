package org.openmultitrack.app.service

import java.util.concurrent.ConcurrentHashMap

/** Stable notification ids for per-mixer recording notifications. */
object RecordingNotificationIds {
    const val PLAYBACK_ID = 1100
    private const val BASE = 1001
    private const val MAX = PLAYBACK_ID - 1
    private val assigned = ConcurrentHashMap<String, Int>()
    @Volatile
    private var next = BASE

    fun idFor(mixerId: String): Int = assigned.getOrPut(mixerId) {
        synchronized(this) {
            val id = next
            next = if (next >= MAX) BASE else next + 1
            id
        }
    }

    fun release(mixerId: String) {
        assigned.remove(mixerId)
    }

    fun cancelFor(manager: android.app.NotificationManager, mixerId: String) {
        assigned.remove(mixerId)?.let { manager.cancel(it) }
    }

    fun trackedMixerIds(): Set<String> = assigned.keys.toSet()

    fun allIds(): Collection<Int> = assigned.values.toList()
}
