package org.openmultitrack.domain.session

enum class AppMode {
    MULTITRACK_RECORD,
    VIRTUAL_SOUNDCHECK,
    /** Stereo mix-down of all unmuted channels to USB outputs 1+2. */
    SIMPLE_PLAY,
}

val AppMode.displayLabel: String
    get() = when (this) {
        AppMode.MULTITRACK_RECORD -> "Recording Mode"
        AppMode.VIRTUAL_SOUNDCHECK -> "Virtual Soundcheck"
        AppMode.SIMPLE_PLAY -> "Simple Play Mode"
    }

/** Shorter toolbar label when horizontal space is tight. */
val AppMode.shortLabel: String
    get() = when (this) {
        AppMode.MULTITRACK_RECORD -> "Recording"
        AppMode.VIRTUAL_SOUNDCHECK -> "Soundcheck"
        AppMode.SIMPLE_PLAY -> "Simple Play"
    }

/** Minimal toolbar label before falling back to icon-only. */
val AppMode.abbrevLabel: String
    get() = when (this) {
        AppMode.MULTITRACK_RECORD -> "Rec…"
        AppMode.VIRTUAL_SOUNDCHECK -> "SC…"
        AppMode.SIMPLE_PLAY -> "Play…"
    }

val AppMode.isPlaybackMode: Boolean
    get() = this != AppMode.MULTITRACK_RECORD
