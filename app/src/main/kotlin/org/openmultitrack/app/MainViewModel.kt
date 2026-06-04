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
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService

data class DeviceRow(
    val descriptor: UsbAudioDeviceDescriptor,
    val hasPermission: Boolean,
    val probe: FullUsbProbeResult? = null,
    val probing: Boolean = false,
)

data class MainUiState(
    val devices: List<DeviceRow> = emptyList(),
    val statusMessage: String? = null,
    val isRefreshing: Boolean = false,
)

class MainViewModel(
    private val enumerator: UsbAudioEnumerator,
    private val probeService: UsbAudioProbeService,
) : ViewModel() {
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
        if (row != null) {
            probeDevice(row.descriptor)
        }
    }

    fun probeDevice(descriptor: UsbAudioDeviceDescriptor) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    devices = state.devices.map { row ->
                        if (row.descriptor.deviceName == descriptor.deviceName) {
                            row.copy(probing = true)
                        } else {
                            row
                        }
                    },
                )
            }
            val result = withContext(Dispatchers.IO) {
                probeService.probe(descriptor)
            }
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

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val enumerator = UsbAudioEnumerator(context.applicationContext)
                    val probeService = UsbAudioProbeService(enumerator)
                    return MainViewModel(enumerator, probeService) as T
                }
            }
    }
}
