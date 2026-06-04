package org.openmultitrack.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.domain.session.TransportStatus
import org.openmultitrack.sessionio.wav.WavReader
import java.io.File

class SessionPlayer {
    private var playbackJob: Job? = null

    @Volatile
    var status: TransportStatus = TransportStatus()
        private set

    fun play(
        scope: CoroutineScope,
        file: File,
        deviceId: Int,
        startFrame: Long = 0,
    ): Result<Unit> {
        stop()
        val reader = WavReader(file)
        val channels = reader.format.channelCount.coerceAtMost(32)
        val sampleRate = reader.format.sampleRate
        val duration = reader.format.frameCount
        if (startFrame > 0) {
            reader.seekFrame(startFrame)
        }

        val engineStatus = NativeAudioEngine.startPlayback(deviceId, channels, sampleRate)
        if (!engineStatus.active) {
            reader.close()
            return Result.failure(IllegalStateException(engineStatus.errorMessage ?: "Playback failed"))
        }

        status = TransportStatus(
            state = TransportState.PLAYING,
            positionFrames = startFrame,
            durationFrames = duration,
        )

        val scratch = FloatArray(2048 * channels)
        playbackJob = scope.launch(Dispatchers.IO) {
            reader.use { wav ->
                while (isActive) {
                    val frames = wav.readInterleavedFloat(scratch, 2048)
                    if (frames <= 0) break
                    var submitted = 0
                    while (submitted < frames && isActive) {
                        val framesLeft = frames - submitted
                        val sampleStart = submitted * channels
                        val sampleEnd = sampleStart + framesLeft * channels
                        val chunk = scratch.copyOfRange(sampleStart, sampleEnd)
                        val written = NativeAudioEngine.writePlaybackFrames(chunk, framesLeft)
                        if (written <= 0) {
                            Thread.sleep(2)
                            continue
                        }
                        submitted += written
                        status = status.copy(positionFrames = status.positionFrames + written)
                    }
                }
            }
            NativeAudioEngine.stopPlayback()
            status = status.copy(state = TransportState.IDLE, message = "Playback finished")
        }
        return Result.success(Unit)
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        NativeAudioEngine.stopPlayback()
        status = TransportStatus(state = TransportState.IDLE)
    }
}
