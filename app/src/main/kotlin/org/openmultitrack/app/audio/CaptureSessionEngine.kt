package org.openmultitrack.app.audio

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.audio.NativeAudioMonitor
import org.openmultitrack.audio.NativeUac2Engine
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
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.atomic.AtomicReference
import android.os.Process
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

        data class PcmFrames(
            val samples: ByteArray,
            val frameCount: Int,
            val channelCount: Int,
            val bytesPerFrame: Int,
            val returnToPool: Boolean = true,
        ) : RecordingWriteRequest

        data class Silence(val frameCount: Int) : RecordingWriteRequest

        data object Shutdown : RecordingWriteRequest
    }

    private data class ActiveRecordingWriters(
        val resilient: ResilientSessionWriter?,
        val perChannel: PerChannelWavWriter?,
    )

    private val lifecycleMutex = Mutex()
    private var captureScope: CoroutineScope? = null
    private var fanoutJob: Job? = null
    private var diskWriteExecutor: ExecutorService? = null
    private var diskWriteFuture: Future<*>? = null
    private val diskWriteQueue = LinkedBlockingQueue<RecordingWriteRequest>(DISK_WRITE_QUEUE_CAPACITY)
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
    private var captureBytesPerFrame: Int = 8

    @Volatile
    private var nativePcmRecordingActive: Boolean = false

    private var nativeFramesBaselineAtRecordingStart: Long = 0L

    /** False until USB backlog at record start is measured and timeline counters are anchored. */
    @Volatile
    private var nativeTimelineBaselineReady: Boolean = false

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
    private var lastMetadataPersistNs: Long = 0L

    @Volatile
    private var acceptRecordingWrites: Boolean = true

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
    private val recordModeWarmCapture = AtomicBoolean(false)
    private var waveformWindowSec = DEFAULT_WAVEFORM_WINDOW_SEC
    private var waveformPeaksPerSecond = DEFAULT_WAVEFORM_PEAKS_PER_SEC
    private var lastWaveformEmitNs = 0L
    private var recordingMeterChunkCounter = 0
    private var lastMeterDecayNs = 0L
    private var syntheticRealtimeAnchorNs = 0L

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
        if (nativePcmRecordingActive) {
            val backend = activeBackend
            if (backend != null) {
                val nativeFrames = AudioEngineRouter.nativePcmFileFramesWritten(backend)
                val sessionFrames = nativeFrames - nativeFramesBaselineAtRecordingStart
                if (sessionFrames > framesCaptured) {
                    lastFrameReceivedNs = System.nanoTime()
                }
            }
        } else if (isNativeUsbCaptureRunning() && !isCaptureActive) {
            return true
        }
        val last = lastFrameReceivedNs
        if (last <= 0L) return false
        return System.nanoTime() - last <= withinMs * 1_000_000L
    }

    fun isNativeCaptureOwner(): Boolean {
        if (syntheticGenerator != null) return true
        val backend = activeBackend ?: return false
        return NativeAudioCaptureRegistry.isOwner(ownerId, backend)
    }

    fun isNativePcmRecording(): Boolean = nativePcmRecordingActive

    fun isNativeUsbCaptureRunning(): Boolean {
        if (syntheticGenerator != null) return isCaptureActive
        if (activeBackend != AudioBackend.UAC2) return isCaptureActive
        return NativeUac2Engine.isCaptureRunning() && isNativeCaptureOwner()
    }

    fun isUsbStreamHealthy(): Boolean = isCaptureActive || isNativeUsbCaptureRunning()

    fun isSyntheticCapture(): Boolean = syntheticGenerator != null

    fun recordingStartedAtEpochMs(): Long? = recordingStartedAtEpochMs

    fun recordElapsedSec(): Float {
        if (!isRecording) return 0f
        val frameSec = if (sampleRate > 0) framesCaptured.toFloat() / sampleRate else 0f
        val startedMs = recordingStartedAtEpochMs ?: return frameSec
        val wallSec = (System.currentTimeMillis() - startedMs).coerceAtLeast(0L) / 1000f
        return minOf(frameSec, wallSec)
    }

    @VisibleForTesting
    internal fun debugTimingSnapshot(): RecordingTimingSnapshot = RecordingTimingSnapshot(
        framesCaptured = framesCaptured,
        framesWritten = framesWritten,
        diskQueueDepth = diskWriteQueue.size,
        droppedFrames = droppedFrames,
        sampleRate = sampleRate,
    )

    data class RecordingTimingSnapshot(
        val framesCaptured: Long,
        val framesWritten: Long,
        val diskQueueDepth: Int,
        val droppedFrames: Long,
        val sampleRate: Int,
    ) {
        val capturedSec: Float
            get() = if (sampleRate > 0) framesCaptured.toFloat() / sampleRate else 0f

        val writtenSec: Float
            get() = if (sampleRate > 0) framesWritten.toFloat() / sampleRate else 0f
    }

    fun updateVuMetering(enabled: Boolean) {
        vuMeteringEnabled.set(enabled)
    }

    fun setRecordModeWarmCapture(enabled: Boolean) {
        recordModeWarmCapture.set(enabled)
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
            if (isRecording && nativePcmRecordingActive && isNativeCaptureOwner()) {
                return Result.success(Unit)
            }
            if (isNativeCaptureOwner() && isNativeUsbCaptureRunning()) {
                return Result.success(Unit)
            }
            val receiveWindowMs = if (isRecording) 5_000L else 2_000L
            if (isNativeCaptureOwner() && isReceivingAudio(receiveWindowMs)) {
                return Result.success(Unit)
            }
            if (isRecording) {
                OmtLog.w(
                    "CaptureSession",
                    "startCapture: keeping active capture during recording (no reconnect)",
                )
                return Result.success(Unit)
            }
            stopCaptureInternalLocked()
        }
        if (isRecording && usbDegraded && !nativePcmRecordingActive) {
            return reconnectCaptureLocked(scope, route, usbDevice)
        }
        if (isRecording && usbDegraded) {
            OmtLog.w(
                "CaptureSession",
                "startCapture: recording degraded with native PCM — keeping stream",
            )
            return Result.success(Unit)
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
        captureBytesPerFrame = route.pcmBytesPerFrame()
        if (route.backend == AudioBackend.UAC2) {
            val nativeBpf = AudioEngineRouter.capturePcmBytesPerFrame(route.backend)
            if (nativeBpf > 0) {
                captureBytesPerFrame = nativeBpf
            }
        }
        OmtLog.i("CaptureSession", "capture pcmBytesPerFrame=$captureBytesPerFrame ch=$channelCount")
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

    /** Restarts the Kotlin fanout loop when native USB capture is still running. */
    suspend fun ensureFanoutRunning(scope: CoroutineScope): Result<Unit> = lifecycleMutex.withLock {
        if (isCaptureActive) {
            return Result.success(Unit)
        }
        val backend = activeBackend
            ?: return Result.failure(IllegalStateException("No active capture backend"))
        if (!isNativeUsbCaptureRunning()) {
            return Result.failure(IllegalStateException("Native USB capture is not running"))
        }
        OmtLog.i("CaptureSession", "ensureFanoutRunning backend=$backend")
        startFanoutLoop(scope, backend)
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
        syntheticRealtimeAnchorNs = 0L
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
        nativeFramesBaselineAtRecordingStart = 0L
        nativeTimelineBaselineReady = false
        recordingMeterChunkCounter = 0
        resetLiveWaveformBuffers()
        syntheticRealtimeAnchorNs = 0L
        recordingStartedAtEpochMs = null
        lastMetadataPersistFrames = 0

        if (plan != null) {
            var stagingFile = if (channelCount >= INTERLEAVED_LIVE_THRESHOLD) {
                plan.liveCaptureStagingFile
            } else {
                null
            }
            var nativeActive = false
            val captureBackend = activeRoute?.backend ?: activeBackend
            if (stagingFile != null && captureBackend == AudioBackend.UAC2) {
                stagingFile.parentFile?.mkdirs()
                nativeActive = AudioEngineRouter.startNativePcmFileRecording(
                    stagingFile.absolutePath,
                    AudioBackend.UAC2,
                )
                if (nativeActive) {
                    OmtLog.i(
                        "CaptureSession",
                        "native PCM recording started path=${stagingFile.absolutePath} ch=$channelCount",
                    )
                } else {
                    OmtLog.w(
                        "CaptureSession",
                        "native PCM recording failed backend=$captureBackend path=${stagingFile.absolutePath}; " +
                            "using JNI disk path",
                    )
                    stagingFile = null
                }
            } else if (stagingFile != null) {
                OmtLog.w(
                    "CaptureSession",
                    "native PCM recording skipped backend=$captureBackend ch=$channelCount",
                )
                stagingFile = null
            }
            nativePcmRecordingActive = nativeActive
            sessionWriter = ResilientSessionWriter(
                primarySessionDir = plan.primarySessionDir,
                mirrorSessionDirs = plan.mirrorSessionDirs,
                spillSessionDir = plan.spillSessionDir,
                channelStrips = config.channelStrips,
                sampleRate = sampleRate,
                minFreeBytes = plan.minFreeBytes,
                primaryRoot = plan.primaryRoot,
                captureChannelCount = channelCount,
                liveCaptureStagingFile = if (nativeActive) stagingFile else null,
            )
            perChannelWriter = null
        } else {
            perChannelWriter = PerChannelWavWriter(dir, config.channelStrips, sampleRate)
            sessionWriter = null
        }

        buildSessionMetadata(config, sampleRate, framesWritten).writeTo(dir)
        val framesPerChunk = SessionRecorder.chunkFramesForChannels(channelCount)
        prewarmWriteBuffers(framesPerChunk * channelCount)
        if (activeBackend == AudioBackend.UAC2) {
            prewarmPcmWriteBuffers(framesPerChunk * captureBytesPerFrame)
        }
        if (!nativePcmRecordingActive) {
            startRecordingWriteLoop()
        }

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

    private fun stopNativePcmRecordingIfActive(): Long {
        if (!nativePcmRecordingActive) return 0L
        val backend = activeBackend ?: AudioBackend.UAC2
        AudioEngineRouter.stopNativePcmFileRecording(backend)
        val frames = AudioEngineRouter.nativePcmFileFramesWritten(backend)
        nativePcmRecordingActive = false
        nativeTimelineBaselineReady = false
        OmtLog.i("CaptureSession", "native PCM recording stopped frames=$frames")
        return frames
    }

    /**
     * Anchors the visible recording timeline to wall clock. Call when the user (or controller)
     * is about to show recording as active — after any native PCM warm-up / USB backlog drain.
     */
    suspend fun anchorRecordingTimeline(): Unit = lifecycleMutex.withLock {
        if (!isRecording) return
        if (nativeTimelineBaselineReady && recordingStartedAtEpochMs != null) return
        if (nativePcmRecordingActive) {
            val backend = activeBackend ?: AudioBackend.UAC2
            var backlogPeak = 0L
            var stableSamples = 0
            var settleIndex = 0
            while (settleIndex < 80) {
                val nativeFrames = AudioEngineRouter.nativePcmFileFramesWritten(backend)
                if (nativeFrames > backlogPeak) {
                    backlogPeak = nativeFrames
                    stableSamples = 0
                } else if (nativeFrames == backlogPeak && backlogPeak > 0L) {
                    stableSamples++
                }
                if (stableSamples >= 4 && backlogPeak > 0L) {
                    break
                }
                kotlinx.coroutines.delay(5)
                settleIndex++
            }
            nativeFramesBaselineAtRecordingStart = backlogPeak
            sessionWriter?.setNativeStagingSkipFrames(backlogPeak)
            OmtLog.i(
                "CaptureSession",
                "native PCM backlog settle baseline=$backlogPeak frames stable=$stableSamples",
            )
        }
        framesCaptured = 0L
        framesWritten = 0L
        sessionWriter?.setLiveFramesWritten(0L)
        nativeTimelineBaselineReady = true
        recordingStartedAtEpochMs = System.currentTimeMillis()
        OmtLog.i("CaptureSession", "recording timeline anchored")
    }

    private fun syncNativeRecordingProgress(backend: AudioBackend): Boolean {
        if (!nativeTimelineBaselineReady) return false
        val nativeFrames = AudioEngineRouter.nativePcmFileFramesWritten(backend)
        val sessionFrames = (nativeFrames - nativeFramesBaselineAtRecordingStart).coerceAtLeast(0L)
        if (sessionFrames <= framesCaptured) return false
        framesCaptured = sessionFrames
        framesWritten = sessionFrames
        sessionWriter?.setLiveFramesWritten(sessionFrames)
        lastFrameReceivedNs = System.nanoTime()
        if (recordingMeterChunkCounter % 16 == 0) {
            droppedFrames = AudioEngineRouter.recordingDroppedFrames(backend)
        }
        maybePersistTimelineAsync()
        return true
    }

    private suspend fun closeRecordingWritersLocked(markComplete: Boolean): RecordingSession? {
        val dir = sessionDir
        val config = recordingConfig
        val rate = sampleRate
        val capturedFrames = framesCaptured
        val resilient = sessionWriter
        val writer = perChannelWriter
        val legacy = legacyWavWriter
        acceptRecordingWrites = false
        stopRecordingWriteLoop()
        val nativeFrames = stopNativePcmRecordingIfActive()
        if (nativeFrames > 0L) {
            val sessionFrames = (nativeFrames - nativeFramesBaselineAtRecordingStart).coerceAtLeast(0L)
            resilient?.setLiveFramesWritten(sessionFrames)
            framesCaptured = sessionFrames.coerceAtLeast(framesCaptured)
            framesWritten = sessionFrames.coerceAtLeast(framesWritten)
        }
        val recordedFrames = when {
            resilient != null -> resilient.totalFramesWritten()
            writer != null -> writer.totalFramesWritten()
            else -> framesWritten.coerceAtLeast(capturedFrames)
        }
        OmtLog.i(
            "CaptureSession",
            "closeRecording captured=$capturedFrames queued=$framesWritten disk=$recordedFrames " +
                "dropped=$droppedFrames rate=$rate markComplete=$markComplete",
        )
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
            val hasChannelWavs = dir.listFiles()?.any { file ->
                file.isFile && file.extension.equals("wav", ignoreCase = true)
            } == true
            if (markComplete && hasChannelWavs) {
                SessionMetadata.read(dir)?.markComplete(dir)
            } else if (!markComplete) {
                SessionMetadata.read(dir)?.copy(incomplete = true)?.writeTo(dir)
            } else {
                OmtLog.e(
                    "CaptureSession",
                    "recording close produced no channel WAVs; leaving session incomplete",
                )
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
        stopNativePcmRecordingIfActive()
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
        captureBytesPerFrame = route.pcmBytesPerFrame()
        if (route.backend == AudioBackend.UAC2) {
            val nativeBpf = AudioEngineRouter.capturePcmBytesPerFrame(route.backend)
            if (nativeBpf > 0) {
                captureBytesPerFrame = nativeBpf
            }
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
                        if (!isRecording) {
                            decayMeterHoldForElapsed()
                        }
                        val maxFramesThisPass = if (isRecording || recordModeWarmCapture.get()) {
                            MAX_FRAMES_PER_FANOUT_PASS
                        } else {
                            framesPerChunk
                        }
                        var framesThisPass = 0
                        var gotAnyFrames = false

                        if (isRecording && nativePcmRecordingActive && backend == AudioBackend.UAC2 && synthetic == null) {
                            recordingMeterChunkCounter++
                            gotAnyFrames = syncNativeRecordingProgress(backend)
                            if (!gotAnyFrames) {
                                consecutiveEmptyReads++
                                if (isRecording && usbDegraded) {
                                    val advanced = catchUpTimelineToTarget(maxFramesPerPass = 65_536)
                                    if (advanced > 0) continue
                                }
                                if (consecutiveEmptyReads > 32) {
                                    Thread.sleep(1)
                                }
                            } else {
                                consecutiveEmptyReads = 0
                            }
                            continue
                        }

                        while (framesThisPass < maxFramesThisPass) {
                            val usePcmRecording =
                                isRecording && backend == AudioBackend.UAC2 && synthetic == null
                            val frames: Int
                            if (usePcmRecording) {
                                val bytesPerFrame = captureBytesPerFrame
                                val byteCount = framesPerChunk * bytesPerFrame
                                val recordPcmBuf = acquirePcmWriteBuffer(byteCount)
                                val rawFrames = AudioEngineRouter.readRecordedPcm(
                                    recordPcmBuf,
                                    framesPerChunk,
                                    backend,
                                )
                                frames = rawFrames.coerceIn(0, framesPerChunk)
                                if (frames <= 0) {
                                    releasePcmWriteBuffer(recordPcmBuf)
                                    break
                                }
                                gotAnyFrames = true
                                framesThisPass += frames
                                consecutiveEmptyReads = 0
                                lastFrameReceivedNs = System.nanoTime()
                                framesCaptured += frames
                                if (recordingMeterChunkCounter % 16 == 0) {
                                    droppedFrames = AudioEngineRouter.recordingDroppedFrames(backend)
                                }
                                enqueueRecordingPcmFrames(recordPcmBuf, frames, channelCount, bytesPerFrame)
                            } else {
                                val sampleCount = framesPerChunk * channelCount
                                val recordBuf = if (isRecording && backend != null && synthetic == null) {
                                    acquireWriteBuffer(sampleCount)
                                } else {
                                    null
                                }
                                val captureDest = recordBuf ?: scratch
                                val rawFrames = when {
                                    synthetic != null -> synthetic.fill(scratch, framesPerChunk)
                                    usbDegraded -> 0
                                    backend != null -> AudioEngineRouter.readRecordedFrames(
                                        captureDest,
                                        framesPerChunk,
                                        backend,
                                    )
                                    else -> 0
                                }
                                frames = rawFrames.coerceIn(0, framesPerChunk)

                                if (frames <= 0) {
                                    recordBuf?.let { releaseWriteBuffer(it) }
                                    break
                                }
                                gotAnyFrames = true
                                framesThisPass += frames
                                consecutiveEmptyReads = 0
                                lastFrameReceivedNs = System.nanoTime()
                                framesCaptured += frames
                                if (synthetic != null) {
                                    throttleSyntheticToRealtime()
                                }
                                if (backend != null && (!isRecording || recordingMeterChunkCounter % 16 == 0)) {
                                    droppedFrames = AudioEngineRouter.recordingDroppedFrames(backend)
                                }

                                if (isRecording) {
                                    if (recordBuf != null) {
                                        enqueueRecordingFrames(recordBuf, frames, channelCount)
                                    } else {
                                        writeRecordingFrames(scratch, frames, channelCount)
                                    }
                                } else {
                                    recordBuf?.let { releaseWriteBuffer(it) }
                                    accumulateWaveformPeaks(scratch, frames, channelCount)
                                    maybeEmitWaveformPeaks()

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
                                }
                            }

                            if (!isRecording || frames < framesPerChunk) {
                                break
                            }
                        }

                        if (!gotAnyFrames) {
                            consecutiveEmptyReads++
                            if (isRecording && usbDegraded) {
                                val advanced = catchUpTimelineToTarget(maxFramesPerPass = 65_536)
                                if (advanced > 0) continue
                            }
                            if (!isRecording && consecutiveEmptyReads > 2) {
                                Thread.sleep(2)
                            } else if (isRecording && consecutiveEmptyReads > 32) {
                                Thread.sleep(1)
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
                OmtLog.i(
                    "CaptureSession",
                    "fanout ended framesWritten=$framesWritten framesCaptured=$framesCaptured " +
                        "recording=$isRecording",
                )
            }
        }
    }

    private fun startRecordingWriteLoop() {
        if (diskWriteFuture != null) return
        val writers = ActiveRecordingWriters(
            resilient = sessionWriter,
            perChannel = perChannelWriter,
        )
        if (writers.resilient == null && writers.perChannel == null) return
        activeRecordingWriters = writers
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "capture-disk-write").apply {
                priority = Thread.NORM_PRIORITY
            }
        }
        diskWriteExecutor = executor
        diskWriteFuture = executor.submit {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            try {
                while (true) {
                    val request = diskWriteQueue.take()
                    if (request === RecordingWriteRequest.Shutdown) break
                    val active = activeRecordingWriters ?: continue
                    when (request) {
                        is RecordingWriteRequest.Frames -> {
                            val samples = request.samples
                            val frameCount = request.frameCount
                            val channels = request.channelCount
                            if (active.resilient != null) {
                                active.resilient.writeInterleavedMultiChannel(samples, frameCount, channels)
                            } else {
                                active.perChannel?.writeInterleavedMultiChannel(samples, frameCount, channels)
                            }
                            framesWritten += frameCount
                            if (request.returnToPool) {
                                releaseWriteBuffer(samples)
                            }
                        }
                        is RecordingWriteRequest.PcmFrames -> {
                            val samples = request.samples
                            val frameCount = request.frameCount
                            val channels = request.channelCount
                            val bytesPerFrame = request.bytesPerFrame
                            if (active.resilient != null) {
                                active.resilient.writeInterleavedPcm24(
                                    samples,
                                    frameCount,
                                    channels,
                                    bytesPerFrame,
                                )
                            } else {
                                active.perChannel?.writeInterleavedPcm24(
                                    samples,
                                    frameCount,
                                    channels,
                                    bytesPerFrame,
                                )
                            }
                            framesWritten += frameCount
                            if (request.returnToPool) {
                                releasePcmWriteBuffer(samples)
                            }
                        }
                        is RecordingWriteRequest.Silence -> {
                            val frameCount = request.frameCount
                            if (active.resilient != null) {
                                active.resilient.writeSilence(frameCount)
                            } else {
                                active.perChannel?.writeSilence(frameCount)
                            }
                            framesWritten += frameCount
                        }
                        RecordingWriteRequest.Shutdown -> break
                    }
                    maybePersistTimelineAsync()
                }
            } catch (_: IllegalStateException) {
                // writer closed during stop
            }
        }
    }

    private fun stopRecordingWriteLoop() {
        acceptRecordingWrites = false
        runCatching {
            diskWriteQueue.put(RecordingWriteRequest.Shutdown)
        }
        runCatching {
            diskWriteFuture?.get(120, TimeUnit.SECONDS)
        }.onFailure { e ->
            OmtLog.w("CaptureSession", "disk writer drain timed out: ${e.message}")
        }
        diskWriteFuture = null
        diskWriteExecutor?.shutdownNow()
        diskWriteExecutor = null
        diskWriteQueue.clear()
        activeRecordingWriters = null
        acceptRecordingWrites = true
    }

    private fun writeRecordingFrames(scratch: FloatArray, frames: Int, channels: Int) {
        if (!isRecording || !acceptRecordingWrites) return
        val toWrite = recordingFrameBudget(frames) ?: return
        if (activeRecordingWriters == null) {
            writeRecordingFramesSync(scratch, toWrite, channels)
            framesWritten += toWrite
            return
        }
        val sampleCount = toWrite * channels
        val copy = acquireWriteBuffer(sampleCount)
        System.arraycopy(scratch, 0, copy, 0, sampleCount)
        enqueueRecordingFrames(copy, toWrite, channels)
    }

    private fun enqueueRecordingPcmFrames(
        samples: ByteArray,
        frameCount: Int,
        channels: Int,
        bytesPerFrame: Int,
    ) {
        if (!isRecording || !acceptRecordingWrites) {
            releasePcmWriteBuffer(samples)
            return
        }
        val request = RecordingWriteRequest.PcmFrames(samples, frameCount, channels, bytesPerFrame)
        if (!enqueueRecordingWrite(request)) {
            releasePcmWriteBuffer(samples)
            OmtLog.w("CaptureSession", "disk queue saturated; dropped $frameCount recording pcm frames")
        }
    }

    private fun enqueueRecordingFrames(samples: FloatArray, frameCount: Int, channels: Int) {
        if (!isRecording || !acceptRecordingWrites) {
            releaseWriteBuffer(samples)
            return
        }
        val request = RecordingWriteRequest.Frames(samples, frameCount, channels)
        if (!enqueueRecordingWrite(request)) {
            releaseWriteBuffer(samples)
            OmtLog.w("CaptureSession", "disk queue saturated; dropped $frameCount recording frames")
        }
    }

    private fun recordingFrameBudget(frames: Int): Int? {
        if (!isRecording || !acceptRecordingWrites) return null
        val toWrite = if (usbDegraded) {
            val maxFrames = (sessionTargetFrames() - framesWritten).coerceAtLeast(0)
            minOf(frames.toLong(), maxFrames).toInt()
        } else {
            frames
        }
        return toWrite.takeIf { it > 0 }
    }

    private fun enqueueRecordingWrite(request: RecordingWriteRequest): Boolean {
        if (diskWriteQueue.offer(request)) return true
        repeat(4) {
            when (val evicted = diskWriteQueue.poll()) {
                is RecordingWriteRequest.Frames -> if (evicted.returnToPool) {
                    releaseWriteBuffer(evicted.samples)
                }
                is RecordingWriteRequest.PcmFrames -> if (evicted.returnToPool) {
                    releasePcmWriteBuffer(evicted.samples)
                }
                else -> Unit
            }
            if (diskWriteQueue.offer(request)) return true
        }
        return false
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

    private fun enqueueSilenceFrames(frames: Int) {
        if (frames <= 0 || !acceptRecordingWrites) return
        if (activeRecordingWriters != null) {
            if (!enqueueRecordingWrite(RecordingWriteRequest.Silence(frames))) {
                OmtLog.w("CaptureSession", "disk queue saturated; dropped $frames silence frames")
            }
            return
        }
        writeSilenceFramesSync(frames)
        framesWritten += frames
    }

    private fun writeSilenceFramesSync(frames: Int) {
        val resilient = sessionWriter
        val legacyWriter = perChannelWriter
        try {
            if (resilient != null) {
                resilient.writeSilence(frames)
            } else {
                legacyWriter?.writeSilence(frames)
            }
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
        val now = System.nanoTime()
        if (now - lastMetadataPersistNs < 2_000_000_000L) return
        val dir = sessionDir ?: return
        val config = recordingConfig ?: return
        val diskFrames = sessionWriter?.totalFramesWritten()
            ?: perChannelWriter?.totalFramesWritten()
            ?: framesWritten
        if (diskFrames - lastMetadataPersistFrames < sampleRate) return
        lastMetadataPersistFrames = diskFrames
        lastMetadataPersistNs = now
        buildSessionMetadata(config, sampleRate, diskFrames).writeTo(dir)
    }

    private fun prewarmWriteBuffers(sampleCount: Int) {
        writeBufferPool.clear()
        repeat(WRITE_BUFFER_POOL_SIZE) {
            writeBufferPool.offer(FloatArray(sampleCount))
        }
    }

    private fun prewarmPcmWriteBuffers(byteCount: Int) {
        pcmWriteBufferPool.clear()
        repeat(WRITE_BUFFER_POOL_SIZE) {
            pcmWriteBufferPool.offer(ByteArray(byteCount))
        }
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

    private fun acquirePcmWriteBuffer(byteCount: Int): ByteArray {
        val pooled = pcmWriteBufferPool.poll()
        return if (pooled != null && pooled.size >= byteCount) {
            pooled
        } else {
            ByteArray(byteCount)
        }
    }

    private fun releasePcmWriteBuffer(buffer: ByteArray) {
        pcmWriteBufferPool.offer(buffer)
    }

    private fun throttleSyntheticToRealtime() {
        val rate = sampleRate.coerceAtLeast(1)
        val anchor = syntheticRealtimeAnchorNs
        if (anchor <= 0L) {
            syntheticRealtimeAnchorNs = System.nanoTime()
            return
        }
        val targetNs = framesCaptured * 1_000_000_000L / rate
        val elapsedNs = System.nanoTime() - anchor
        val sleepNs = targetNs - elapsedNs
        if (sleepNs > 2_000_000L) {
            val sleepMs = sleepNs / 1_000_000L
            val sleepExtraNs = (sleepNs % 1_000_000L).toInt()
            if (sleepMs > 0L) {
                Thread.sleep(sleepMs)
            } else if (sleepExtraNs > 0) {
                Thread.sleep(0, sleepExtraNs)
            }
        }
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

    private fun accumulateWaveformPeaksFromPcm(
        pcm: ByteArray,
        frames: Int,
        channels: Int,
        bytesPerFrame: Int,
    ) {
        val subframeBytes = bytesPerFrame / channels.coerceAtLeast(1)
        val stride = max(1, frames / 256)
        val channelPeaks = FloatArray(channels)
        synchronized(meterLock) {
            for (ch in 0 until channels) {
                var peak = 0f
                var frame = 0
                while (frame < frames) {
                    val base = frame * bytesPerFrame + ch * subframeBytes
                    val sample = when (subframeBytes) {
                        4 -> {
                            val v = (pcm[base].toInt() and 0xFF) or
                                ((pcm[base + 1].toInt() and 0xFF) shl 8) or
                                ((pcm[base + 2].toInt() and 0xFF) shl 16) or
                                ((pcm[base + 3].toInt() and 0xFF) shl 24)
                            v.toFloat() / 2_147_483_648f
                        }
                        3 -> pcm24ToFloat(pcm, base)
                        else -> 0f
                    }
                    peak = maxOf(peak, kotlin.math.abs(sample))
                    frame += stride
                }
                channelPeaks[ch] = peak
                pendingWaveformPeaks[ch] = maxOf(pendingWaveformPeaks[ch], peak)
            }
            absorbChannelPeaks(meterHold, lastRawPeaks, channelPeaks, channels)
        }
    }

    private fun pcm24ToFloat(pcm: ByteArray, offset: Int): Float {
        val v = pcm[offset].toInt() and 0xFF or
            ((pcm[offset + 1].toInt() and 0xFF) shl 8) or
            ((pcm[offset + 2].toInt() and 0xFF) shl 16)
        val signed = if (v and 0x800000 != 0) v or -0x1000000 else v
        return signed / 8_388_608f
    }

    private fun accumulateWaveformPeaksLight(scratch: FloatArray, frames: Int, channels: Int) {
        val stride = max(1, frames / 256)
        val channelPeaks = FloatArray(channels)
        synchronized(meterLock) {
            for (ch in 0 until channels) {
                var peak = 0f
                var frame = 0
                while (frame < frames) {
                    val sample = scratch[frame * channels + ch]
                    peak = maxOf(peak, kotlin.math.abs(sample))
                    frame += stride
                }
                channelPeaks[ch] = peak
                pendingWaveformPeaks[ch] = maxOf(pendingWaveformPeaks[ch], peak)
            }
            absorbChannelPeaks(meterHold, lastRawPeaks, channelPeaks, channels)
        }
    }

    private fun accumulateWaveformPeaks(scratch: FloatArray, frames: Int, channels: Int) {
        val stride = when {
            isRecording && frames >= 4_096 -> max(1, frames / 2_048)
            frames >= 8_192 -> max(1, frames / 8_192)
            else -> 1
        }
        val channelPeaks = FloatArray(channels)
        synchronized(meterLock) {
            for (ch in 0 until channels) {
                var peak = 0f
                var frame = 0
                while (frame < frames) {
                    val sample = scratch[frame * channels + ch]
                    peak = maxOf(peak, kotlin.math.abs(sample))
                    frame += stride
                }
                channelPeaks[ch] = peak
                pendingWaveformPeaks[ch] = maxOf(pendingWaveformPeaks[ch], peak)
            }
            absorbChannelPeaks(meterHold, lastRawPeaks, channelPeaks, channels)
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
        private const val WRITE_BUFFER_POOL_SIZE = 24
        private const val DISK_WRITE_QUEUE_CAPACITY = 64
        private const val INTERLEAVED_LIVE_THRESHOLD = 8
        /** Drain up to ~680 ms of audio per fanout pass when the native ring has backlog. */
        private const val MAX_FRAMES_PER_FANOUT_PASS = 32_768

        private val captureFanoutDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "capture-fanout").apply {
                priority = Thread.NORM_PRIORITY + 1
            }
        }.asCoroutineDispatcher()

        private val writeBufferPool = ArrayBlockingQueue<FloatArray>(WRITE_BUFFER_POOL_SIZE)
        private val pcmWriteBufferPool = ArrayBlockingQueue<ByteArray>(WRITE_BUFFER_POOL_SIZE)

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
