package org.openmultitrack.app.audio

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmultitrack.audio.NativeEngineStatus
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
    private var nativePlaybackWarm: Boolean = false
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
        ) { scratch, onFramesSubmitted ->
            reader.use { wav ->
                if (startFrame > 0) wav.seekFrame(startFrame)
                readLoop(wav, channels, scratch, onFramesSubmitted)
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
        ) { scratch, onFramesSubmitted ->
            reader.use { wav ->
                if (startFrame > 0) wav.seekFrame(startFrame)
                if (mixContext != null) {
                    readLoopMixed(wav, inputChannels, outputChannels, scratch, mixContext, onFramesSubmitted)
                } else {
                    readLoop(wav, inputChannels, scratch, onFramesSubmitted)
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
        val trace = TransportTrace("stopAndAwait")
        playbackEpoch++
        val job = playbackJob
        playbackJob = null
        job?.cancel()
        trace.mark("read loop cancelled, stopping native engine")
        stopNative()
        status = TransportStatus(state = TransportState.IDLE)
        trace.mark("done")
    }

    /** Stops the read loop but keeps the native USB/Oboe playback engine open for fast resume. */
    suspend fun suspendAndAwait() {
        val trace = TransportTrace("suspendAndAwait")
        playbackEpoch++
        val job = playbackJob
        playbackJob = null
        job?.cancel()
        trace.mark("read loop cancelled, keeping native engine warm=$nativePlaybackWarm backend=$activeBackend")
        job?.join()
        val pos = status.positionFrames
        val dur = status.durationFrames
        status = TransportStatus(state = TransportState.IDLE, positionFrames = pos, durationFrames = dur)
        trace.mark("done warm=$nativePlaybackWarm")
    }

    fun suspendPlayback() {
        val trace = TransportTrace("suspendPlayback")
        playbackEpoch++
        val job = playbackJob
        playbackJob = null
        job?.cancel()
        trace.mark("read loop cancel requested, keeping native engine warm=$nativePlaybackWarm backend=$activeBackend")
        val pos = status.positionFrames
        val dur = status.durationFrames
        status = TransportStatus(state = TransportState.IDLE, positionFrames = pos, durationFrames = dur)
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
        run: (scratch: FloatArray, onFramesSubmitted: (Int) -> Unit) -> Unit,
    ): Result<Unit> {
        val trace = TransportTrace("startPlayback")
        val previous = playbackJob
        playbackEpoch++
        val epoch = playbackEpoch
        val backend = route.backend
        this.loopStartFrame = loopStartFrame
        this.loopEndFrame = loopEndFrame
        this.loopEnabled = loopEnabled && loopStartFrame != null && loopEndFrame != null
        seekReader = seek
        trace.mark("preparing backend=$backend warm=$nativePlaybackWarm activeBackend=$activeBackend label=$label")

        // UAC2 (FLOW 8) must cold-start after any stop — warm reuse leaves a dead libusb engine.
        val canReuseWarm = nativePlaybackWarm &&
            activeBackend == backend &&
            backend == org.openmultitrack.usb.AudioBackend.OBOE
        val engineStatus = if (canReuseWarm) {
            trace.mark("reusing warm native engine backend=$backend")
            NativeEngineStatus(
                active = true,
                channelCount = route.channelCount,
                sampleRate = route.sampleRate,
                errorMessage = null,
            )
        } else {
            trace.mark("starting native engine backend=$backend")
            val started = AudioEngineRouter.startPlayback(route, usbDevice)
            trace.mark("native engine start returned active=${started.active}")
            started
        }
        if (!engineStatus.active) {
            OmtLog.e("Player", "native start failed: ${engineStatus.errorMessage}")
            activeBackend = null
            nativePlaybackWarm = false
            seekReader = null
            return Result.failure(IllegalStateException(engineStatus.errorMessage ?: "Playback failed"))
        }
        activeBackend = backend
        nativePlaybackWarm = true

        status = TransportStatus(
            state = TransportState.PLAYING,
            positionFrames = startFrame,
            durationFrames = duration,
        )
        trace.mark("status=PLAYING, launching read loop")

        val scratch = FloatArray(2048 * scratchChannels.coerceAtLeast(1))
        previous?.cancel()
        var loggedFirstWrite = false
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                if (epoch != playbackEpoch) return@launch
                run(scratch) { framesWritten ->
                    if (!loggedFirstWrite && framesWritten > 0) {
                        loggedFirstWrite = true
                        trace.mark("first audio frames written to native engine ($framesWritten frames)")
                    }
                }
            } catch (e: Exception) {
                OmtLog.e("Player", "playback loop failed", e)
                stopNative()
            } finally {
                if (epoch == playbackEpoch) {
                    val underruns = AudioEngineRouter.playbackUnderrunFrames(backend)
                    trace.mark(
                        "read loop ended position=${status.positionFrames}/$duration underruns=$underruns " +
                            "warm=$nativePlaybackWarm",
                    )
                    status = status.copy(state = TransportState.IDLE, message = "Playback finished")
                    playbackJob = null
                }
            }
        }
        trace.mark("read loop job launched")
        return Result.success(Unit)
    }

    private fun readLoopMixed(
        wav: Any,
        inputChannels: Int,
        outputChannels: Int,
        scratch: FloatArray,
        mixContext: PlaybackMixContext,
        onFramesSubmitted: (Int) -> Unit,
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
                onFramesSubmitted(written)
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
        onFramesSubmitted: (Int) -> Unit,
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
                onFramesSubmitted(written)
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
        val trace = TransportTrace("hardStop")
        playbackEpoch++
        playbackJob?.cancel()
        playbackJob = null
        trace.mark("stopping native engine")
        stopNative()
        status = TransportStatus(state = TransportState.IDLE)
        trace.mark("done")
    }

    private fun stopNative() {
        val backend = activeBackend
        if (backend != null) {
            val underruns = AudioEngineRouter.playbackUnderrunFrames(backend)
            OmtLog.i("Player", "stopped underruns=$underruns backend=$backend")
        }
        AudioEngineRouter.stopPlayback()
        activeBackend = null
        nativePlaybackWarm = false
        seekReader = null
        loopStartFrame = null
        loopEndFrame = null
        loopEnabled = false
        resetMeterLevels()
    }
}
