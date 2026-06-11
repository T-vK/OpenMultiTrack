package org.openmultitrack.app.data

/** What happens when a multitrack recording stops. */
enum class PostRecordBehavior(val label: String) {
    AUTO_SOUNDCHECK("Open in Soundcheck"),
    AUTO_SIMPLE_PLAY("Open in Simple Play"),
    RENAME_ONLY("Name only"),
    FULL_PROMPT("Full prompt"),
    NOTHING("Nothing"),
    ;

    companion object {
        fun fromOrdinal(ordinal: Int): PostRecordBehavior =
            entries.getOrElse(ordinal) { FULL_PROMPT }
    }
}
