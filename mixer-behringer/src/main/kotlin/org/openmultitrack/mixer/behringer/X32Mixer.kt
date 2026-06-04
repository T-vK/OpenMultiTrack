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

/** X32 OSC driver — UDP port 10023. Routing capture/apply completed in milestone 4. */
class X32Mixer(
  private val host: String,
  override val id: String = "x32-$host",
) : Mixer {
    override val model: MixerModel = MixerModel.X32

    companion object {
        const val DEFAULT_PORT = 10023
    }

    override suspend fun connect(config: MixerConnectionConfig): Result<Unit> {
        if (config.port != DEFAULT_PORT) {
            return Result.failure(IllegalArgumentException("X32 expects port $DEFAULT_PORT"))
        }
        // OSC socket wiring in milestone 4.
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
