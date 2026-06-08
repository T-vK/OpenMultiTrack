package org.openmultitrack.mixer.behringer

/** Optional log hook for OSC discovery failures (wired from app module on Android). */
object OscDiscoveryLog {
    var onSendFailed: ((String, Exception) -> Unit)? = null

    internal fun sendFailed(context: String, error: Exception) {
        onSendFailed?.invoke(context, error)
    }
}
