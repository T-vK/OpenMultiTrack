package org.openmultitrack.app

import android.content.Context
import android.media.AudioDeviceInfo
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
import org.openmultitrack.app.service.AudioSessionClient
import org.openmultitrack.app.service.AudioSessionUiState
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService

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
    val isMonitoring: Boolean = false,
    val isVirtualMicActive: Boolean = false,
    val monitorChannels: Set<Int> = emptySet(),
    val monitorOutputDeviceId: Int = -1,
    val virtualMicChannels: Set<Int> = emptySet(),
    val virtualMicStereo: Boolean = false,
    val rootAvailable: Boolean = false,
    val virtualMicStatus: String? = null,
    val outputDevices: List<AudioDeviceInfo> = emptyList(),
    val captureChannelCount: Int = 0,
)

class MainViewModel(
    private val appContext: Context,
    private val enumerator: UsbAudioEnumerator,
    private val probeService: UsbAudioProbeService,
    private val sessionClient: AudioSessionClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        sessionClient.bind()
        sessionClient.whenReady { controller ->
            viewModelScope.launch {
                controller.state.collect { session ->
                    mergeSessionState(session)
                }
            }
            controller.refreshRootStatus()
        }
        refreshOutputDevices()
    }

    fun refreshDevices() {
        viewModelScope.launch {
            OmtLog.d("ViewModel", "refreshDevices")
            _uiState.update { it.copy(isRefreshing = true) }
            val activeDevice = sessionClient.state?.value?.activeDeviceName
            val rows = withContext(Dispatchers.IO) {
                enumerator.listUsbDevices().map { usb ->
                    val existing = _uiState.value.devices.firstOrNull { it.descriptor.deviceName == usb.deviceName }
                    DeviceRow(
                        descriptor = usb,
                        hasPermission = enumerator.hasUsbPermission(usb.deviceName),
                        isRecording = usb.deviceName == activeDevice &&
                            sessionClient.state?.value?.isRecording == true,
                        probe = existing?.probe,
                        recordChannelCount = existing?.probe?.input?.channelCount?.takeIf { it > 0 }
                            ?: existing?.probe?.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 },
                    )
                }
            }
            _uiState.update {
                it.copy(devices = rows, isRefreshing = false)
            }
        }
    }

    fun refreshOutputDevices() {
        viewModelScope.launch {
            val outputs = withContext(Dispatchers.IO) { enumerator.listAudioOutputDevices() }
            val defaultId = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }?.id ?: outputs.firstOrNull()?.id ?: -1
            _uiState.update {
                it.copy(
                    outputDevices = outputs,
                    monitorOutputDeviceId = if (it.monitorOutputDeviceId < 0) defaultId else it.monitorOutputDeviceId,
                )
            }
        }
    }

    fun onUsbPermissionGranted(deviceName: String) {
        OmtLog.i("ViewModel", "onUsbPermissionGranted deviceName=$deviceName")
        refreshDevices()
        val row = _uiState.value.devices.firstOrNull { it.descriptor.deviceName == deviceName }
        if (row != null) probeDevice(row.descriptor)
    }

    fun onUsbDetached(deviceName: String?) {
        sessionClient.withController { it.onUsbDetached(deviceName) }
        refreshDevices()
    }

    fun probeDevice(descriptor: UsbAudioDeviceDescriptor) {
        OmtLog.i("ViewModel", "probeDevice ${descriptor.productName}")
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
            _uiState.update { state ->
                val allMonitorChannels = if (inputChannels != null && state.monitorChannels.isEmpty()) {
                    (0 until inputChannels).toSet()
                } else {
                    state.monitorChannels
                }
                state.copy(
                    devices = state.devices.map { row ->
                        if (row.descriptor.deviceName == descriptor.deviceName) {
                            row.copy(probing = false, probe = result, recordChannelCount = inputChannels)
                        } else {
                            row
                        }
                    },
                    statusMessage = buildProbeMessage(result, inputChannels),
                    captureChannelCount = inputChannels ?: state.captureChannelCount,
                    monitorChannels = allMonitorChannels,
                )
            }
        }
    }

    fun startRecording(descriptor: UsbAudioDeviceDescriptor) {
        val probe = probeFor(descriptor) ?: return
        sessionClient.promoteForeground("Recording")
        sessionClient.withController { it.startRecording(descriptor, probe) }
    }

    fun stopRecording() {
        sessionClient.withController { it.stopRecording() }
    }

    fun startMonitoring(descriptor: UsbAudioDeviceDescriptor) {
        val probe = probeFor(descriptor) ?: return
        val channels = _uiState.value.monitorChannels.ifEmpty {
            val count = probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
                ?: probe.input?.takeIf { it.isSuccess }?.channelCount
                ?: 0
            (0 until count).toSet()
        }
        val outputId = _uiState.value.monitorOutputDeviceId
        sessionClient.promoteForeground("Live monitor")
        sessionClient.withController {
            it.startMonitoring(descriptor, probe, channels, outputId)
        }
    }

    fun stopMonitoring() {
        sessionClient.withController { it.stopMonitoring() }
    }

    fun toggleMonitorChannel(channel: Int) {
        val updated = _uiState.value.monitorChannels.toMutableSet().apply {
            if (contains(channel)) remove(channel) else add(channel)
        }
        _uiState.update { it.copy(monitorChannels = updated) }
        sessionClient.withController { it.setMonitorChannels(updated) }
    }

    fun setMonitorOutputDevice(deviceId: Int) {
        _uiState.update { it.copy(monitorOutputDeviceId = deviceId) }
        sessionClient.withController { it.setMonitorOutputDevice(deviceId) }
    }

    fun enableVirtualMic(descriptor: UsbAudioDeviceDescriptor) {
        val probe = probeFor(descriptor) ?: return
        val channels = _uiState.value.virtualMicChannels
        if (channels.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Select 1–2 channels for virtual mic.") }
            return
        }
        sessionClient.promoteForeground("Virtual microphone")
        sessionClient.withController {
            it.enableVirtualMic(descriptor, probe, channels, _uiState.value.virtualMicStereo)
        }
    }

    fun disableVirtualMic() {
        sessionClient.withController { it.disableVirtualMic() }
    }

    fun toggleVirtualMicChannel(channel: Int, maxChannels: Int = 2) {
        val updated = _uiState.value.virtualMicChannels.toMutableSet()
        if (updated.contains(channel)) {
            updated.remove(channel)
        } else if (updated.size < maxChannels) {
            updated.add(channel)
        }
        val stereo = updated.size >= 2
        _uiState.update { it.copy(virtualMicChannels = updated, virtualMicStereo = stereo) }
        sessionClient.withController { it.setVirtualMicChannels(updated, stereo) }
    }

    fun playLastRecording(descriptor: UsbAudioDeviceDescriptor) {
        val probe = probeFor(descriptor) ?: return
        sessionClient.promoteForeground("Playback")
        sessionClient.withController { it.playLastRecording(descriptor, probe) }
    }

    fun stopPlayback() {
        sessionClient.withController { it.stopPlayback() }
    }

    override fun onCleared() {
        sessionClient.unbind()
        super.onCleared()
    }

    private fun probeFor(descriptor: UsbAudioDeviceDescriptor): FullUsbProbeResult? {
        val probe = _uiState.value.devices
            .firstOrNull { it.descriptor.deviceName == descriptor.deviceName }
            ?.probe
        if (probe == null) {
            _uiState.update { it.copy(statusMessage = "Probe device first.") }
        }
        return probe
    }

    private fun mergeSessionState(session: AudioSessionUiState) {
        _uiState.update { ui ->
            ui.copy(
                statusMessage = session.statusMessage ?: ui.statusMessage,
                lastRecordingPath = session.lastRecordingPath ?: ui.lastRecordingPath,
                transportState = session.transportState,
                isMonitoring = session.isMonitoring,
                isVirtualMicActive = session.isVirtualMicActive,
                monitorChannels = session.monitorChannels.ifEmpty { ui.monitorChannels },
                monitorOutputDeviceId = if (session.monitorOutputDeviceId >= 0) {
                    session.monitorOutputDeviceId
                } else {
                    ui.monitorOutputDeviceId
                },
                virtualMicChannels = session.virtualMicChannels.ifEmpty { ui.virtualMicChannels },
                virtualMicStereo = session.virtualMicStereo,
                rootAvailable = session.rootAvailable,
                virtualMicStatus = session.virtualMicStatus,
                captureChannelCount = session.captureChannelCount.takeIf { it > 0 } ?: ui.captureChannelCount,
                devices = ui.devices.map { row ->
                    row.copy(isRecording = row.descriptor.deviceName == session.activeDeviceName && session.isRecording)
                },
            )
        }
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
                    val sessionClient = AudioSessionClient(appContext)
                    return MainViewModel(appContext, enumerator, probeService, sessionClient) as T
                }
            }
    }
}
