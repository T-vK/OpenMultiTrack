package org.openmultitrack.mixer.behringer

/** Optional log hook for XR18 routing timing (wired from app module on Android). */
object Xr18RoutingLog {
    var onInfo: ((String) -> Unit)? = null

    fun info(message: String) {
        onInfo?.invoke(message)
    }

    inline fun <T> step(label: String, block: () -> T): T {
        val t0 = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - t0) / 1_000_000
        info("$label ${ms}ms")
        return result
    }

    suspend inline fun <T> stepSuspend(label: String, block: suspend () -> T): T {
        val t0 = System.nanoTime()
        val result = block()
        val ms = (System.nanoTime() - t0) / 1_000_000
        info("$label ${ms}ms")
        return result
    }
}
