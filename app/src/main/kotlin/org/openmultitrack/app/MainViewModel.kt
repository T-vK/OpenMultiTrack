package org.openmultitrack.app

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmultitrack.app.audio.SessionPlayer
import org.openmultitrack.app.audio.SessionRecorder
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService
import org.openmultitrack.usb.UsbAudioStreamHandle
import java.io.File

data class DeviceRow(
    val descriptor: UsbAudioDeviceDescriptor,
    val hasPermission: Boolean,
    val probe: FullUsbProbeResult? = null,
    val probing: Boolean = false,
    val isRecording: Boolean = false,
    val recordChannelCount: Int? = null,
)

data class MainUiState(
    val devices: List<DeviceRow> = emptyList(),
    val statusMessage: String? = null,
    val isRefreshing: Boolean = false,
    val lastRecordingPath: String? = null,
    val transportState: TransportState = TransportState.IDLE,
    val playbackPositionFrames: Long = 0,
)

class MainViewModel(
    private val appContext: Context,
    private val enumerator: UsbAudioEnumerator,
    private val probeService: UsbAudioProbeService,
) : ViewModel() {
    private val recorder = SessionRecorder()
    private val player = SessionPlayer()
    private val sessionsDir: File = File(appContext.getExternalFilesDir(null), "sessions").apply { mkdirs() }
    private val usbManager: UsbManager =
        appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private var activeRecordingDevice: String? = null
    private var usbStream: UsbAudioStreamHandle? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun refreshDevices() {
        viewModelScope.launch {
            OmtLog.d("ViewModel", "refreshDevices")
            _uiState.update { it.copy(isRefreshing = true) }
            val rows = withContext(Dispatchers.IO) {
                enumerator.listUsbDevices().map { usb ->
                    val existing = _uiState.value.devices.firstOrNull { it.descriptor.deviceName == usb.deviceName }
                    DeviceRow(
                        descriptor = usb,
                        hasPermission = enumerator.hasUsbPermission(usb.deviceName),
                        isRecording = usb.deviceName == activeRecordingDevice,
                        probe = existing?.probe,
                        recordChannelCount = existing?.probe?.input?.channelCount?.takeIf { it > 0 }
                            ?: existing?.probe?.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 },
                    )
                }
            }
            OmtLog.i("ViewModel", "found ${rows.size} USB device(s): ${rows.map { it.descriptor.productName }}")
            _uiState.update {
                it.copy(devices = rows, isRefreshing = false, statusMessage = null)
            }
        }
    }

    fun onUsbPermissionGranted(deviceName: String) {
        OmtLog.i("ViewModel", "onUsbPermissionGranted deviceName=$deviceName")
        refreshDevices()
        val row = _uiState.value.devices.firstOrNull { it.descriptor.deviceName == deviceName }
        if (row != null) probeDevice(row.descriptor)
    }

    fun probeDevice(descriptor: UsbAudioDeviceDescriptor) {
        OmtLog.i("ViewModel", "probeDevice ${descriptor.productName} (${descriptor.deviceName})")
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    devices = state.devices.map { row ->
                        if (row.descriptor.deviceName == descriptor.deviceName) row.copy(probing = true) else row
                    },
                )
            }
            val result = withContext(Dispatchers.IO) { probeService.probe(descriptor) }
            val inputChannels = result.input?.takeIf { it.isSuccess }?.channelCount
                ?: result.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
            OmtLog.i(
                "ViewModel",
                "probe result input=${result.input?.channelCount}ch output=${result.output?.channelCount}ch " +
                    "uac2=${result.uac2Caps?.maxCaptureChannels}in " +
                    "inputErr=${result.input?.errorMessage} outputErr=${result.output?.errorMessage}",
            )
            _uiState.update { state ->
                state.copy(
                    devices = state.devices.map { row ->
                        if (row.descriptor.deviceName == descriptor.deviceName) {
                            row.copy(
                                probing = false,
                                probe = result,
                                recordChannelCount = inputChannels,
                            )
                        } else {
                            row
                        }
                    },
                    statusMessage = buildProbeMessage(result, inputChannels),
                )
            }
        }
    }

    fun startRecording(descriptor: UsbAudioDeviceDescriptor) {
        val row = _uiState.value.devices.firstOrNull { it.descriptor.deviceName == descriptor.deviceName }
        val probe = row?.probe
        if (probe == null) {
            _uiState.update { it.copy(statusMessage = "Probe device before recording.") }
            return
        }

        val requestedChannels = probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
            ?: probe.input?.takeIf { it.isSuccess }?.channelCount
            ?: run {
                _uiState.update { it.copy(statusMessage = "No capture channels available.") }
                return
            }

        OmtLog.i(
            "ViewModel",
            "startRecording ${descriptor.productName} requestedChannels=$requestedChannels",
        )
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val device = enumerator.getUsbDevice(descriptor.deviceName)
                    ?: return@withContext Result.failure(IllegalStateException("USB device not found"))
                val stream = openStream(descriptor) ?: return@withContext Result.failure(
                    IllegalStateException("Could not open USB device for streaming"),
                )
                usbStream?.close()
                usbStream = stream
                val route = AudioEngineRouter.resolveCaptureRoute(probe, stream, requestedChannels)
                    ?: return@withContext Result.failure(
                        IllegalStateException("No audio capture route available"),
                    )
                recorder.start(
                    scope = viewModelScope,
                    route = route,
                    outputDir = sessionsDir,
                    usbDevice = device,
                )
            }
            result.onSuccess { session ->
                activeRecordingDevice = descriptor.deviceName
                _uiState.update { state ->
                    state.copy(
                        statusMessage = "Recording ${session.channelCount}ch @ ${session.sampleRate}Hz → ${session.filePath}",
                        devices = state.devices.map { dev ->
                            dev.copy(isRecording = dev.descriptor.deviceName == descriptor.deviceName)
                        },
                    )
                }
            }.onFailure { error ->
                usbStream?.close()
                usbStream = null
                OmtLog.e("ViewModel", "startRecording failed", error)
                _uiState.update { it.copy(statusMessage = error.message) }
            }
        }
    }

    fun stopRecording() {
        OmtLog.i("ViewModel", "stopRecording")
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { recorder.stop() }
            usbStream?.close()
            usbStream = null
            val dropped = recorder.droppedFrameCount()
            OmtLog.i(
                "ViewModel",
                "recording stopped frames=${session?.framesRecorded} dropped=$dropped path=${session?.filePath}",
            )
            activeRecordingDevice = null
            _uiState.update { state ->
                state.copy(
                    lastRecordingPath = session?.filePath,
                    statusMessage = session?.let {
                        val dropNote = if (dropped > 0) " ($dropped drops)" else ""
                        "Saved ${it.channelCount}ch, ${it.framesRecorded} frames$dropNote → ${it.filePath}"
                    } ?: "Recording stopped",
                    devices = state.devices.map { it.copy(isRecording = false) },
                )
            }
        }
    }

    fun playLastRecording(descriptor: UsbAudioDeviceDescriptor) {
        val path = _uiState.value.lastRecordingPath ?: run {
            _uiState.update { it.copy(statusMessage = "No recording to play.") }
            return
        }
        val probe = _uiState.value.devices
            .firstOrNull { it.descriptor.deviceName == descriptor.deviceName }
            ?.probe
        if (probe == null) {
            _uiState.update { it.copy(statusMessage = "Probe device before playback.") }
            return
        }
        val wavChannels = runCatching {
            org.openmultitrack.sessionio.wav.WavReader(File(path)).use { it.format.channelCount }
        }.getOrDefault(2)

        OmtLog.i("ViewModel", "playLastRecording path=$path channels=$wavChannels")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val device = enumerator.getUsbDevice(descriptor.deviceName)
                    ?: return@withContext Result.failure(IllegalStateException("USB device not found"))
                val stream = openStream(descriptor) ?: return@withContext Result.failure(
                    IllegalStateException("Could not open USB device for streaming"),
                )
                usbStream?.close()
                usbStream = stream
                val route = AudioEngineRouter.resolvePlaybackRoute(probe, stream, wavChannels)
                    ?: return@withContext Result.failure(
                        IllegalStateException("No audio playback route available"),
                    )
                player.play(viewModelScope, File(path), route, usbDevice = device)
            }
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        transportState = TransportState.PLAYING,
                        statusMessage = "Playing $path",
                    )
                }
            }.onFailure { e ->
                usbStream?.close()
                usbStream = null
                OmtLog.e("ViewModel", "playLastRecording failed", e)
                _uiState.update { it.copy(statusMessage = e.message) }
            }
        }
    }

    fun stopPlayback() {
        OmtLog.i("ViewModel", "stopPlayback")
        player.stop()
        usbStream?.close()
        usbStream = null
        _uiState.update {
            it.copy(transportState = TransportState.IDLE, statusMessage = "Playback stopped")
        }
    }

    override fun onCleared() {
        OmtLog.d("ViewModel", "onCleared")
        kotlinx.coroutines.runBlocking {
            runCatching { recorder.stop() }
                .onFailure { OmtLog.w("ViewModel", "recorder.stop on clear failed", it) }
        }
        player.stop()
        usbStream?.close()
        usbStream = null
        super.onCleared()
    }

    private fun openStream(descriptor: UsbAudioDeviceDescriptor): UsbAudioStreamHandle? {
        val device = enumerator.getUsbDevice(descriptor.deviceName) ?: return null
        return UsbAudioStreamHandle.open(appContext, usbManager, device)
    }

    private fun buildProbeMessage(result: FullUsbProbeResult, inputChannels: Int?): String {
        val uac2In = result.uac2Caps?.maxCaptureChannels ?: 0
        if (inputChannels != null && inputChannels > 0) {
            val oboeIn = result.input?.takeIf { it.isSuccess }?.channelCount ?: 0
            if (uac2In > oboeIn && oboeIn > 0) {
                return "Oboe: $oboeIn ch. USB descriptor: $uac2In ch capture (UAC2 host available)."
            }
            return "Ready to record $inputChannels input channel(s)."
        }
        if (uac2In > 0) {
            val uac2Out = result.uac2Caps?.maxPlaybackChannels ?: 0
            return "USB descriptor: $uac2In ch in / $uac2Out ch out. ${result.note ?: ""}".trim()
        }
        return result.note ?: "Probe complete."
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val enumerator = UsbAudioEnumerator(appContext)
                    val probeService = UsbAudioProbeService(enumerator)
                    return MainViewModel(appContext, enumerator, probeService) as T
                }
            }
    }
}
