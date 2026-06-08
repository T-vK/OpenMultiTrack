package org.openmultitrack.mixer.behringer

/** Optional log hook for OSC discovery (wired from app module on Android). */
object OscDiscoveryLog {
    var onSendFailed: ((String, Exception) -> Unit)? = null
    var onDebug: ((String) -> Unit)? = null

    internal fun sendFailed(context: String, error: Exception) {
        onSendFailed?.invoke(context, error)
    }

    internal fun debug(message: String) {
        onDebug?.invoke(message)
    }
}
