package org.openmultitrack.mixer.behringer

/** One stored mixer snapshot slot with its user-visible name. */
data class MixerSnapshotOption(
    val slot: Int,
    val name: String,
)
