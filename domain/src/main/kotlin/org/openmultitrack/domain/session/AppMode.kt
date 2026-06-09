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

val AppMode.isPlaybackMode: Boolean
    get() = this != AppMode.MULTITRACK_RECORD
