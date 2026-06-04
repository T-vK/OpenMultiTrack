package org.openmultitrack.mixer.behringer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.openmultitrack.domain.mixer.Mixer
import org.openmultitrack.domain.mixer.MixerConnectionConfig
import org.openmultitrack.domain.mixer.MixerFeedback
import org.openmultitrack.domain.mixer.MixerModel
import org.openmultitrack.domain.mixer.MixerSnapshot
import org.openmultitrack.domain.mixer.OscArg
import org.openmultitrack.domain.mixer.SnapshotApplyResult
import org.openmultitrack.domain.mixer.SnapshotMode

/** XR18 OSC driver — UDP port 10024. */
class Xr18Mixer(
  private val host: String,
  override val id: String = "xr18-$host",
) : Mixer {
    override val model: MixerModel = MixerModel.XR18

    companion object {
        const val DEFAULT_PORT = 10024
    }

    override suspend fun connect(config: MixerConnectionConfig): Result<Unit> {
        if (config.port != DEFAULT_PORT) {
            return Result.failure(IllegalArgumentException("XR18 expects port $DEFAULT_PORT"))
        }
        return Result.success(Unit)
    }

    override suspend fun disconnect() = Unit

    override fun feedback(): Flow<MixerFeedback> = emptyFlow()

    override suspend fun applySnapshot(snapshot: MixerSnapshot): Result<SnapshotApplyResult> =
        Result.failure(UnsupportedOperationException("OSC apply not implemented yet"))

    override suspend fun captureSnapshot(name: String, mode: SnapshotMode): Result<MixerSnapshot> =
        Result.failure(UnsupportedOperationException("OSC capture not implemented yet"))

    override suspend fun sendOsc(path: String, args: List<OscArg>): Result<Unit> =
        Result.failure(UnsupportedOperationException("OSC send not implemented yet"))
}
