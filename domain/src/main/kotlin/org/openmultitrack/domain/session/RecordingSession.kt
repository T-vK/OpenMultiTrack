package org.openmultitrack.domain.session

data class RecordingSession(
    val filePath: String,
    val channelCount: Int,
    val sampleRate: Int,
    val framesRecorded: Long,
)

enum class TransportState {
    IDLE,
    RECORDING,
    PLAYING,
    PAUSED,
}

data class TransportStatus(
    val state: TransportState = TransportState.IDLE,
    val positionFrames: Long = 0,
    val durationFrames: Long = 0,
    val message: String? = null,
)
