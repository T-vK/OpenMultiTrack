package org.openmultitrack.audio

data class NativeEngineStatus(
    val active: Boolean,
    val channelCount: Int,
    val sampleRate: Int,
    val errorMessage: String?,
)
