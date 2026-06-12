package org.openmultitrack.app.audio

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.audio.NativeAudioMonitor
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.session.RecordingSession
import org.openmultitrack.sessionio.session.SessionDirectory
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.app.data.RecordingWritePlan
import org.openmultitrack.app.ui.daw.RecordViewLayout
import org.openmultitrack.sessionio.wav.PerChannelWavWriter
import org.openmultitrack.sessionio.wav.SessionWaveformExtractor
import org.openmultitrack.sessionio.wav.WavWriter
import org.openmultitrack.usb.AudioBackend
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.CaptureRoute
import org.openmultitrack.usb.NativeAudioCaptureRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import java.util.concurrent.atomic.AtomicReference
import androidx.annotation.VisibleForTesting

/**
 * Single USB capture stream shared by recording, live monitor, and optional root virtual mic.
 */
class CaptureSessionEngine(
    private val ownerId: String,
) {
    data class VirtualMicConfig(
        val enabled: Boolean,
        val selectedChannels: Set<Int>,
        val stereo: Boolean,
        val loopbackDeviceId: Int,
    )

    data class RecordingConfig(
        val mixerId: String,
        val mixerFolderName: String,
        val storageRoot: File,
        val channelStrips: List<ChannelStripState>,
        val writePlan: RecordingWritePlan? = null,
        val customTitle: String? = null,
    )

    private sealed interface RecordingWriteRequest {
        data class Frames(
            val samples: FloatArray,
            val frameCount: Int,
            val channelCount: Int,
            val returnToPool: Boolean = true,
        ) : RecordingWriteRequest

        data class Silence(val frameCount: Int) : RecordingWriteRequest
    }

    private data class ActiveRecordingWriters(
        val resilient: ResilientSessionWriter?,
        val perChannel: PerChannelWavWriter?,
    )

    private val lifecycleMutex = Mutex()
    private var captureScope: CoroutineScope? = null
    private var fanoutJob: Job? = null
    private var recordingWriteJob: Job? = null
    private var recordingWriteChannel: Channel<RecordingWriteRequest>? = null
    private var activeRecordingWriters: ActiveRecordingWriters? = null
    private var activeRoute: CaptureRoute? = null
    private var activeBackend: AudioBackend? = null
    private var syntheticGenerator: SyntheticCaptureGenerator? = null
    @Volatile
    private var sessionWriter: ResilientSessionWriter? = null
    @Volatile
    private var perChannelWriter: PerChannelWavWriter? = null
    private var legacyWavWriter: WavWriter? = null
    private var sessionDir: File? = null
    val activeSessionDir: File?
        get() = sessionDir
    private var recordingConfig: RecordingConfig? = null
    private var channelCount: Int = 2
    private var sampleRate: Int = 48_000

    @Volatile
    private var framesWritten: Long = 0

    /** Frames received from USB/synthetic capture (timeline source of truth). */
    @Volatile
    private var framesCaptured: Long = 0

    @Volatile
    private var droppedFrames: Long = 0

    @Volatile
    private var lastFrameReceivedNs: Long = 0

    @Volatile
    private var usbDegraded: Boolean = false

    /** Wall-clock origin for the session timeline (persists across resume). */
    private var recordingStartedAtEpochMs: Long? = null

    private var lastMetadataPersistFrames: Long = 0

    private val monitorConfig = AtomicReference(MonitorMixConfig())
    private val virtualMicConfig = AtomicReference<VirtualMicConfig?>(null)
    private val monitorOutputDeviceId = AtomicReference(-1)
    private val monitorOutputRunning = AtomicBoolean(false)
    private var virtualMicOutputRunning = false
    private val waveformRings = Array<LiveWaveformRing?>(64) { null }
    private val pendingWaveformPeaks = FloatArray(64)
    private val meterHold = FloatArray(64)
    private val lastRawPeaks = FloatArray(64)
    private val meterLock = Any()
    private val vuMeteringEnabled = AtomicBoolean(false)
    private var waveformWindowSec = DEFAULT_WAVEFORM_WINDOW_SEC
    private var waveformPeaksPerSecond = DEFAULT_WAVEFORM_PEAKS_PER_SEC
    private var lastWaveformEmitNs = 0L
    private var lastMeterDecayNs = 0L

    val isCaptureActive: Boolean
        get() = fanoutJob?.isActive == true

    val activeChannelCount: Int
        get() = channelCount

    val isRecording: Boolean
        get() = sessionWriter != null || perChannelWriter != null || legacyWavWriter != null

    val isUsbDegraded: Boolean
        get() = usbDegraded

    /** True when the fanout loop has read USB frames recently (used to heal stale capture). */
    fun isReceivingAudio(withinMs: Long = 1_500): Boolean {
        val last = lastFrameReceivedNs
        if (last <= 0L) return false
        return System.nanoTime() - last <= withinMs * 1_000_000L
    }

    fun isNativeCaptureOwner(): Boolean {
        if (syntheticGenerator != null) return true
        val backend = activeBackend ?: return false
        return NativeAudioCaptureRegistry.isOwner(ownerId, backend)
    }

    fun isSyntheticCapture(): Boolean = syntheticGenerator != null

    fun recordingStartedAtEpochMs(): Long? = recordingStartedAtEpochMs

    fun recordElapsedSec(): Float {
        if (!isRecording) return 0f
        val frameSec = if (sampleRate > 0) framesCaptured.toFloat() / sampleRate else 0f
        if (usbDegraded) return frameSec
        val started = recordingStartedAtEpochMs ?: return frameSec
        return (System.currentTimeMillis() - started).coerceAtLeast(0) / 1000f
    }

    fun updateVuMetering(enabled: Boolean) {
        vuMeteringEnabled.set(enabled)
    }

    fun updateMonitor(config: MonitorMixConfig) {
        val prev = monitorConfig.getAndSet(config)
        if (!config.enabled) {
            stopMonitorOutput()
        } else if (
            !prev.enabled ||
            prev.outputDeviceId != config.outputDeviceId ||
            prev.channelMonitoring != config.channelMonitoring ||
            prev.soloChannel != config.soloChannel ||
            prev.gainLinear != config.gainLinear
        ) {
            stopMonitorOutput()
        }
    }

    fun updateVirtualMic(config: VirtualMicConfig?) {
        virtualMicConfig.set(config)
        if (config == null || !config.enabled) {
            if (virtualMicOutputRunning) {
                NativeAudioEngine.stopPlayback()
                virtualMicOutputRunning = false
            }
        }
    }

    fun setUsbDegraded(degraded: Boolean) {
        usbDegraded = degraded
    }

    fun setWaveformConfig(windowSec: Float, peaksPerSecond: Int = peaksPerSecondForWindow(windowSec)) {
        waveformWindowSec = windowSec.coerceIn(
            RecordViewLayout.MIN_HISTORY_SEC,
            RecordViewLayout.MAX_HISTORY_SEC,
        )
        waveformPeaksPerSecond = peaksPerSecond.coerceIn(10, 120)
        if (isCaptureActive) {
            allocateWaveformRings()
        }
    }

    suspend fun startCapture(
        scope: CoroutineScope,
        route: CaptureRoute,
        usbDevice: UsbDevice?,
    ): Result<Unit> = lifecycleMutex.withLock {
        if (isCaptureActive && activeRoute?.backend == route.backend && !usbDegraded) {
            if (isNativeCaptureOwner() && isReceivingAudio(500)) {
                return Result.success(Unit)
            }
            stopCaptureInternalLocked()
        }
        if (isRecording && (usbDegraded || isCaptureActive)) {
            return reconnectCaptureLocked(scope, route, usbDevice)
        }
        stopCaptureInternalLocked()
        activeBackend = route.backend
        activeRoute = route

        OmtLog.i("CaptureSession", "startCapture backend=${route.backend} ch=${route.channelCount}")
        val status = AudioEngineRouter.startRecording(route, ownerId, usbDevice)
        if (!status.active) {
            activeBackend = null
            activeRoute = null
            return Result.failure(IllegalStateException(status.errorMessage ?: "Capture failed"))
        }

        channelCount = status.channelCount
        sampleRate = status.sampleRate
        usbDegraded = false
        lastFrameReceivedNs = 0L
        clearWaveforms()
        allocateWaveformRings()
        val framesPerChunk = SessionRecorder.chunkFramesForChannels(channelCount)
        val primeScratch = FloatArray(framesPerChunk * channelCount)
        val primed = AudioEngineRouter.readRecordedFrames(primeScratch, framesPerChunk, route.backend)
        if (primed > 0) {
            lastFrameReceivedNs = System.nanoTime()
            synchronized(meterLock) {
                absorbInterleavedPeaks(meterHold, lastRawPeaks, primeScratch, primed, channelCount)
            }
        }
        OmtLog.i(
            "CaptureSession",
            "startCapture primed=$primed ch=$channelCount backend=${route.backend}",
        )
        startFanoutLoop(scope, route.backend)
        Result.success(Unit)
    }

    suspend fun startSyntheticCapture(
        scope: CoroutineScope,
        channelCount: Int,
        sampleRateHz: Int = 48_000,
        generator: SyntheticCaptureGenerator? = null,
    ): Result<Unit> = lifecycleMutex.withLock {
        if (isCaptureActive && syntheticGenerator != null) {
            return Result.success(Unit)
        }
        stopCaptureInternalLocked()
        val synth = generator ?: SyntheticCaptureGenerator(channelCount, sampleRateHz)
        this.channelCount = synth.channelCount.coerceAtLeast(1)
        sampleRate = sampleRateHz.coerceAtLeast(1)
        syntheticGenerator = synth
        usbDegraded = false
        lastFrameReceivedNs = 0L
        clearWaveforms()
        allocateWaveformRings()
        OmtLog.i("CaptureSession", "startSyntheticCapture ch=$channelCount rate=$sampleRate")
        startFanoutLoop(scope, backend = null)
        Result.success(Unit)
    }

    suspend fun startRecording(config: RecordingConfig): Result<RecordingSession> = lifecycleMutex.withLock {
        if (!isCaptureActive) {
            return Result.failure(IllegalStateException("Capture not running"))
        }
        if (isRecording) {
            return Result.success(currentRecordingSession())
        }

        val plan = config.writePlan
        val dir = plan?.primarySessionDir
            ?: SessionDirectory.createSessionDir(config.storageRoot, config.mixerFolderName)
        sessionDir = dir
        recordingConfig = config
        framesWritten = 0
        framesCaptured = 0
        resetLiveWaveformBuffers()
        recordingStartedAtEpochMs = System.currentTimeMillis()
        lastMetadataPersistFrames = 0

        if (plan != null) {
            sessionWriter = ResilientSessionWriter(
                primarySessionDir = plan.primarySessionDir,
                mirrorSessionDirs = plan.mirrorSessionDirs,
                spillSessionDir = plan.spillSessionDir,
                channelStrips = config.channelStrips,
                sampleRate = sampleRate,
                minFreeBytes = plan.minFreeBytes,
                primaryRoot = plan.primaryRoot,
            )
            perChannelWriter = null
        } else {
            perChannelWriter = PerChannelWavWriter(dir, config.channelStrips, sampleRate)
            sessionWriter = null
        }

        buildSessionMetadata(config, sampleRate, framesWritten).writeTo(dir)
        startRecordingWriteLoop()

        val armedCount = config.channelStrips.count { it.armed }
        Result.success(
            RecordingSession(
                filePath = dir.absolutePath,
                channelCount = armedCount,
                sampleRate = sampleRate,
                framesRecorded = 0,
            ),
        )
    }

    suspend fun resumeRecording(sessionDir: File): Result<RecordingSession> = lifecycleMutex.withLock {
        if (!isCaptureActive) {
            return Result.failure(IllegalStateException("Capture not running"))
        }
        if (isRecording) {
            return Result.success(currentRecordingSession())
        }
        val meta = SessionMetadata.read(sessionDir)
            ?: return Result.failure(IllegalStateException("No session metadata"))
        if (!meta.incomplete) {
            return Result.failure(IllegalStateException("Session already complete"))
        }

        val writer = PerChannelWavWriter.openForResume(sessionDir, meta)
        perChannelWriter = writer
        this.sessionDir = sessionDir
        framesWritten = writer.totalFramesWritten()
        framesCaptured = framesWritten
        recordingStartedAtEpochMs = meta.startedAtEpochMs
        lastMetadataPersistFrames = framesWritten
        sampleRate = meta.sampleRate
        recordingConfig = RecordingConfig(
            mixerId = meta.mixerId,
            mixerFolderName = meta.mixerFolderName,
            storageRoot = sessionDir.parentFile?.parentFile ?: sessionDir.parentFile ?: sessionDir,
            channelStrips = writer.channelStrips(),
            customTitle = meta.customTitle,
        )

        val preInterruptionFrames = framesWritten
        if (waveformRings[0] == null) {
            allocateWaveformRings()
        }
        hydrateWaveformsAfterResume(sessionDir, meta, preInterruptionFrames)
        startRecordingWriteLoop()
        catchUpTimelineToTarget(maxFramesPerPass = Int.MAX_VALUE / 2)
        maybePersistTimelineAsync()

        val armedCount = meta.channels.size
        Result.success(
            RecordingSession(
                filePath = sessionDir.absolutePath,
                channelCount = armedCount,
                sampleRate = sampleRate,
                framesRecorded = framesWritten,
            ),
        )
    }

    suspend fun pauseRecording(): RecordingSession? = lifecycleMutex.withLock {
        closeRecordingWritersLocked(markComplete = false)
    }

    suspend fun stopRecording(): RecordingSession? = lifecycleMutex.withLock {
        closeRecordingWritersLocked(markComplete = true)
    }

    private suspend fun closeRecordingWritersLocked(markComplete: Boolean): RecordingSession? {
        val dir = sessionDir
        val config = recordingConfig
        val rate = sampleRate
        val resilient = sessionWriter
        val writer = perChannelWriter
        val legacy = legacyWavWriter
        stopRecordingWriteLoop()
        val recordedFrames = when {
            resilient != null -> resilient.totalFramesWritten()
            writer != null -> writer.totalFramesWritten()
            else -> framesCaptured.coerceAtLeast(framesWritten)
        }
        sessionWriter = null
        resilient?.close()
        perChannelWriter = null
        writer?.close()
        legacyWavWriter = null
        legacy?.close()
        recordingConfig = null
        sessionDir = null
        recordingStartedAtEpochMs = null
        lastMetadataPersistFrames = 0
        framesWritten = 0
        framesCaptured = 0
        resetLiveWaveformBuffers()

        if (dir != null && config != null) {
            buildSessionMetadata(config, rate, recordedFrames).writeTo(dir)
            if (markComplete) {
                SessionMetadata.read(dir)?.markComplete(dir)
            } else {
                SessionMetadata.read(dir)?.copy(incomplete = true)?.writeTo(dir)
            }
        }

        if (!needsCapture()) {
            stopCaptureInternalLocked()
        }

        return dir?.let {
            RecordingSession(
                filePath = it.absolutePath,
                channelCount = channelCount,
                sampleRate = rate,
                framesRecorded = recordedFrames,
            )
        }
    }

    suspend fun stopCapture() = lifecycleMutex.withLock {
        stopRecordingWriteLoop()
        val resilient = sessionWriter
        sessionWriter = null
        resilient?.close()
        val writer = perChannelWriter
        perChannelWriter = null
        writer?.close()
        val legacy = legacyWavWriter
        legacyWavWriter = null
        legacy?.close()
        sessionDir = null
        recordingConfig = null
        recordingStartedAtEpochMs = null
        lastMetadataPersistFrames = 0
        stopCaptureInternalLocked()
    }

    fun droppedFrameCount(): Long = droppedFrames

    /** Live per-channel input levels for VU meters (independent of waveform history). */
    fun captureMeterLevels(): Map<Int, Float> = synchronized(meterLock) {
        val out = LinkedHashMap<Int, Float>(channelCount)
        for (ch in 0 until channelCount) {
            if (meterHold[ch] > METER_OUTPUT_THRESHOLD) {
                out[ch] = meterHold[ch]
            }
        }
        out
    }

    fun waveformSnapshots(normalize: Boolean): Map<Int, LiveWaveformSnapshot> {
        val out = LinkedHashMap<Int, LiveWaveformSnapshot>()
        for (ch in 0 until channelCount) {
            waveformRings[ch]?.let { ring ->
                out[ch] = ring.snapshot(normalize)
            }
        }
        return out
    }

    fun clearWaveforms() {
        resetLiveWaveformBuffers()
        synchronized(meterLock) {
            meterHold.fill(0f)
            lastRawPeaks.fill(0f)
        }
        lastFrameReceivedNs = 0L
        lastMeterDecayNs = 0L
    }

    /** Clears live waveform ring buffers without touching VU meter state. */
    fun resetLiveWaveformBuffers() {
        pendingWaveformPeaks.fill(0f)
        lastWaveformEmitNs = 0L
        if (channelCount > 0) {
            allocateWaveformRings()
        }
    }

    @VisibleForTesting
    internal fun debugRawMeterPeaks(): FloatArray = synchronized(meterLock) {
        lastRawPeaks.copyOf(channelCount)
    }

    private fun allocateWaveformRings() {
        for (ch in 0 until channelCount) {
            waveformRings[ch] = LiveWaveformRing.forWindow(
                sampleRate,
                waveformWindowSec,
                waveformPeaksPerSecond,
            )
        }
    }

    private fun needsCapture(): Boolean {
        val monitor = monitorConfig.get()
        val virtualMic = virtualMicConfig.get()
        return isRecording ||
            monitor.enabled ||
            (virtualMic?.enabled == true) ||
            vuMeteringEnabled.get()
    }

    private suspend fun reconnectCaptureLocked(
        scope: CoroutineScope,
        route: CaptureRoute,
        usbDevice: UsbDevice?,
    ): Result<Unit> {
        OmtLog.i(
            "CaptureSession",
            "reconnectCapture while recording=$isRecording degraded=$usbDegraded backend=${route.backend}",
        )
        fanoutJob?.cancelAndJoin()
        fanoutJob = null
        val oldBackend = activeBackend
        if (oldBackend != null) {
            AudioEngineRouter.stopRecording(oldBackend, ownerId)
        }
        activeBackend = route.backend
        activeRoute = route
        val status = AudioEngineRouter.startRecording(route, ownerId, usbDevice)
        if (!status.active) {
            activeBackend = oldBackend
            activeRoute = null
            usbDegraded = true
            return Result.failure(IllegalStateException(status.errorMessage ?: "USB reconnect failed"))
        }
        if (status.channelCount > 0 && channelCount > 0 && status.channelCount != channelCount) {
            OmtLog.w(
                "CaptureSession",
                "USB reconnect channel count changed $channelCount → ${status.channelCount}",
            )
        }
        if (status.channelCount > 0) {
            channelCount = status.channelCount
        }
        if (status.sampleRate > 0) {
            sampleRate = status.sampleRate
        }
        usbDegraded = false
        startFanoutLoop(scope, route.backend)
        return Result.success(Unit)
    }

    private suspend fun stopCaptureInternalLocked() {
        val backend = activeBackend
        fanoutJob?.cancelAndJoin()
        fanoutJob = null
        stopMonitorOutput()
        if (virtualMicOutputRunning) {
            NativeAudioEngine.stopPlayback()
            virtualMicOutputRunning = false
        }
        if (backend != null) {
            AudioEngineRouter.stopRecording(backend, ownerId)
        }
        activeBackend = null
        activeRoute = null
        syntheticGenerator = null
    }

    private fun currentRecordingSession(): RecordingSession {
        val dir = sessionDir ?: error("No session dir")
        return RecordingSession(
            filePath = dir.absolutePath,
            channelCount = channelCount,
            sampleRate = sampleRate,
            framesRecorded = framesWritten,
        )
    }

    private fun stopMonitorOutput() {
        if (monitorOutputRunning.compareAndSet(true, false)) {
            NativeAudioMonitor.stop()
        }
        monitorOutputDeviceId.set(-1)
    }

    private fun startFanoutLoop(scope: CoroutineScope, backend: AudioBackend?) {
        captureScope = scope
        val framesPerChunk = SessionRecorder.chunkFramesForChannels(channelCount)
        val scratch = FloatArray(framesPerChunk * channelCount)
        val monitorScratch = FloatArray(framesPerChunk * 2)
        val virtualMicScratch = FloatArray(framesPerChunk * 2)
        var consecutiveEmptyReads = 0
        val synthetic = syntheticGenerator

        fanoutJob = scope.launch(captureFanoutDispatcher) {
            try {
                while (isActive) {
                    try {
                        decayMeterHoldForElapsed()
                        val rawFrames = when {
                            synthetic != null -> synthetic.fill(scratch, framesPerChunk)
                            usbDegraded -> 0
                            backend != null -> AudioEngineRouter.readRecordedFrames(scratch, framesPerChunk, backend)
                            else -> 0
                        }
                        val frames = rawFrames.coerceIn(0, framesPerChunk)

                        if (frames <= 0) {
                            consecutiveEmptyReads++
                            if (isRecording && usbDegraded) {
                                val advanced = catchUpTimelineToTarget(maxFramesPerPass = 65_536)
                                if (advanced > 0) continue
                            }
                            Thread.sleep(2)
                            continue
                        }
                        consecutiveEmptyReads = 0
                        lastFrameReceivedNs = System.nanoTime()
                        framesCaptured += frames
                        if (backend != null) {
                            droppedFrames = AudioEngineRouter.recordingDroppedFrames(backend)
                        }

                        accumulateWaveformPeaks(scratch, frames, channelCount)
                        maybeEmitWaveformPeaks()

                        writeRecordingFrames(scratch, frames, channelCount)

                        val monitor = monitorConfig.get()
                        if (monitor.enabled && MonitorMixer.effectiveMonitorChannels(monitor).isNotEmpty()) {
                            ensureMonitorOutput(monitor.outputDeviceId)
                            val mixed = MonitorMixer.mixToStereo(
                                scratch,
                                frames,
                                channelCount,
                                monitor,
                                monitorScratch,
                            )
                            if (mixed > 0 && monitorOutputRunning.get()) {
                                NativeAudioMonitor.writeFrames(monitorScratch, mixed)
                            }
                        }

                        val virtualMic = virtualMicConfig.get()
                        if (virtualMic != null && virtualMic.enabled && virtualMic.selectedChannels.isNotEmpty()) {
                            ensureVirtualMicOutput(virtualMic)
                            val outCh = ChannelMixdown.outputChannelCount(
                                virtualMic.selectedChannels,
                                virtualMic.stereo,
                            )
                            val mixed = ChannelMixdown.mixToOutput(
                                scratch,
                                frames,
                                channelCount,
                                virtualMic.selectedChannels,
                                stereo = outCh == 2,
                                dest = virtualMicScratch,
                            )
                            if (mixed > 0 && virtualMicOutputRunning) {
                                NativeAudioEngine.writePlaybackFrames(virtualMicScratch, mixed)
                            }
                        }
                    } catch (e: Exception) {
                        OmtLog.e("CaptureSession", "fanout chunk failed", e)
                        Thread.sleep(10)
                    }
                }
            } catch (e: Exception) {
                OmtLog.e("CaptureSession", "fanout loop failed", e)
            } finally {
                OmtLog.i("CaptureSession", "fanout ended frames=$framesWritten")
            }
        }
    }

    private fun startRecordingWriteLoop() {
        val scope = captureScope ?: return
        if (recordingWriteJob?.isActive == true) return
        val writers = ActiveRecordingWriters(
            resilient = sessionWriter,
            perChannel = perChannelWriter,
        )
        if (writers.resilient == null && writers.perChannel == null) return
        activeRecordingWriters = writers
        val channel = Channel<RecordingWriteRequest>(Channel.UNLIMITED)
        recordingWriteChannel = channel
        recordingWriteJob = scope.launch(diskWriteDispatcher) {
            try {
                for (request in channel) {
                    val active = activeRecordingWriters ?: break
                    when (request) {
                        is RecordingWriteRequest.Frames -> {
                            val samples = request.samples
                            val frames = request.frameCount
                            val channels = request.channelCount
                            if (active.resilient != null) {
                                active.resilient.writeInterleavedMultiChannel(samples, frames, channels)
                            } else {
                                active.perChannel?.writeInterleavedMultiChannel(samples, frames, channels)
                            }
                            if (request.returnToPool) {
                                releaseWriteBuffer(samples)
                            }
                        }
                        is RecordingWriteRequest.Silence -> {
                            val frames = request.frameCount
                            if (active.resilient != null) {
                                active.resilient.writeSilence(frames)
                            } else {
                                active.perChannel?.writeSilence(frames)
                            }
                        }
                    }
                    maybePersistTimelineAsync()
                }
            } catch (_: IllegalStateException) {
                // writer closed during stop
            }
        }
    }

    private suspend fun stopRecordingWriteLoop() {
        recordingWriteChannel?.close()
        recordingWriteJob?.join()
        recordingWriteChannel = null
        recordingWriteJob = null
        activeRecordingWriters = null
    }

    private suspend fun writeRecordingFrames(scratch: FloatArray, frames: Int, channels: Int) {
        if (!isRecording) return
        val toWrite = if (usbDegraded) {
            val maxFrames = (sessionTargetFrames() - framesWritten).coerceAtLeast(0)
            minOf(frames.toLong(), maxFrames).toInt()
        } else {
            frames
        }
        if (toWrite <= 0) return
        val channel = recordingWriteChannel
        if (channel != null) {
            val sampleCount = toWrite * channels
            val copy = acquireWriteBuffer(sampleCount)
            System.arraycopy(scratch, 0, copy, 0, sampleCount)
            try {
                channel.send(RecordingWriteRequest.Frames(copy, toWrite, channels))
                framesWritten += toWrite
            } catch (_: Exception) {
                releaseWriteBuffer(copy)
            }
            return
        }
        writeRecordingFramesSync(scratch, toWrite, channels)
        framesWritten += toWrite
    }

    private fun writeRecordingFramesSync(scratch: FloatArray, toWrite: Int, channels: Int) {
        val writer = sessionWriter
        if (writer != null) {
            try {
                writer.writeInterleavedMultiChannel(scratch, toWrite, channels)
            } catch (_: IllegalStateException) {
            }
            return
        }
        val legacyWriter = perChannelWriter ?: return
        try {
            legacyWriter.writeInterleavedMultiChannel(scratch, toWrite, channels)
        } catch (_: IllegalStateException) {
        }
    }

    private suspend fun enqueueSilenceFrames(frames: Int) {
        if (frames <= 0) return
        val channel = recordingWriteChannel
        if (channel != null) {
            try {
                channel.send(RecordingWriteRequest.Silence(frames))
                framesWritten += frames
            } catch (_: Exception) {
            }
            return
        }
        val resilient = sessionWriter
        val legacyWriter = perChannelWriter
        try {
            if (resilient != null) {
                resilient.writeSilence(frames)
            } else {
                legacyWriter?.writeSilence(frames)
            }
            framesWritten += frames
        } catch (_: IllegalStateException) {
        }
    }

    private fun sessionTargetFrames(): Long {
        val started = recordingStartedAtEpochMs ?: return framesWritten
        val elapsedMs = (System.currentTimeMillis() - started).coerceAtLeast(0)
        return elapsedMs * sampleRate / 1_000L
    }

    /** Inserts silence so [framesWritten] matches wall clock when USB input is degraded. */
    private suspend fun catchUpTimelineToTarget(maxFramesPerPass: Int): Int {
        if (!usbDegraded) return 0
        val resilient = sessionWriter
        val legacyWriter = perChannelWriter
        if (resilient == null && legacyWriter == null) return 0
        if (!isRecording) return 0
        var written = 0
        try {
            var gap = sessionTargetFrames() - framesCaptured
            while (gap > 0 && written < maxFramesPerPass) {
                val chunk = minOf(gap, (maxFramesPerPass - written).toLong(), 8_192L).toInt()
                enqueueSilenceFrames(chunk)
                framesCaptured += chunk
                written += chunk
                gap -= chunk
            }
        } catch (_: IllegalStateException) {
            // writer closed during stop
        }
        if (written > 0) {
            emitSilenceWaveformPeaks(written)
        }
        return written
    }

    private fun hydrateWaveformsAfterResume(
        sessionDir: File,
        metadata: SessionMetadata,
        preInterruptionFrames: Long,
    ) {
        if (preInterruptionFrames <= 0L) return
        val rate = sampleRate.coerceAtLeast(1)
        val tailSec = min(waveformWindowSec, preInterruptionFrames.toFloat() / rate)
        if (tailSec <= 0f) return

        val capacity = waveformRings.firstOrNull { it != null }?.capacity ?: return
        val gapFrames = (sessionTargetFrames() - preInterruptionFrames).coerceAtLeast(0)
        val silenceSlots = (
            (gapFrames.toFloat() / rate) * waveformPeaksPerSecond
            ).toInt().coerceIn(0, capacity)
        val roomForAudio = (capacity - silenceSlots).coerceAtLeast(0)
        if (roomForAudio <= 0) return

        for (chMeta in metadata.channels) {
            val ring = waveformRings.getOrNull(chMeta.index) ?: continue
            val file = File(sessionDir, chMeta.fileName)
            if (!file.isFile) continue
            val peaks = SessionWaveformExtractor.extractTailPeaks(
                file = file,
                audioFrames = preInterruptionFrames,
                sampleRate = rate,
                tailDurationSec = tailSec,
                peaksPerSec = waveformPeaksPerSecond,
            )
            val trimmed = if (peaks.size > roomForAudio) {
                peaks.copyOfRange(peaks.size - roomForAudio, peaks.size)
            } else {
                peaks
            }
            ring.seedPeaks(trimmed)
        }
    }

    private fun emitSilenceWaveformPeaks(silenceFrames: Int) {
        if (silenceFrames <= 0 || !isRecording) return
        val rate = sampleRate.coerceAtLeast(1)
        val peakCount = maxOf(1, (silenceFrames.toFloat() / rate * waveformPeaksPerSecond).toInt())
        for (i in 0 until peakCount) {
            for (ch in 0 until channelCount) {
                waveformRings[ch]?.pushPeak(0f)
            }
        }
    }

    private fun buildSessionMetadata(
        config: RecordingConfig,
        rate: Int,
        timelineFrames: Long,
    ): SessionMetadata = SessionMetadata(
        mixerId = config.mixerId,
        mixerFolderName = config.mixerFolderName,
        customTitle = config.customTitle,
        sampleRate = rate,
        format = org.openmultitrack.domain.session.SessionFormat.PER_CHANNEL_WAV,
        startedAtEpochMs = recordingStartedAtEpochMs ?: System.currentTimeMillis(),
        timelineFramesWritten = timelineFrames,
        channels = config.channelStrips.filter { it.armed }.map { strip ->
            org.openmultitrack.sessionio.session.ChannelMetadata(
                index = strip.index,
                fileName = org.openmultitrack.sessionio.session.ChannelFileNaming.fileName(
                    strip.index,
                    strip.label,
                ),
                displayName = org.openmultitrack.sessionio.session.ChannelFileNaming.displayName(
                    strip.index,
                    strip.label,
                ),
                colorArgb = strip.colorArgb,
            )
        },
    )

    private fun maybePersistTimelineAsync() {
        val dir = sessionDir ?: return
        val config = recordingConfig ?: return
        val captured = framesCaptured
        if (captured - lastMetadataPersistFrames < sampleRate / 2) return
        lastMetadataPersistFrames = captured
        buildSessionMetadata(config, sampleRate, captured).writeTo(dir)
    }

    private fun acquireWriteBuffer(sampleCount: Int): FloatArray {
        val pooled = writeBufferPool.poll()
        return if (pooled != null && pooled.size >= sampleCount) {
            pooled
        } else {
            FloatArray(sampleCount)
        }
    }

    private fun releaseWriteBuffer(buffer: FloatArray) {
        writeBufferPool.offer(buffer)
    }

    private fun decayMeterHoldForElapsed() {
        val now = System.nanoTime()
        val prev = lastMeterDecayNs
        lastMeterDecayNs = now
        if (prev <= 0L) return
        val elapsedMs = (now - prev) / 1_000_000f
        if (elapsedMs < 8f) return
        val factor = kotlin.math.exp(-elapsedMs / METER_DECAY_TAU_MS)
        synchronized(meterLock) {
            for (ch in 0 until channelCount) {
                meterHold[ch] *= factor
            }
        }
    }

    private fun accumulateWaveformPeaks(scratch: FloatArray, frames: Int, channels: Int) {
        synchronized(meterLock) {
            for (ch in 0 until channels) {
                var peak = 0f
                for (frame in 0 until frames) {
                    val sample = scratch[frame * channels + ch]
                    peak = maxOf(peak, kotlin.math.abs(sample))
                }
                pendingWaveformPeaks[ch] = maxOf(pendingWaveformPeaks[ch], peak)
            }
            absorbInterleavedPeaks(meterHold, lastRawPeaks, scratch, frames, channels)
        }
    }

    private fun maybeEmitWaveformPeaks() {
        if (!isRecording) return
        val intervalNs = 1_000_000_000L / waveformPeaksPerSecond
        val now = System.nanoTime()
        if (lastWaveformEmitNs != 0L && now - lastWaveformEmitNs < intervalNs) return
        lastWaveformEmitNs = now
        for (ch in 0 until channelCount) {
            waveformRings[ch]?.pushPeak(pendingWaveformPeaks[ch])
            pendingWaveformPeaks[ch] = 0f
        }
    }

    private fun ensureMonitorOutput(deviceId: Int) {
        if (deviceId < 0) return
        val prevId = monitorOutputDeviceId.get()
        if (monitorOutputRunning.get() && prevId == deviceId) return
        stopMonitorOutput()
        val status = NativeAudioMonitor.start(deviceId, 2, sampleRate)
        if (status.active) {
            monitorOutputDeviceId.set(deviceId)
            monitorOutputRunning.set(true)
        } else {
            OmtLog.e("CaptureSession", "monitor failed: ${status.errorMessage}")
        }
    }

    companion object {
        const val DEFAULT_WAVEFORM_WINDOW_SEC = 15f
        const val DEFAULT_WAVEFORM_PEAKS_PER_SEC = 30
        private const val TARGET_WAVEFORM_CAPACITY = 900
        private const val METER_DECAY_TAU_MS = 250f
        private const val METER_OUTPUT_THRESHOLD = 1e-5f
        private const val WRITE_BUFFER_POOL_SIZE = 6

        private val captureFanoutDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "capture-fanout").apply {
                priority = Thread.MAX_PRIORITY
            }
        }.asCoroutineDispatcher()

        private val diskWriteDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "capture-disk-write")
        }.asCoroutineDispatcher()

        private val writeBufferPool = ArrayBlockingQueue<FloatArray>(WRITE_BUFFER_POOL_SIZE)

        /** Keep roughly constant peak count across window sizes so short windows stay sharp. */
        fun peaksPerSecondForWindow(windowSec: Float): Int = DEFAULT_WAVEFORM_PEAKS_PER_SEC
    }

    private fun ensureVirtualMicOutput(config: VirtualMicConfig) {
        if (virtualMicOutputRunning) return
        val outCh = maxOf(1, ChannelMixdown.outputChannelCount(config.selectedChannels, config.stereo))
        val status = NativeAudioEngine.startPlayback(config.loopbackDeviceId, outCh, sampleRate)
        if (status.active) {
            virtualMicOutputRunning = true
        } else {
            OmtLog.e("CaptureSession", "virtual mic failed: ${status.errorMessage}")
        }
    }
}
