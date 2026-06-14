package org.openmultitrack.mixer.behringer

/** X32/XR18 family OSC path helpers. */
object OscPath {
    /** X32-style path (not used on X-Air; prefer [channelConfigInsrc]). */
    fun channelInputSource(channel: Int): String =
        "/ch/${channel.toString().padStart(2, '0')}/in/src"

    fun channelConfigInsrc(channel: Int): String =
        "/ch/${channel.toString().padStart(2, '0')}/config/insrc"

    fun channelConfigRtnsrc(channel: Int): String =
        "/ch/${channel.toString().padStart(2, '0')}/config/rtnsrc"

    fun channelPreampRtnSw(channel: Int): String =
        "/ch/${channel.toString().padStart(2, '0')}/preamp/rtnsw"

    /** X-Air snapshot load (slots 1–64). */
    fun snapLoad(): String = "/-snap/load"

    /** X-Air snapshot name for slot 1–64 (`/-snap/01/name/01` … `/-snap/64/name/01`). */
    fun snapSlotName(slot: Int): String {
        require(slot in 1..64) { "snapshot slot must be 1..64, got $slot" }
        return "/-snap/${slot.toString().padStart(2, '0')}/name/01"
    }

    fun snapName(): String = "/-snap/name"

    fun snapIndex(): String = "/-snap/index"

    fun snapSlotNameQueryPaths(slot: Int): List<String> {
        require(slot in 1..64) { "snapshot slot must be 1..64, got $slot" }
        val oneBased = slot.toString().padStart(2, '0')
        return listOf(
            "/-snap/$oneBased/name/01",
            "/-snap/$oneBased/name",
        )
    }

    fun snapshotRecall(slot: Int): String = "/snap/recall/$slot"

    fun snapshotStore(slot: Int): String = "/snap/store/$slot"

    fun info(): String = "/info"

    fun xinfo(): String = "/xinfo"

    fun xremote(): String = "/xremote"
}
