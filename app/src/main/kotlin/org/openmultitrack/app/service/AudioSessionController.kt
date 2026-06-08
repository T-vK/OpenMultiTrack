package org.openmultitrack.app.service

import android.content.Context
import android.hardware.usb.UsbDevice
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmultitrack.app.audio.CaptureSessionEngine
import org.openmultitrack.app.audio.SessionPlayer
import org.openmultitrack.app.root.LoopbackSetup
import org.openmultitrack.app.root.RootShell
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioStreamHandle
import java.io.File

data class AudioSessionUiState(
    val activeDeviceName: String? = null,
    val isRecording: Boolean = false,
    val isMonitoring: Boolean = false,
    val isVirtualMicActive: Boolean = false,
    val isPlaying: Boolean = false,
    val transportState: TransportState = TransportState.IDLE,
    val statusMessage: String? = null,
    val lastRecordingPath: String? = null,
    val monitorChannels: Set<Int> = emptySet(),
    val monitorOutputDeviceId: Int = -1,
    val virtualMicChannels: Set<Int> = emptySet(),
    val virtualMicStereo: Boolean = false,
    val rootAvailable: Boolean = false,
    val virtualMicStatus: String? = null,
    val captureChannelCount: Int = 0,
)

class AudioSessionController(
    private val appContext: Context,
    private val enumerator: UsbAudioEnumerator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureEngine = CaptureSessionEngine()
    private val player = SessionPlayer()
    private val sessionsDir = File(appContext.getExternalFilesDir(null), "sessions").apply { mkdirs() }

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var usbStream: UsbAudioStreamHandle? = null
    private var activeProbe: FullUsbProbeResult? = null
    private var activeUsbDevice: UsbDevice? = null
    private var loopbackPlaybackId: Int? = null

    private val _state = MutableStateFlow(AudioSessionUiState(rootAvailable = RootShell.isAvailable()))
    val state: StateFlow<AudioSessionUiState> = _state.asStateFlow()

    fun refreshRootStatus() {
        RootShell.invalidateCache()
        _state.update { it.copy(rootAvailable = RootShell.isAvailable()) }
    }

    fun setMonitorChannels(channels: Set<Int>) {
        _state.update { it.copy(monitorChannels = channels) }
        applyMonitorIfActive()
    }

    fun setMonitorOutputDevice(deviceId: Int) {
        _state.update { it.copy(monitorOutputDeviceId = deviceId) }
        applyMonitorIfActive()
    }

    fun setVirtualMicChannels(channels: Set<Int>, stereo: Boolean) {
        _state.update { it.copy(virtualMicChannels = channels, virtualMicStereo = stereo) }
        applyVirtualMicIfActive()
    }

    fun startMonitoring(
        descriptor: UsbAudioDeviceDescriptor,
        probe: FullUsbProbeResult,
        channels: Set<Int>,
        outputDeviceId: Int,
    ) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ensureCapture(descriptor, probe, channels)
            }
            result.onSuccess { channelCount ->
                captureEngine.updateMonitor(
                    CaptureSessionEngine.MonitorConfig(
                        enabled = true,
                        selectedChannels = channels,
                        outputDeviceId = outputDeviceId,
                    ),
                )
                acquireSessionResources()
                _state.update {
                    it.copy(
                        activeDeviceName = descriptor.deviceName,
                        isMonitoring = true,
                        monitorChannels = channels,
                        monitorOutputDeviceId = outputDeviceId,
                        captureChannelCount = channelCount,
                        statusMessage = "Monitoring ${channels.size} channel(s)",
                    )
                }
            }.onFailure { e ->
                OmtLog.e("Session", "startMonitoring failed", e)
                _state.update { it.copy(statusMessage = e.message) }
            }
        }
    }

    fun stopMonitoring() {
        captureEngine.updateMonitor(
            CaptureSessionEngine.MonitorConfig(false, emptySet(), -1),
        )
        _state.update { it.copy(isMonitoring = false, statusMessage = "Monitoring stopped") }
        releaseCaptureIfIdle()
    }

    fun enableVirtualMic(
        descriptor: UsbAudioDeviceDescriptor,
        probe: FullUsbProbeResult,
        channels: Set<Int>,
        stereo: Boolean,
    ) {
        scope.launch {
            val setup = withContext(Dispatchers.IO) {
                LoopbackSetup.setup(enumerator)
            }
            setup.onFailure { e ->
                _state.update { it.copy(virtualMicStatus = e.message, statusMessage = e.message) }
                return@launch
            }
            val loopback = setup.getOrThrow()
            val playbackId = loopback.playbackDeviceId
            if (playbackId == null) {
                _state.update {
                    it.copy(
                        virtualMicStatus = loopback.statusMessage,
                        statusMessage = "Loopback output not visible — ${loopback.statusMessage}",
                    )
                }
                return@launch
            }
            loopbackPlaybackId = playbackId

            val captureResult = withContext(Dispatchers.IO) {
                ensureCapture(descriptor, probe, channels)
            }
            captureResult.onSuccess { channelCount ->
                captureEngine.updateVirtualMic(
                    CaptureSessionEngine.VirtualMicConfig(
                        enabled = true,
                        selectedChannels = channels,
                        stereo = stereo,
                        loopbackDeviceId = playbackId,
                    ),
                )
                acquireSessionResources()
                _state.update {
                    it.copy(
                        activeDeviceName = descriptor.deviceName,
                        isVirtualMicActive = true,
                        virtualMicChannels = channels,
                        virtualMicStereo = stereo,
                        virtualMicStatus = loopback.statusMessage,
                        captureChannelCount = channelCount,
                        statusMessage = loopback.statusMessage,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(statusMessage = e.message) }
            }
        }
    }

    fun disableVirtualMic() {
        captureEngine.updateVirtualMic(null)
        loopbackPlaybackId = null
        _state.update {
            it.copy(isVirtualMicActive = false, virtualMicStatus = "Virtual mic disabled")
        }
        releaseCaptureIfIdle()
    }

    fun startRecording(descriptor: UsbAudioDeviceDescriptor, probe: FullUsbProbeResult) {
        val requestedChannels = probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
            ?: probe.input?.takeIf { it.isSuccess }?.channelCount
            ?: run {
                _state.update { it.copy(statusMessage = "No capture channels available.") }
                return
            }
        val allChannels = (0 until requestedChannels).toSet()

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (!captureEngine.isCaptureActive) {
                        ensureCapture(descriptor, probe, allChannels).getOrThrow()
                    }
                    captureEngine.startRecording(scope, sessionsDir).getOrThrow()
                }
                acquireSessionResources()
                _state.update {
                    it.copy(
                        activeDeviceName = descriptor.deviceName,
                        isRecording = true,
                        transportState = TransportState.RECORDING,
                        captureChannelCount = requestedChannels,
                        statusMessage = "Recording $requestedChannels ch → ${result.filePath}",
                    )
                }
            } catch (e: Exception) {
                OmtLog.e("Session", "startRecording failed", e)
                _state.update { it.copy(statusMessage = e.message) }
            }
        }
    }

    fun stopRecording() {
        scope.launch {
            val session = withContext(Dispatchers.IO) { captureEngine.stopRecording() }
            _state.update {
                it.copy(
                    isRecording = false,
                    transportState = TransportState.IDLE,
                    lastRecordingPath = session?.filePath ?: it.lastRecordingPath,
                    statusMessage = session?.let { s ->
                        "Saved ${s.channelCount}ch, ${s.framesRecorded} frames → ${s.filePath}"
                    } ?: "Recording stopped",
                )
            }
            releaseCaptureIfIdle()
        }
    }

    fun playLastRecording(descriptor: UsbAudioDeviceDescriptor, probe: FullUsbProbeResult) {
        val path = _state.value.lastRecordingPath ?: run {
            _state.update { it.copy(statusMessage = "No recording to play.") }
            return
        }
        if (_state.value.isVirtualMicActive) {
            _state.update { it.copy(statusMessage = "Stop virtual mic before USB soundcheck playback.") }
            return
        }
        val wavChannels = runCatching {
            WavReader(File(path)).use { it.format.channelCount }
        }.getOrDefault(2)

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val device = enumerator.getUsbDevice(descriptor.deviceName)
                    ?: return@withContext Result.failure(IllegalStateException("USB device not found"))
                val stream = openStream(descriptor)
                    ?: return@withContext Result.failure(
                        IllegalStateException("Could not open USB device"),
                    )
                usbStream?.close()
                usbStream = stream
                val route = AudioEngineRouter.resolvePlaybackRoute(probe, stream, wavChannels)
                    ?: return@withContext Result.failure(
                        IllegalStateException("No playback route"),
                    )
                player.play(scope, File(path), route, usbDevice = device)
            }
            result.onSuccess {
                acquireSessionResources()
                _state.update {
                    it.copy(isPlaying = true, transportState = TransportState.PLAYING, statusMessage = "Playing $path")
                }
            }.onFailure { e ->
                usbStream?.close()
                usbStream = null
                _state.update { it.copy(statusMessage = e.message) }
            }
        }
    }

    fun stopPlayback() {
        player.stop()
        usbStream?.close()
        usbStream = null
        _state.update {
            it.copy(isPlaying = false, transportState = TransportState.IDLE, statusMessage = "Playback stopped")
        }
        releaseSessionResourcesIfIdle()
    }

    fun onUsbDetached(deviceName: String?) {
        if (deviceName != null && deviceName != _state.value.activeDeviceName) return
        scope.launch {
            OmtLog.w("Session", "USB detached — emergency stop")
            withContext(Dispatchers.IO) {
                if (_state.value.isRecording) {
                    captureEngine.stopRecording()
                }
                captureEngine.stopCapture()
                player.stop()
            }
            usbStream?.close()
            usbStream = null
            activeProbe = null
            activeUsbDevice = null
            _state.update {
                it.copy(
                    activeDeviceName = null,
                    isRecording = false,
                    isMonitoring = false,
                    isVirtualMicActive = false,
                    isPlaying = false,
                    transportState = TransportState.IDLE,
                    statusMessage = "USB device disconnected — session stopped",
                )
            }
            releaseSessionResourcesIfIdle()
        }
    }

    fun shutdown() {
        scope.launch {
            withContext(Dispatchers.IO) {
                captureEngine.stopCapture()
                player.stop()
            }
            usbStream?.close()
            usbStream = null
            releaseSessionResourcesIfIdle()
        }
    }

    private fun applyMonitorIfActive() {
        val s = _state.value
        if (!s.isMonitoring) return
        captureEngine.updateMonitor(
            CaptureSessionEngine.MonitorConfig(
                enabled = true,
                selectedChannels = s.monitorChannels,
                outputDeviceId = s.monitorOutputDeviceId,
            ),
        )
    }

    private fun applyVirtualMicIfActive() {
        val s = _state.value
        if (!s.isVirtualMicActive) return
        val playbackId = loopbackPlaybackId ?: return
        captureEngine.updateVirtualMic(
            CaptureSessionEngine.VirtualMicConfig(
                enabled = true,
                selectedChannels = s.virtualMicChannels,
                stereo = s.virtualMicStereo,
                loopbackDeviceId = playbackId,
            ),
        )
    }

    private suspend fun ensureCapture(
        descriptor: UsbAudioDeviceDescriptor,
        probe: FullUsbProbeResult,
        @Suppress("UNUSED_PARAMETER") channels: Set<Int>,
    ): Result<Int> {
        if (captureEngine.isCaptureActive && _state.value.activeDeviceName == descriptor.deviceName) {
            val count = probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
                ?: probe.input?.takeIf { it.isSuccess }?.channelCount
                ?: return Result.failure(IllegalStateException("No capture channels"))
            return Result.success(count)
        }

        val requestedChannels = probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
            ?: probe.input?.takeIf { it.isSuccess }?.channelCount
            ?: return Result.failure(IllegalStateException("No capture channels"))

        val device = enumerator.getUsbDevice(descriptor.deviceName)
            ?: return Result.failure(IllegalStateException("USB device not found"))
        val stream = openStream(descriptor)
            ?: return Result.failure(IllegalStateException("Could not open USB device"))
        usbStream?.close()
        usbStream = stream
        activeProbe = probe
        activeUsbDevice = device

        val route = AudioEngineRouter.resolveCaptureRoute(probe, stream, requestedChannels)
            ?: return Result.failure(IllegalStateException("No capture route"))
        return captureEngine.startCapture(scope, route, device).map { requestedChannels }
    }

    private fun openStream(descriptor: UsbAudioDeviceDescriptor): UsbAudioStreamHandle? {
        val device = enumerator.getUsbDevice(descriptor.deviceName) ?: return null
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
        return UsbAudioStreamHandle.open(appContext, usbManager, device)
    }

    private fun releaseCaptureIfIdle() {
        scope.launch {
            if (!captureEngine.isRecording &&
                !_state.value.isMonitoring &&
                !_state.value.isVirtualMicActive
            ) {
                withContext(Dispatchers.IO) { captureEngine.stopCapture() }
                usbStream?.close()
                usbStream = null
            }
            releaseSessionResourcesIfIdle()
        }
    }

    private fun acquireSessionResources() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "OpenMultiTrack::AudioSession",
            ).apply { acquire() }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioManager.requestAudioFocus(audioFocusRequest!!)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun releaseSessionResourcesIfIdle() {
        val s = _state.value
        if (s.isRecording || s.isMonitoring || s.isVirtualMicActive || s.isPlaying) return
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioFocusRequest = null
    }
}
