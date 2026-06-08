package org.openmultitrack.app.audio

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.audio.NativeAudioMonitor
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.session.RecordingSession
import org.openmultitrack.sessionio.wav.WavWriter
import org.openmultitrack.usb.AudioBackend
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.CaptureRoute
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

/**
 * Single USB capture stream shared by recording, live monitor, and optional root virtual mic.
 */
class CaptureSessionEngine {
    data class MonitorConfig(
        val enabled: Boolean,
        val selectedChannels: Set<Int>,
        val outputDeviceId: Int,
    )

    data class VirtualMicConfig(
        val enabled: Boolean,
        val selectedChannels: Set<Int>,
        val stereo: Boolean,
        val loopbackDeviceId: Int,
    )

    private var fanoutJob: Job? = null
    private var activeRoute: CaptureRoute? = null
    private var activeBackend: AudioBackend? = null
    private var wavWriter: WavWriter? = null
    private var outputFile: File? = null
    private var channelCount: Int = 2
    private var sampleRate: Int = 48_000

    @Volatile
    private var framesWritten: Long = 0

    @Volatile
    private var droppedFrames: Long = 0

    private val monitorConfig = AtomicReference(MonitorConfig(false, emptySet(), -1))
    private val virtualMicConfig = AtomicReference<VirtualMicConfig?>(null)
    private var monitorOutputRunning = false
    private var virtualMicOutputRunning = false

    val isCaptureActive: Boolean
        get() = fanoutJob?.isActive == true

    val isRecording: Boolean
        get() = wavWriter != null

    fun updateMonitor(config: MonitorConfig) {
        monitorConfig.set(config)
        if (!config.enabled && monitorOutputRunning) {
            NativeAudioMonitor.stop()
            monitorOutputRunning = false
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

    fun startCapture(
        scope: CoroutineScope,
        route: CaptureRoute,
        usbDevice: UsbDevice?,
    ): Result<Unit> {
        if (isCaptureActive && activeRoute?.backend == route.backend) {
            return Result.success(Unit)
        }
        stopCaptureSync()
        activeBackend = route.backend
        activeRoute = route

        OmtLog.i(
            "CaptureSession",
            "startCapture backend=${route.backend} channels=${route.channelCount}",
        )
        val status = AudioEngineRouter.startRecording(route, usbDevice)
        if (!status.active) {
            activeBackend = null
            activeRoute = null
            return Result.failure(IllegalStateException(status.errorMessage ?: "Capture failed"))
        }

        channelCount = status.channelCount
        sampleRate = status.sampleRate
        startFanoutLoop(scope, route.backend)
        return Result.success(Unit)
    }

    fun startRecording(scope: CoroutineScope, outputDir: File): Result<RecordingSession> {
        val route = activeRoute ?: return Result.failure(IllegalStateException("Capture not running"))
        if (wavWriter != null) {
            return Result.success(currentRecordingSession())
        }

        val file = File(
            outputDir,
            "session_${channelCount}ch_${sampleRate}hz_${System.currentTimeMillis()}.wav",
        )
        outputFile = file
        framesWritten = 0
        wavWriter = WavWriter(file, channelCount, sampleRate)

        if (!isCaptureActive) {
            return Result.failure(IllegalStateException("Capture not running"))
        }

        return Result.success(
            RecordingSession(
                filePath = file.absolutePath,
                channelCount = channelCount,
                sampleRate = sampleRate,
                framesRecorded = 0,
            ),
        )
    }

    suspend fun stopRecording(): RecordingSession? {
        wavWriter?.close()
        wavWriter = null
        val file = outputFile
        outputFile = null
        if (!needsCapture()) {
            stopCaptureInternal()
        }
        return file?.let {
            RecordingSession(
                filePath = it.absolutePath,
                channelCount = channelCount,
                sampleRate = sampleRate,
                framesRecorded = framesWritten,
            )
        }
    }

    suspend fun stopCapture() {
        wavWriter?.close()
        wavWriter = null
        outputFile = null
        stopCaptureInternal()
    }

    private fun stopCaptureSync() {
        wavWriter?.close()
        wavWriter = null
        outputFile = null
        kotlinx.coroutines.runBlocking { stopCaptureInternal() }
    }

    fun droppedFrameCount(): Long = droppedFrames

    private fun needsCapture(): Boolean {
        val monitor = monitorConfig.get()
        val virtualMic = virtualMicConfig.get()
        return wavWriter != null || monitor.enabled || (virtualMic?.enabled == true)
    }

    private suspend fun stopCaptureInternal() {
        fanoutJob?.cancelAndJoin()
        fanoutJob = null
        NativeAudioMonitor.stop()
        NativeAudioEngine.stopPlayback()
        monitorOutputRunning = false
        virtualMicOutputRunning = false
        AudioEngineRouter.stopRecording()
        activeBackend = null
        activeRoute = null
    }

    private fun currentRecordingSession(): RecordingSession {
        val file = outputFile ?: error("No recording file")
        return RecordingSession(
            filePath = file.absolutePath,
            channelCount = channelCount,
            sampleRate = sampleRate,
            framesRecorded = framesWritten,
        )
    }

    private fun startFanoutLoop(scope: CoroutineScope, backend: AudioBackend) {
        val framesPerChunk = SessionRecorder.chunkFramesForChannels(channelCount)
        val scratch = FloatArray(framesPerChunk * channelCount)
        val monitorScratch = FloatArray(framesPerChunk * 2)
        val virtualMicScratch = FloatArray(framesPerChunk * 2)

        fanoutJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val frames = AudioEngineRouter.readRecordedFrames(scratch, framesPerChunk, backend)
                    if (frames <= 0) {
                        Thread.sleep(2)
                        continue
                    }
                    droppedFrames = AudioEngineRouter.recordingDroppedFrames(backend)

                    wavWriter?.writeInterleavedFloat(scratch, frames)?.also {
                        framesWritten += frames
                    }

                    val monitor = monitorConfig.get()
                    if (monitor.enabled && monitor.selectedChannels.isNotEmpty()) {
                        ensureMonitorOutput(monitor)
                        val outCh = ChannelMixdown.outputChannelCount(monitor.selectedChannels, stereo = true)
                        val mixed = ChannelMixdown.mixToOutput(
                            scratch,
                            frames,
                            channelCount,
                            monitor.selectedChannels,
                            stereo = outCh == 2,
                            dest = monitorScratch,
                        )
                        if (mixed > 0) {
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
                OmtLog.i("CaptureSession", "fanout loop ended framesWritten=$framesWritten")
            }
        }
    }

    private fun ensureMonitorOutput(config: MonitorConfig) {
        if (monitorOutputRunning) return
        val outCh = max(1, ChannelMixdown.outputChannelCount(config.selectedChannels, stereo = true))
        val status = NativeAudioMonitor.start(config.outputDeviceId, outCh, sampleRate)
        if (status.active) {
            monitorOutputRunning = true
        } else {
            OmtLog.e("CaptureSession", "monitor output failed: ${status.errorMessage}")
        }
    }

    private fun ensureVirtualMicOutput(config: VirtualMicConfig) {
        if (virtualMicOutputRunning) return
        val outCh = max(1, ChannelMixdown.outputChannelCount(config.selectedChannels, config.stereo))
        val status = NativeAudioEngine.startPlayback(config.loopbackDeviceId, outCh, sampleRate)
        if (status.active) {
            virtualMicOutputRunning = true
        } else {
            OmtLog.e("CaptureSession", "virtual mic output failed: ${status.errorMessage}")
        }
    }
}
