package org.openmultitrack.app.audio

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.domain.session.TransportStatus
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.wav.PerChannelWavReader
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.PlaybackRoute
import java.io.File
import kotlin.math.abs
import kotlin.math.max

class SessionPlayer {
    private var playbackJob: Job? = null
    private var activeBackend: org.openmultitrack.usb.AudioBackend? = null
    private var loopStartFrame: Long? = null
    private var loopEndFrame: Long? = null
    private var loopEnabled: Boolean = false
    private var seekReader: ((Long) -> Unit)? = null
    private var playbackEpoch: Int = 0
    private val readerLock = Any()
    private val meterLevels = FloatArray(32)
    private var meterChannelCount = 0

    @Volatile
    var status: TransportStatus = TransportStatus()
        private set

    val isPlaying: Boolean
        get() = playbackJob?.isActive == true && status.state == TransportState.PLAYING

    fun meterLevelsSnapshot(): Map<Int, Float> {
        val count = meterChannelCount
        if (count <= 0) return emptyMap()
        return buildMap(count) {
            for (ch in 0 until count) {
                put(ch, meterLevels[ch])
            }
        }
    }

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
        mixContext: PlaybackMixContext? = null,
    ): Result<Unit> {
        val reader = PerChannelWavReader.open(sessionDir, metadata)
        val inputChannels = reader.channelCount.coerceAtMost(32)
        val outputChannels = mixContext?.usbOutputCount?.coerceAtLeast(1) ?: inputChannels
        val scratchChannels = max(inputChannels, outputChannels)
        return startPlayback(
            scope = scope,
            route = route,
            usbDevice = usbDevice,
            startFrame = startFrame,
            channels = outputChannels,
            scratchChannels = scratchChannels,
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
                if (mixContext != null) {
                    readLoopMixed(wav, inputChannels, outputChannels, it, mixContext)
                } else {
                    readLoop(wav, inputChannels, it)
                }
            }
        }
    }

    suspend fun seekToFrame(frame: Long) = withContext(Dispatchers.IO) {
        if (!isPlaying) return@withContext
        val clamped = frame.coerceIn(0L, status.durationFrames.coerceAtLeast(0L))
        try {
            synchronized(readerLock) {
                seekReader?.invoke(clamped)
            }
            status = status.copy(positionFrames = clamped)
        } catch (e: Exception) {
            OmtLog.e("Player", "seek failed", e)
        }
    }

    suspend fun stopAndAwait() {
        playbackEpoch++
        val job = playbackJob
        playbackJob = null
        job?.cancel()
        stopNative()
        status = TransportStatus(state = TransportState.IDLE)
    }

    private fun startPlayback(
        scope: CoroutineScope,
        route: PlaybackRoute,
        usbDevice: UsbDevice?,
        startFrame: Long,
        channels: Int,
        scratchChannels: Int = channels,
        sampleRate: Int,
        duration: Long,
        label: String,
        loopStartFrame: Long?,
        loopEndFrame: Long?,
        loopEnabled: Boolean,
        seek: (Long) -> Unit,
        run: (scratch: FloatArray) -> Unit,
    ): Result<Unit> {
        val previous = playbackJob
        playbackEpoch++
        val epoch = playbackEpoch
        activeBackend = route.backend
        this.loopStartFrame = loopStartFrame
        this.loopEndFrame = loopEndFrame
        this.loopEnabled = loopEnabled && loopStartFrame != null && loopEndFrame != null
        seekReader = seek
        OmtLog.i("Player", "play $label backend=${route.backend} startFrame=$startFrame loop=$loopEnabled")

        val engineStatus = AudioEngineRouter.startPlayback(route, usbDevice)
        if (!engineStatus.active) {
            OmtLog.e("Player", "native start failed: ${engineStatus.errorMessage}")
            activeBackend = null
            seekReader = null
            return Result.failure(IllegalStateException(engineStatus.errorMessage ?: "Playback failed"))
        }

        status = TransportStatus(
            state = TransportState.PLAYING,
            positionFrames = startFrame,
            durationFrames = duration,
        )

        val scratch = FloatArray(2048 * scratchChannels.coerceAtLeast(1))
        val backend = route.backend
        previous?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                if (epoch != playbackEpoch) return@launch
                run(scratch)
            } catch (e: Exception) {
                OmtLog.e("Player", "playback loop failed", e)
            } finally {
                if (epoch == playbackEpoch) {
                    val underruns = AudioEngineRouter.playbackUnderrunFrames(backend)
                    OmtLog.i("Player", "finished position=${status.positionFrames}/$duration underruns=$underruns")
                    stopNative()
                    status = status.copy(state = TransportState.IDLE, message = "Playback finished")
                    playbackJob = null
                }
            }
        }
        return Result.success(Unit)
    }

    private fun readLoopMixed(
        wav: Any,
        inputChannels: Int,
        outputChannels: Int,
        scratch: FloatArray,
        mixContext: PlaybackMixContext,
    ) {
        val backend = activeBackend ?: return
        val outScratch = FloatArray(2048 * outputChannels)
        val chunkScratch = FloatArray(2048 * outputChannels)
        while (playbackJob?.isActive == true) {
            val frames = synchronized(readerLock) {
                when (wav) {
                    is WavReader -> wav.readInterleavedFloat(scratch, 2048)
                    is PerChannelWavReader -> wav.readInterleavedFloat(scratch, 2048)
                    else -> 0
                }
            }
            if (frames <= 0) break
            mixContext.mixInterleaved(scratch, frames, inputChannels, outScratch)
            var submitted = 0
            while (submitted < frames && playbackJob?.isActive == true) {
                maybeLoopRewind()
                val framesLeft = frames - submitted
                val sampleStart = submitted * outputChannels
                val sampleCount = framesLeft * outputChannels
                System.arraycopy(outScratch, sampleStart, chunkScratch, 0, sampleCount)
                val written = AudioEngineRouter.writePlaybackFrames(chunkScratch, framesLeft, backend)
                if (written <= 0) {
                    Thread.sleep(2)
                    continue
                }
                submitted += written
                updateMeterLevels(chunkScratch, written, outputChannels, frameOffset = 0)
                status = status.copy(positionFrames = status.positionFrames + written)
                maybeLoopRewind()
            }
        }
    }

    private fun readLoop(
        wav: Any,
        channels: Int,
        scratch: FloatArray,
    ) {
        val backend = activeBackend ?: return
        val chunkScratch = FloatArray(2048 * channels)
        while (playbackJob?.isActive == true) {
            val frames = synchronized(readerLock) {
                when (wav) {
                    is WavReader -> wav.readInterleavedFloat(scratch, 2048)
                    is PerChannelWavReader -> wav.readInterleavedFloat(scratch, 2048)
                    else -> 0
                }
            }
            if (frames <= 0) break
            var submitted = 0
            while (submitted < frames && playbackJob?.isActive == true) {
                maybeLoopRewind()
                val framesLeft = frames - submitted
                val sampleStart = submitted * channels
                val sampleCount = framesLeft * channels
                System.arraycopy(scratch, sampleStart, chunkScratch, 0, sampleCount)
                val written = AudioEngineRouter.writePlaybackFrames(chunkScratch, framesLeft, backend)
                if (written <= 0) {
                    Thread.sleep(2)
                    continue
                }
                submitted += written
                updateMeterLevels(chunkScratch, written, channels, frameOffset = 0)
                status = status.copy(positionFrames = status.positionFrames + written)
                maybeLoopRewind()
            }
        }
    }

    private fun updateMeterLevels(
        scratch: FloatArray,
        frames: Int,
        channels: Int,
        frameOffset: Int,
    ) {
        meterChannelCount = channels
        for (ch in 0 until channels) {
            var peak = 0f
            for (f in 0 until frames) {
                val sample = scratch[(frameOffset + f) * channels + ch]
                peak = max(peak, abs(sample))
            }
            val display = scalePlaybackMeterPeak(peak)
            meterLevels[ch] = max(display, meterLevels[ch] * 0.82f)
        }
    }

    private fun scalePlaybackMeterPeak(raw: Float): Float {
        if (raw <= 1e-6f) return 0f
        val gain = if (raw < 0.15f) 1f / raw else 1f
        return (raw * gain).coerceIn(0f, 1f)
    }

    private fun resetMeterLevels() {
        meterChannelCount = 0
        meterLevels.fill(0f)
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
        playbackEpoch++
        playbackJob?.cancel()
        playbackJob = null
        stopNative()
        status = TransportStatus(state = TransportState.IDLE)
    }

    private fun stopNative() {
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
        resetMeterLevels()
    }
}
