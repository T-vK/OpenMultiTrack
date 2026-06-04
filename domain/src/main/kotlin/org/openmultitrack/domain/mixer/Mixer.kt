package org.openmultitrack.domain.mixer

import kotlinx.coroutines.flow.Flow

/** Console-agnostic control plane (OSC). Audio is always UAC2 via [org.openmultitrack.audio.AudioEngine]. */
interface Mixer {
    val model: MixerModel
    val id: String

    suspend fun connect(config: MixerConnectionConfig): Result<Unit>

    suspend fun disconnect()

    fun feedback(): Flow<MixerFeedback>

    suspend fun applySnapshot(snapshot: MixerSnapshot): Result<SnapshotApplyResult>

    suspend fun captureSnapshot(name: String, mode: SnapshotMode): Result<MixerSnapshot>

    suspend fun sendOsc(path: String, args: List<OscArg>): Result<Unit>
}

enum class MixerModel {
    X32,
    XR18,
    GENERIC,
}

enum class SnapshotMode {
    RECORD,
    SOUNDCHECK,
}

data class MixerConnectionConfig(
    val host: String,
    val port: Int,
)

data class MixerSnapshot(
    val id: String,
    val name: String,
    val mode: SnapshotMode,
    val commands: List<OscCommand>,
    val verifyPaths: List<String>,
)

data class OscCommand(
    val path: String,
    val args: List<OscArg>,
)

sealed interface OscArg {
    data class IntArg(val value: Int) : OscArg
    data class FloatArg(val value: Float) : OscArg
    data class StringArg(val value: String) : OscArg
}

sealed interface MixerFeedback {
    data class OscMessage(val path: String, val args: List<OscArg>) : MixerFeedback
    data class ConnectionLost(val reason: String?) : MixerFeedback
}

enum class SnapshotApplyResult {
    APPLIED,
    PARTIAL,
    FAILED,
}
