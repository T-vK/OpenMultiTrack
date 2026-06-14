package org.openmultitrack.mixer.behringer

/** XR18 snapshot name helpers (OSC strings). */
object MixerSnapshotNames {
    /** Firmware uses "-" for unused snapshot slots; not a user label. */
    fun isPlaceholder(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed == "-" || trimmed == "—" || trimmed == "_") return true
        return false
    }

    fun normalize(name: String): String =
        if (isPlaceholder(name)) "" else name.trim()

    fun resolveFromReplies(slot: Int, replies: Map<String, List<Any>>): String {
        for (path in OscPath.snapSlotNameQueryPaths(slot)) {
            val raw = replies[path]?.firstOrNull() as? String
            val normalized = normalize(raw.orEmpty())
            if (normalized.isNotEmpty()) return normalized
        }
        val padded = slot.toString().padStart(2, '0')
        val flex = replies.entries.firstOrNull { (replyPath, _) ->
            replyPath.startsWith("/-snap/$padded/name")
        }?.value?.firstOrNull() as? String
        return normalize(flex.orEmpty())
    }
}
