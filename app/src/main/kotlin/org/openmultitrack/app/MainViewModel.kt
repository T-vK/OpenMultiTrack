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
import org.openmultitrack.domain.audio.RecordingChannels
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

    private var activeRecordingDevice: String? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun refreshDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val rows = withContext(Dispatchers.IO) {
                enumerator.listUsbDevices().map { usb ->
                    val existing = _uiState.value.devices.firstOrNull { it.descriptor.deviceName == usb.deviceName }
                    DeviceRow(
                        descriptor = usb,
                        hasPermission = enumerator.hasUsbPermission(usb.deviceName),
                        isRecording = usb.deviceName == activeRecordingDevice,
                        probe = existing?.probe,
                        recordChannelCount = existing?.probe?.input?.channelCount?.takeIf { it > 0 },
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
            val inputChannels = result.input?.takeIf { it.isSuccess }?.channelCount
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
        val resolved = RecordingChannels.fromInputProbe(row?.probe?.input).getOrElse { error ->
            _uiState.update { it.copy(statusMessage = error.message) }
            return
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                recorder.start(
                    scope = viewModelScope,
                    deviceId = resolved.deviceId,
                    channels = resolved.channelCount,
                    outputDir = sessionsDir,
                    sampleRateHz = resolved.sampleRate,
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
                _uiState.update { it.copy(statusMessage = error.message) }
            }
        }
    }

    fun stopRecording() {
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { recorder.stop() }
            val dropped = recorder.droppedFrameCount()
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
        val outputProbe = _uiState.value.devices
            .firstOrNull { it.descriptor.deviceName == descriptor.deviceName }
            ?.probe?.output
        val deviceId = outputProbe?.takeIf { it.isSuccess }?.deviceId
            ?: enumerator.findAndroidAudioDeviceId(descriptor, input = false)
        if (deviceId == null) {
            _uiState.update { it.copy(statusMessage = "Probe output channels before playback.") }
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

    private fun buildProbeMessage(result: FullUsbProbeResult, inputChannels: Int?): String {
        if (inputChannels != null && inputChannels > 0) {
            return "Ready to record $inputChannels input channel(s)."
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
