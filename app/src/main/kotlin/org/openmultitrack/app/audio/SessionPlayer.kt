package org.openmultitrack.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.domain.session.TransportStatus
import org.openmultitrack.sessionio.wav.WavReader
import android.hardware.usb.UsbDevice
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.PlaybackRoute
import java.io.File

class SessionPlayer {
    private var playbackJob: Job? = null
    private var activeBackend: org.openmultitrack.usb.AudioBackend? = null

    @Volatile
    var status: TransportStatus = TransportStatus()
        private set

    fun play(
        scope: CoroutineScope,
        file: File,
        route: PlaybackRoute,
        usbDevice: UsbDevice? = null,
        startFrame: Long = 0,
    ): Result<Unit> {
        stop()
        activeBackend = route.backend
        OmtLog.i(
            "Player",
            "play file=${file.absolutePath} backend=${route.backend} startFrame=$startFrame",
        )
        val reader = WavReader(file)
        val channels = reader.format.channelCount.coerceAtMost(32)
        val sampleRate = reader.format.sampleRate
        val duration = reader.format.frameCount
        OmtLog.i("Player", "wav ${channels}ch @ ${sampleRate}Hz duration=$duration frames")
        if (startFrame > 0) {
            reader.seekFrame(startFrame)
        }

        val engineStatus = AudioEngineRouter.startPlayback(route, usbDevice)
        if (!engineStatus.active) {
            OmtLog.e("Player", "native start failed: ${engineStatus.errorMessage}")
            reader.close()
            activeBackend = null
            return Result.failure(IllegalStateException(engineStatus.errorMessage ?: "Playback failed"))
        }
        OmtLog.i(
            "Player",
            "native running ${engineStatus.channelCount}ch @ ${engineStatus.sampleRate}Hz (${route.backend})",
        )

        status = TransportStatus(
            state = TransportState.PLAYING,
            positionFrames = startFrame,
            durationFrames = duration,
        )

        val scratch = FloatArray(2048 * channels)
        val backend = route.backend
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
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
                            val written = AudioEngineRouter.writePlaybackFrames(chunk, framesLeft, backend)
                            if (written <= 0) {
                                Thread.sleep(2)
                                continue
                            }
                            submitted += written
                            status = status.copy(positionFrames = status.positionFrames + written)
                        }
                    }
                }
            } catch (e: Exception) {
                OmtLog.e("Player", "playback loop failed", e)
                throw e
            } finally {
                val underruns = AudioEngineRouter.playbackUnderrunFrames(backend)
                AudioEngineRouter.stopPlayback()
                activeBackend = null
                OmtLog.i(
                    "Player",
                    "finished position=${status.positionFrames}/$duration underruns=$underruns",
                )
                status = status.copy(state = TransportState.IDLE, message = "Playback finished")
            }
        }
        return Result.success(Unit)
    }

    fun stop() {
        OmtLog.i("Player", "stop requested position=${status.positionFrames}")
        playbackJob?.cancel()
        playbackJob = null
        val backend = activeBackend
        if (backend != null) {
            val underruns = AudioEngineRouter.playbackUnderrunFrames(backend)
            OmtLog.i("Player", "stopped underruns=$underruns")
        }
        AudioEngineRouter.stopPlayback()
        activeBackend = null
        status = TransportStatus(state = TransportState.IDLE)
    }
}
