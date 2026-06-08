package org.openmultitrack.app.audio

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.domain.session.TransportStatus
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.wav.PerChannelWavReader
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.PlaybackRoute
import java.io.File

class SessionPlayer {
    private var playbackJob: Job? = null
    private var activeBackend: org.openmultitrack.usb.AudioBackend? = null
    private var loopStartFrame: Long? = null
    private var loopEndFrame: Long? = null
    private var loopEnabled: Boolean = false
    private var seekReader: ((Long) -> Unit)? = null

    @Volatile
    var status: TransportStatus = TransportStatus()
        private set

    fun play(
        scope: CoroutineScope,
        file: File,
        route: PlaybackRoute,
        usbDevice: UsbDevice? = null,
        startFrame: Long = 0,
        loopStartFrame: Long? = null,
        loopEndFrame: Long? = null,
        loopEnabled: Boolean = false,
    ): Result<Unit> {
        val reader = WavReader(file)
        val channels = reader.format.channelCount.coerceAtMost(32)
        return startPlayback(
            scope = scope,
            route = route,
            usbDevice = usbDevice,
            startFrame = startFrame,
            channels = channels,
            sampleRate = reader.format.sampleRate,
            duration = reader.format.frameCount,
            label = file.absolutePath,
            loopStartFrame = loopStartFrame,
            loopEndFrame = loopEndFrame,
            loopEnabled = loopEnabled,
            seek = { frame -> reader.seekFrame(frame) },
        ) {
            reader.use { wav ->
                if (startFrame > 0) wav.seekFrame(startFrame)
                readLoop(wav, channels, it)
            }
        }
    }

    fun playSession(
        scope: CoroutineScope,
        sessionDir: File,
        metadata: SessionMetadata,
        route: PlaybackRoute,
        usbDevice: UsbDevice? = null,
        startFrame: Long = 0,
        loopStartFrame: Long? = null,
        loopEndFrame: Long? = null,
        loopEnabled: Boolean = false,
    ): Result<Unit> {
        val reader = PerChannelWavReader.open(sessionDir, metadata)
        val channels = reader.channelCount.coerceAtMost(32)
        return startPlayback(
            scope = scope,
            route = route,
            usbDevice = usbDevice,
            startFrame = startFrame,
            channels = channels,
            sampleRate = reader.sampleRate,
            duration = reader.frameCount,
            label = sessionDir.absolutePath,
            loopStartFrame = loopStartFrame,
            loopEndFrame = loopEndFrame,
            loopEnabled = loopEnabled,
            seek = { frame -> reader.seekFrame(frame) },
        ) {
            reader.use { wav ->
                if (startFrame > 0) wav.seekFrame(startFrame)
                readLoop(wav, channels, it)
            }
        }
    }

    private fun startPlayback(
        scope: CoroutineScope,
        route: PlaybackRoute,
        usbDevice: UsbDevice?,
        startFrame: Long,
        channels: Int,
        sampleRate: Int,
        duration: Long,
        label: String,
        loopStartFrame: Long?,
        loopEndFrame: Long?,
        loopEnabled: Boolean,
        seek: (Long) -> Unit,
        run: (scratch: FloatArray) -> Unit,
    ): Result<Unit> {
        stop()
        activeBackend = route.backend
        this.loopStartFrame = loopStartFrame
        this.loopEndFrame = loopEndFrame
        this.loopEnabled = loopEnabled && loopStartFrame != null && loopEndFrame != null
        seekReader = seek
        OmtLog.i("Player", "play $label backend=${route.backend} startFrame=$startFrame loop=$loopEnabled")
        OmtLog.i("Player", "session ${channels}ch @ ${sampleRate}Hz duration=$duration frames")

        val engineStatus = AudioEngineRouter.startPlayback(route, usbDevice)
        if (!engineStatus.active) {
            OmtLog.e("Player", "native start failed: ${engineStatus.errorMessage}")
            activeBackend = null
            return Result.failure(IllegalStateException(engineStatus.errorMessage ?: "Playback failed"))
        }

        status = TransportStatus(
            state = TransportState.PLAYING,
            positionFrames = startFrame,
            durationFrames = duration,
        )

        val scratch = FloatArray(2048 * channels)
        val backend = route.backend
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                run(scratch)
            } catch (e: Exception) {
                OmtLog.e("Player", "playback loop failed", e)
            } finally {
                val underruns = AudioEngineRouter.playbackUnderrunFrames(backend)
                AudioEngineRouter.stopPlayback()
                activeBackend = null
                seekReader = null
                OmtLog.i("Player", "finished position=${status.positionFrames}/$duration underruns=$underruns")
                status = status.copy(state = TransportState.IDLE, message = "Playback finished")
            }
        }
        return Result.success(Unit)
    }

    private fun readLoop(
        wav: Any,
        channels: Int,
        scratch: FloatArray,
    ) {
        val backend = activeBackend ?: return
        while (playbackJob?.isActive == true) {
            val frames = when (wav) {
                is WavReader -> wav.readInterleavedFloat(scratch, 2048)
                is PerChannelWavReader -> wav.readInterleavedFloat(scratch, 2048)
                else -> 0
            }
            if (frames <= 0) break
            var submitted = 0
            while (submitted < frames && playbackJob?.isActive == true) {
                maybeLoopRewind()
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
                maybeLoopRewind()
            }
        }
    }

    private fun maybeLoopRewind() {
        if (!loopEnabled) return
        val start = loopStartFrame ?: return
        val end = loopEndFrame ?: return
        if (end <= start) return
        if (status.positionFrames >= end) {
            seekReader?.invoke(start)
            status = status.copy(positionFrames = start)
        }
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
        seekReader = null
        loopStartFrame = null
        loopEndFrame = null
        loopEnabled = false
        status = TransportStatus(state = TransportState.IDLE)
    }
}
