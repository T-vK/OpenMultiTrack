package org.openmultitrack.app.ui

import android.media.AudioDeviceInfo
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.DeviceRow
import org.openmultitrack.app.MainUiState
import org.openmultitrack.app.R
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.session.TransportState

@Composable
fun MainScreen(
    state: MainUiState,
    onRefresh: () -> Unit,
    onRequestPermission: (UsbAudioDeviceDescriptor) -> Unit,
    onProbe: (UsbAudioDeviceDescriptor) -> Unit,
    onStartRecord: (UsbAudioDeviceDescriptor) -> Unit,
    onStopRecord: () -> Unit,
    onPlay: (UsbAudioDeviceDescriptor) -> Unit,
    onStopPlayback: () -> Unit,
    onStartMonitor: (UsbAudioDeviceDescriptor) -> Unit,
    onStopMonitor: () -> Unit,
    onToggleMonitorChannel: (Int) -> Unit,
    onSelectMonitorOutput: (Int) -> Unit,
    onEnableVirtualMic: (UsbAudioDeviceDescriptor) -> Unit,
    onDisableVirtualMic: () -> Unit,
    onToggleVirtualMicChannel: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "OpenMultiTrack", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "v${org.openmultitrack.app.BuildConfig.VERSION_NAME} — multichannel USB record, monitor & virtual mic",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRefresh, enabled = !state.isRefreshing) {
            Text(stringResource(R.string.refresh_devices))
        }
        if (state.isRefreshing) CircularProgressIndicator()
        state.statusMessage?.let { Text(text = it) }
        state.lastRecordingPath?.let { Text("Last session: $it", style = MaterialTheme.typography.bodySmall) }
        if (state.isMonitoring) {
            Text("Live monitor active (${state.monitorChannels.size} ch)", color = MaterialTheme.colorScheme.primary)
        }
        if (state.isVirtualMicActive) {
            Text("Virtual mic active", color = MaterialTheme.colorScheme.primary)
            state.virtualMicStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (state.devices.isEmpty() && !state.isRefreshing) {
            Text(stringResource(R.string.no_usb_devices))
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.devices, key = { it.descriptor.deviceName }) { row ->
                DeviceCard(
                    row = row,
                    state = state,
                    canPlay = state.lastRecordingPath != null && state.transportState != TransportState.PLAYING,
                    onRequestPermission = onRequestPermission,
                    onProbe = onProbe,
                    onStartRecord = onStartRecord,
                    onStopRecord = onStopRecord,
                    onPlay = onPlay,
                    onStopPlayback = onStopPlayback,
                    onStartMonitor = onStartMonitor,
                    onStopMonitor = onStopMonitor,
                    onToggleMonitorChannel = onToggleMonitorChannel,
                    onSelectMonitorOutput = onSelectMonitorOutput,
                    onEnableVirtualMic = onEnableVirtualMic,
                    onDisableVirtualMic = onDisableVirtualMic,
                    onToggleVirtualMicChannel = onToggleVirtualMicChannel,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(
    row: DeviceRow,
    state: MainUiState,
    canPlay: Boolean,
    onRequestPermission: (UsbAudioDeviceDescriptor) -> Unit,
    onProbe: (UsbAudioDeviceDescriptor) -> Unit,
    onStartRecord: (UsbAudioDeviceDescriptor) -> Unit,
    onStopRecord: () -> Unit,
    onPlay: (UsbAudioDeviceDescriptor) -> Unit,
    onStopPlayback: () -> Unit,
    onStartMonitor: (UsbAudioDeviceDescriptor) -> Unit,
    onStopMonitor: () -> Unit,
    onToggleMonitorChannel: (Int) -> Unit,
    onSelectMonitorOutput: (Int) -> Unit,
    onEnableVirtualMic: (UsbAudioDeviceDescriptor) -> Unit,
    onDisableVirtualMic: () -> Unit,
    onToggleVirtualMicChannel: (Int) -> Unit,
) {
    val d = row.descriptor
    val channelCount = state.captureChannelCount.takeIf { it > 0 }
        ?: row.recordChannelCount
        ?: 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = d.productName ?: d.deviceName, style = MaterialTheme.typography.titleMedium)
            Text("VID:PID ${"%04X".format(d.vendorId)}:${"%04X".format(d.productId)}")
            d.guessedModel?.let { Text("Guessed model: $it") }
            Text("USB permission: ${if (row.hasPermission) "granted" else "required"}")
            d.androidAudioDeviceId?.let { Text("AAudio device id: $it") }
            if (!row.hasPermission) {
                Button(onClick = { onRequestPermission(d) }) {
                    Text(stringResource(R.string.usb_permission_required))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onProbe(d) }, enabled = !row.probing) {
                        Text(stringResource(R.string.probe_channels))
                    }
                    if (row.isRecording) {
                        Button(onClick = onStopRecord) {
                            Text(stringResource(R.string.stop_record))
                        }
                    } else {
                        Button(
                            onClick = { onStartRecord(d) },
                            enabled = row.recordChannelCount != null && row.recordChannelCount > 0,
                        ) {
                            Text(
                                row.recordChannelCount?.let { ch ->
                                    stringResource(R.string.start_record_channels, ch)
                                } ?: stringResource(R.string.start_record_probe_first),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPlay(d) }, enabled = canPlay) {
                        Text(stringResource(R.string.play_soundcheck))
                    }
                    Button(onClick = onStopPlayback) {
                        Text(stringResource(R.string.stop_playback))
                    }
                }

                if (channelCount > 0) {
                    Text(stringResource(R.string.live_monitor), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.monitor_channels_hint), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (ch in 0 until channelCount) {
                            FilterChip(
                                selected = state.monitorChannels.contains(ch),
                                onClick = { onToggleMonitorChannel(ch) },
                                label = { Text("${ch + 1}") },
                            )
                        }
                    }
                    if (state.outputDevices.isNotEmpty()) {
                        Text(stringResource(R.string.monitor_output), style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.outputDevices.forEach { device ->
                                FilterChip(
                                    selected = state.monitorOutputDeviceId == device.id,
                                    onClick = { onSelectMonitorOutput(device.id) },
                                    label = { Text(audioDeviceLabel(device)) },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.isMonitoring) {
                            Button(onClick = onStopMonitor) {
                                Text(stringResource(R.string.stop_monitor))
                            }
                        } else {
                            Button(
                                onClick = { onStartMonitor(d) },
                                enabled = row.probe != null && state.monitorChannels.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.start_monitor))
                            }
                        }
                    }
                }

                if (state.rootAvailable && channelCount > 0) {
                    Text(stringResource(R.string.virtual_mic_root), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.virtual_mic_hint), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (ch in 0 until minOf(channelCount, 18)) {
                            FilterChip(
                                selected = state.virtualMicChannels.contains(ch),
                                onClick = { onToggleVirtualMicChannel(ch) },
                                label = { Text("${ch + 1}") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.isVirtualMicActive) {
                            Button(onClick = onDisableVirtualMic) {
                                Text(stringResource(R.string.disable_virtual_mic))
                            }
                        } else {
                            Button(
                                onClick = { onEnableVirtualMic(d) },
                                enabled = row.probe != null && state.virtualMicChannels.isNotEmpty(),
                            ) {
                                Text(stringResource(R.string.enable_virtual_mic))
                            }
                        }
                    }
                }
            }
            if (row.probing) CircularProgressIndicator()
            row.probe?.let { probe ->
                probe.input?.let { ProbeLine("Input", it.channelCount, it.sampleRate, it.errorMessage) }
                probe.output?.let { ProbeLine("Output", it.channelCount, it.sampleRate, it.errorMessage) }
            }
        }
    }
}

private fun audioDeviceLabel(device: AudioDeviceInfo): String {
    val product = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        device.productName?.toString()
    } else {
        null
    }
    return product?.takeIf { it.isNotBlank() } ?: "Device ${device.id}"
}

@Composable
private fun ProbeLine(label: String, channels: Int, sampleRate: Int, error: String?) {
    if (error != null) {
        Text("$label probe error: $error", color = MaterialTheme.colorScheme.error)
    } else {
        Text("$label: $channels ch @ $sampleRate Hz")
    }
}
