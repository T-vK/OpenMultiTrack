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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openmultitrack.app.audio.CaptureSessionEngine
import org.openmultitrack.app.audio.MonitorMixConfig
import org.openmultitrack.app.audio.SessionPlayer
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.root.LoopbackSetup
import org.openmultitrack.app.root.RootShell
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioStreamHandle
import java.io.File

data class MixerSessionUiState(
    val mixerId: String,
    val mixerProfile: MixerProfile? = null,
    val usbDescriptor: UsbAudioDeviceDescriptor? = null,
    val probe: FullUsbProbeResult? = null,
    val probing: Boolean = false,
    val channelStrips: List<ChannelStripState> = emptyList(),
    val appMode: AppMode = AppMode.MULTITRACK_RECORD,
    val isRecording: Boolean = false,
    val isMonitoring: Boolean = false,
    val isUsbDegraded: Boolean = false,
    val isVirtualMicActive: Boolean = false,
    val isPlaying: Boolean = false,
    val transportState: TransportState = TransportState.IDLE,
    val statusMessage: String? = null,
    val warningMessage: String? = null,
    val lastRecordingPath: String? = null,
    val monitorOutputDeviceId: Int = -1,
    val captureChannelCount: Int = 0,
    val recordElapsedSec: Float = 0f,
    val waveformPeaks: Map<Int, FloatArray> = emptyMap(),
)

class MixerSessionController(
    val mixerId: String,
    private val appContext: Context,
    private val enumerator: UsbAudioEnumerator,
    private val settings: AppSettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureEngine = CaptureSessionEngine()
    private val player = SessionPlayer()
    private val captureMutex = Mutex()
    private var usbStream: UsbAudioStreamHandle? = null
    private var activeProbe: FullUsbProbeResult? = null
    private var activeUsbDevice: UsbDevice? = null
    private var activeDescriptor: UsbAudioDeviceDescriptor? = null
    private var loopbackPlaybackId: Int? = null
    private var usbDetachJob: Job? = null
    private var waveformJob: Job? = null
    private var profile: MixerProfile? = null

    private val storageRoot: File
        get() = settings.storageRootPath?.let { File(it) }
            ?: File(appContext.getExternalFilesDir(null), "OpenMultiTrack")

    private val _state = MutableStateFlow(MixerSessionUiState(mixerId = mixerId))
    val state: StateFlow<MixerSessionUiState> = _state.asStateFlow()

    fun setProfile(mixer: MixerProfile) {
        profile = mixer
        _state.update { it.copy(mixerProfile = mixer) }
    }

    fun setProbeResult(descriptor: UsbAudioDeviceDescriptor, probe: FullUsbProbeResult) {
        activeDescriptor = descriptor
        activeProbe = probe
        val chCount = probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
            ?: probe.input?.takeIf { it.isSuccess }?.channelCount
            ?: 0
        val strips = if (_state.value.channelStrips.isEmpty() && chCount > 0) {
            (0 until chCount).map { ChannelStripState(index = it) }
        } else {
            _state.value.channelStrips
        }
        _state.update {
            it.copy(
                usbDescriptor = descriptor,
                probe = probe,
                probing = false,
                captureChannelCount = chCount,
                channelStrips = strips,
                statusMessage = "Ready — $chCount channels",
            )
        }
    }

    fun setProbing(probing: Boolean) {
        _state.update { it.copy(probing = probing) }
    }

    fun setAppMode(mode: AppMode) {
        _state.update { it.copy(appMode = mode) }
    }

    fun updateChannelStrip(index: Int, transform: (ChannelStripState) -> ChannelStripState) {
        _state.update { s ->
            s.copy(
                channelStrips = s.channelStrips.map { if (it.index == index) transform(it) else it },
            )
        }
        applyMonitorRouting()
    }

    fun setMonitorOutputDevice(deviceId: Int) {
        _state.update { it.copy(monitorOutputDeviceId = deviceId) }
        applyMonitorRouting()
    }

    fun setMonitorGain(gain: Float) {
        settings.monitorGainLinear = gain
        applyMonitorRouting()
    }

    fun startMonitoring() {
        val descriptor = activeDescriptor ?: return
        val probe = activeProbe ?: return
        scope.launch {
            captureMutex.withLock {
                withContext(Dispatchers.IO) {
                    ensureCapture(descriptor, probe).getOrThrow()
                }
                applyMonitorRouting(enabled = true)
                startWaveformUpdates()
                _state.update {
                    it.copy(
                        isMonitoring = true,
                        statusMessage = "Monitoring",
                        warningMessage = null,
                    )
                }
            }
        }
    }

    fun stopMonitoring() {
        captureEngine.updateMonitor(MonitorMixConfig(enabled = false))
        _state.update { it.copy(isMonitoring = false, statusMessage = "Monitor off") }
        stopWaveformUpdatesIfIdle()
        releaseCaptureIfIdle()
    }

    fun startRecording() {
        val descriptor = activeDescriptor ?: return
        val probe = activeProbe ?: return
        val prof = profile ?: return
        scope.launch {
            try {
                captureMutex.withLock {
                    withContext(Dispatchers.IO) {
                        if (!captureEngine.isCaptureActive) {
                            ensureCapture(descriptor, probe).getOrThrow()
                        }
                        captureEngine.startRecording(
                            CaptureSessionEngine.RecordingConfig(
                                mixerId = prof.id,
                                mixerFolderName = prof.storageFolderName(),
                                storageRoot = storageRoot,
                                channelStrips = _state.value.channelStrips,
                            ),
                        ).getOrThrow()
                    }
                }
                startWaveformUpdates()
                _state.update {
                    it.copy(
                        isRecording = true,
                        transportState = TransportState.RECORDING,
                        statusMessage = "Recording…",
                        warningMessage = null,
                    )
                }
            } catch (e: Exception) {
                OmtLog.e("MixerSession", "startRecording failed", e)
                _state.update { it.copy(statusMessage = e.message) }
            }
        }
    }

    fun stopRecording() {
        scope.launch {
            val session = captureMutex.withLock {
                withContext(Dispatchers.IO) { captureEngine.stopRecording() }
            }
            _state.update {
                it.copy(
                    isRecording = false,
                    transportState = TransportState.IDLE,
                    lastRecordingPath = session?.filePath ?: it.lastRecordingPath,
                    statusMessage = session?.let { s -> "Saved → ${s.filePath}" } ?: "Stopped",
                )
            }
            stopWaveformUpdatesIfIdle()
            releaseCaptureIfIdle()
        }
    }

    fun onUsbDetached(deviceName: String?) {
        val desc = activeDescriptor ?: return
        if (deviceName != null && deviceName != desc.deviceName) return
        usbDetachJob?.cancel()
        captureEngine.setUsbDegraded(true)
        _state.update {
            it.copy(
                isUsbDegraded = true,
                warningMessage = "No USB audio — waiting for device. Recording silence if active.",
                transportState = if (it.isRecording) TransportState.RECORDING_DEGRADED else it.transportState,
            )
        }
        usbDetachJob = scope.launch {
            delay(settings.usbDetachDebounceMs)
            withContext(Dispatchers.IO) {
                if (_state.value.isUsbDegraded) {
                    captureMutex.withLock {
                        if (!_state.value.isRecording) {
                            captureEngine.stopCapture()
                        }
                        usbStream?.close()
                        usbStream = null
                    }
                }
            }
        }
    }

    fun onUsbAttached(descriptor: UsbAudioDeviceDescriptor) {
        if (activeDescriptor?.deviceName != descriptor.deviceName &&
            profile?.usbDeviceName != descriptor.deviceName
        ) {
            return
        }
        usbDetachJob?.cancel()
        activeDescriptor = descriptor
        scope.launch {
            val probe = activeProbe
            if (probe == null) return@launch
            captureMutex.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { ensureCapture(descriptor, probe) }
                }
            }
            captureEngine.setUsbDegraded(false)
            _state.update {
                it.copy(
                    isUsbDegraded = false,
                    warningMessage = null,
                    usbDescriptor = descriptor,
                    transportState = if (it.isRecording) TransportState.RECORDING else it.transportState,
                    statusMessage = "USB reconnected",
                )
            }
            if (_state.value.isMonitoring) applyMonitorRouting(enabled = true)
        }
    }

    fun shutdown() {
        waveformJob?.cancel()
        waveformJob = null
        scope.launch {
            captureMutex.withLock {
                withContext(Dispatchers.IO) { captureEngine.stopCapture() }
            }
            player.stop()
            usbStream?.close()
            usbStream = null
        }
    }

    private fun startWaveformUpdates() {
        if (waveformJob?.isActive == true) return
        waveformJob = scope.launch {
            while (isActive) {
                if (captureEngine.isCaptureActive) {
                    val peaks = captureEngine.waveformSnapshots(settings.waveformNormalized)
                    _state.update { it.copy(waveformPeaks = peaks) }
                }
                delay(50)
            }
        }
    }

    private fun stopWaveformUpdatesIfIdle() {
        if (!_state.value.isMonitoring && !_state.value.isRecording) {
            waveformJob?.cancel()
            waveformJob = null
            captureEngine.clearWaveforms()
            _state.update { it.copy(waveformPeaks = emptyMap()) }
        }
    }

    private fun applyMonitorRouting(enabled: Boolean = _state.value.isMonitoring) {
        val s = _state.value
        val monitorChannels = s.channelStrips.filter { it.monitoring }.map { it.index }.toSet()
        val solo = s.channelStrips.firstOrNull { it.solo }?.index
        captureEngine.updateMonitor(
            MonitorMixConfig(
                enabled = enabled,
                channelMonitoring = monitorChannels,
                soloChannel = solo,
                gainLinear = settings.monitorGainLinear,
                outputDeviceId = s.monitorOutputDeviceId,
            ),
        )
    }

    private suspend fun ensureCapture(
        descriptor: UsbAudioDeviceDescriptor,
        probe: FullUsbProbeResult,
    ): Result<Int> {
        if (captureEngine.isCaptureActive && !captureEngine.isUsbDegraded) {
            val count = channelCountFromProbe(probe)
            return Result.success(count)
        }
        val requested = channelCountFromProbe(probe)
        val device = enumerator.getUsbDevice(descriptor.deviceName)
            ?: return Result.failure(IllegalStateException("USB device not found"))
        val stream = openStream(descriptor)
            ?: return Result.failure(IllegalStateException("Could not open USB device"))
        usbStream?.close()
        usbStream = stream
        activeProbe = probe
        activeUsbDevice = device
        val route = AudioEngineRouter.resolveCaptureRoute(probe, stream, requested)
            ?: return Result.failure(IllegalStateException("No capture route"))
        return captureEngine.startCapture(scope, route, device).map { requested }
    }

    private fun channelCountFromProbe(probe: FullUsbProbeResult): Int =
        probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
            ?: probe.input?.takeIf { it.isSuccess }?.channelCount
            ?: 0

    private fun openStream(descriptor: UsbAudioDeviceDescriptor): UsbAudioStreamHandle? {
        val device = enumerator.getUsbDevice(descriptor.deviceName) ?: return null
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
        return UsbAudioStreamHandle.open(appContext, usbManager, device)
    }

    private fun releaseCaptureIfIdle() {
        scope.launch {
            captureMutex.withLock {
                if (!captureEngine.isRecording && !_state.value.isMonitoring) {
                    withContext(Dispatchers.IO) { captureEngine.stopCapture() }
                    usbStream?.close()
                    usbStream = null
                }
            }
        }
    }
}
