package org.openmultitrack.app.audio

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.audio.NativeAudioMonitor
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.session.RecordingSession
import org.openmultitrack.sessionio.session.SessionDirectory
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.wav.PerChannelWavWriter
import org.openmultitrack.sessionio.wav.WavWriter
import org.openmultitrack.usb.AudioBackend
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.CaptureRoute
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Single USB capture stream shared by recording, live monitor, and optional root virtual mic.
 */
class CaptureSessionEngine {
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
        val customTitle: String? = null,
    )

    private val lifecycleMutex = Mutex()
    private var fanoutJob: Job? = null
    private var activeRoute: CaptureRoute? = null
    private var activeBackend: AudioBackend? = null
    private var perChannelWriter: PerChannelWavWriter? = null
    private var legacyWavWriter: WavWriter? = null
    private var sessionDir: File? = null
    private var recordingConfig: RecordingConfig? = null
    private var channelCount: Int = 2
    private var sampleRate: Int = 48_000

    @Volatile
    private var framesWritten: Long = 0

    @Volatile
    private var droppedFrames: Long = 0

    @Volatile
    private var usbDegraded: Boolean = false

    private val monitorConfig = AtomicReference(MonitorMixConfig())
    private val virtualMicConfig = AtomicReference<VirtualMicConfig?>(null)
    private val monitorOutputDeviceId = AtomicReference(-1)
    private val monitorOutputRunning = AtomicBoolean(false)
    private var virtualMicOutputRunning = false

    val isCaptureActive: Boolean
        get() = fanoutJob?.isActive == true

    val isRecording: Boolean
        get() = perChannelWriter != null || legacyWavWriter != null

    val isUsbDegraded: Boolean
        get() = usbDegraded

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

    suspend fun startCapture(
        scope: CoroutineScope,
        route: CaptureRoute,
        usbDevice: UsbDevice?,
    ): Result<Unit> = lifecycleMutex.withLock {
        if (isCaptureActive && activeRoute?.backend == route.backend && !usbDegraded) {
            return Result.success(Unit)
        }
        stopCaptureInternalLocked()
        activeBackend = route.backend
        activeRoute = route

        OmtLog.i("CaptureSession", "startCapture backend=${route.backend} ch=${route.channelCount}")
        val status = AudioEngineRouter.startRecording(route, usbDevice)
        if (!status.active) {
            activeBackend = null
            activeRoute = null
            return Result.failure(IllegalStateException(status.errorMessage ?: "Capture failed"))
        }

        channelCount = status.channelCount
        sampleRate = status.sampleRate
        usbDegraded = false
        startFanoutLoop(scope, route.backend)
        Result.success(Unit)
    }

    fun startRecording(config: RecordingConfig): Result<RecordingSession> {
        if (!isCaptureActive) {
            return Result.failure(IllegalStateException("Capture not running"))
        }
        if (isRecording) {
            return Result.success(currentRecordingSession())
        }

        val dir = SessionDirectory.createSessionDir(config.storageRoot, config.mixerFolderName)
        sessionDir = dir
        recordingConfig = config
        framesWritten = 0

        val writer = PerChannelWavWriter(dir, config.channelStrips, sampleRate)
        perChannelWriter = writer

        SessionMetadata(
            mixerId = config.mixerId,
            mixerFolderName = config.mixerFolderName,
            customTitle = config.customTitle,
            sampleRate = sampleRate,
            format = org.openmultitrack.domain.session.SessionFormat.PER_CHANNEL_WAV,
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
        ).writeTo(dir)

        val armedCount = config.channelStrips.count { it.armed }
        return Result.success(
            RecordingSession(
                filePath = dir.absolutePath,
                channelCount = armedCount,
                sampleRate = sampleRate,
                framesRecorded = 0,
            ),
        )
    }

    suspend fun stopRecording(): RecordingSession? = lifecycleMutex.withLock {
        val dir = sessionDir
        val config = recordingConfig
        perChannelWriter?.close()
        perChannelWriter = null
        legacyWavWriter?.close()
        legacyWavWriter = null
        recordingConfig = null
        sessionDir = null

        if (dir != null && config != null) {
            SessionMetadata.read(dir)?.markComplete(dir)
        }

        if (!needsCapture()) {
            stopCaptureInternalLocked()
        }

        dir?.let {
            RecordingSession(
                filePath = it.absolutePath,
                channelCount = channelCount,
                sampleRate = sampleRate,
                framesRecorded = framesWritten,
            )
        }
    }

    suspend fun stopCapture() = lifecycleMutex.withLock {
        perChannelWriter?.close()
        perChannelWriter = null
        legacyWavWriter?.close()
        legacyWavWriter = null
        sessionDir = null
        recordingConfig = null
        stopCaptureInternalLocked()
    }

    fun droppedFrameCount(): Long = droppedFrames

    private fun needsCapture(): Boolean {
        val monitor = monitorConfig.get()
        val virtualMic = virtualMicConfig.get()
        return isRecording || monitor.enabled || (virtualMic?.enabled == true)
    }

    private suspend fun stopCaptureInternalLocked() {
        fanoutJob?.cancelAndJoin()
        fanoutJob = null
        stopMonitorOutput()
        NativeAudioEngine.stopPlayback()
        virtualMicOutputRunning = false
        AudioEngineRouter.stopRecording()
        activeBackend = null
        activeRoute = null
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

    private fun startFanoutLoop(scope: CoroutineScope, backend: AudioBackend) {
        val framesPerChunk = SessionRecorder.chunkFramesForChannels(channelCount)
        val scratch = FloatArray(framesPerChunk * channelCount)
        val monitorScratch = FloatArray(framesPerChunk * 2)
        val virtualMicScratch = FloatArray(framesPerChunk * 2)
        var consecutiveEmptyReads = 0

        fanoutJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val frames = if (usbDegraded) {
                        0
                    } else {
                        AudioEngineRouter.readRecordedFrames(scratch, framesPerChunk, backend)
                    }

                    if (frames <= 0) {
                        consecutiveEmptyReads++
                        if (isRecording && (usbDegraded || consecutiveEmptyReads > 3)) {
                            val silenceFrames = minOf(256, framesPerChunk)
                            perChannelWriter?.writeSilence(silenceFrames)
                            framesWritten += silenceFrames
                        }
                        Thread.sleep(2)
                        continue
                    }
                    consecutiveEmptyReads = 0
                    droppedFrames = AudioEngineRouter.recordingDroppedFrames(backend)

                    perChannelWriter?.writeInterleavedMultiChannel(scratch, frames, channelCount)?.also {
                        framesWritten += frames
                    }

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
            } finally {
                OmtLog.i("CaptureSession", "fanout ended frames=$framesWritten")
            }
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
