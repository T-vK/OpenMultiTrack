package org.openmultitrack.app

import android.content.Context
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
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService
import java.io.File

data class DeviceRow(
    val descriptor: UsbAudioDeviceDescriptor,
    val hasPermission: Boolean,
    val probe: FullUsbProbeResult? = null,
    val probing: Boolean = false,
    val isRecording: Boolean = false,
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

    private var activeRecordingDevice: String? = null
    private var selectedDeviceId: Int? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun refreshDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val rows = withContext(Dispatchers.IO) {
                enumerator.listUsbDevices().map { usb ->
                    DeviceRow(
                        descriptor = usb,
                        hasPermission = enumerator.hasUsbPermission(usb.deviceName),
                        isRecording = usb.deviceName == activeRecordingDevice,
                    )
                }
            }
            _uiState.update {
                it.copy(devices = rows, isRefreshing = false, statusMessage = null)
            }
        }
    }

    fun onUsbPermissionGranted(deviceName: String) {
        refreshDevices()
        val row = _uiState.value.devices.firstOrNull { it.descriptor.deviceName == deviceName }
        if (row != null) probeDevice(row.descriptor)
    }

    fun probeDevice(descriptor: UsbAudioDeviceDescriptor) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    devices = state.devices.map { row ->
                        if (row.descriptor.deviceName == descriptor.deviceName) row.copy(probing = true) else row
                    },
                )
            }
            val result = withContext(Dispatchers.IO) { probeService.probe(descriptor) }
            selectedDeviceId = result.input?.deviceId ?: result.output?.deviceId
            _uiState.update { state ->
                state.copy(
                    devices = state.devices.map { row ->
                        if (row.descriptor.deviceName == descriptor.deviceName) {
                            row.copy(probing = false, probe = result)
                        } else {
                            row
                        }
                    },
                    statusMessage = result.note,
                )
            }
        }
    }

    fun startRecording(descriptor: UsbAudioDeviceDescriptor) {
        val deviceId = selectedDeviceId
            ?: descriptor.androidAudioDeviceId
            ?: enumerator.findAndroidAudioDeviceId(descriptor, input = true)
        if (deviceId == null) {
            _uiState.update { it.copy(statusMessage = "Probe channels before recording.") }
            return
        }
        val channels = 2
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                recorder.start(viewModelScope, deviceId, channels, sessionsDir)
            }
            result.onSuccess {
                activeRecordingDevice = descriptor.deviceName
                _uiState.update { state ->
                    state.copy(
                        statusMessage = "Recording ${channels}ch → ${it.filePath}",
                        devices = state.devices.map { row ->
                            row.copy(isRecording = row.descriptor.deviceName == descriptor.deviceName)
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(statusMessage = error.message) }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { recorder.stop() }
            activeRecordingDevice = null
            _uiState.update { state ->
                state.copy(
                    lastRecordingPath = session?.filePath,
                    statusMessage = session?.let {
                        "Saved ${it.framesRecorded} frames → ${it.filePath}"
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
        val deviceId = selectedDeviceId
            ?: enumerator.findAndroidAudioDeviceId(descriptor, input = false)
        if (deviceId == null) {
            _uiState.update { it.copy(statusMessage = "No output device id for playback.") }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                player.play(viewModelScope, File(path), deviceId)
            }
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        transportState = TransportState.PLAYING,
                        statusMessage = "Playing $path",
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(statusMessage = e.message) }
            }
        }
    }

    fun stopPlayback() {
        player.stop()
        _uiState.update {
            it.copy(transportState = TransportState.IDLE, statusMessage = "Playback stopped")
        }
    }

    override fun onCleared() {
        kotlinx.coroutines.runBlocking {
            runCatching { recorder.stop() }
        }
        player.stop()
        super.onCleared()
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
