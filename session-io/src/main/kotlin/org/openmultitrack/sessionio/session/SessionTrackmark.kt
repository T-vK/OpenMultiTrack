package org.openmultitrack.sessionio.session

/** A chapter / trackmark stored in a session's companion `.cue` file. */
data class SessionTrackmark(
    val index: Int,
    val title: String,
    val startSec: Float,
)
