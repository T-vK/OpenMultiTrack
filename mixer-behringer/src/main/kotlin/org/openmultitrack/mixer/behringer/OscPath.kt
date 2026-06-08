package org.openmultitrack.mixer.behringer

/** X32/XR18 family OSC path helpers. */
object OscPath {
    fun channelInputSource(channel: Int): String =
        "/ch/${channel.toString().padStart(2, '0')}/in/src"

    fun snapshotRecall(slot: Int): String = "/snap/recall/$slot"

    fun snapshotStore(slot: Int): String = "/snap/store/$slot"

    fun info(): String = "/info"

    fun xinfo(): String = "/xinfo"

    fun xremote(): String = "/xremote"
}
