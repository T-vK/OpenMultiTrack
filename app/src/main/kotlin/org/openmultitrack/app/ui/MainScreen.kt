package org.openmultitrack.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "OpenMultiTrack", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "v${org.openmultitrack.app.BuildConfig.VERSION_NAME} — USB probe, 2ch record, playback",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRefresh, enabled = !state.isRefreshing) {
            Text(stringResource(R.string.refresh_devices))
        }
        if (state.isRefreshing) CircularProgressIndicator()
        state.statusMessage?.let { Text(text = it) }
        state.lastRecordingPath?.let { Text("Last session: $it", style = MaterialTheme.typography.bodySmall) }
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
                    canPlay = state.lastRecordingPath != null && state.transportState != TransportState.PLAYING,
                    onRequestPermission = onRequestPermission,
                    onProbe = onProbe,
                    onStartRecord = onStartRecord,
                    onStopRecord = onStopRecord,
                    onPlay = onPlay,
                    onStopPlayback = onStopPlayback,
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    row: DeviceRow,
    canPlay: Boolean,
    onRequestPermission: (UsbAudioDeviceDescriptor) -> Unit,
    onProbe: (UsbAudioDeviceDescriptor) -> Unit,
    onStartRecord: (UsbAudioDeviceDescriptor) -> Unit,
    onStopRecord: () -> Unit,
    onPlay: (UsbAudioDeviceDescriptor) -> Unit,
    onStopPlayback: () -> Unit,
) {
    val d = row.descriptor
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
                        Button(onClick = { onStartRecord(d) }) {
                            Text(stringResource(R.string.start_record))
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
            }
            if (row.probing) CircularProgressIndicator()
            row.probe?.let { probe ->
                probe.input?.let { ProbeLine("Input", it.channelCount, it.sampleRate, it.errorMessage) }
                probe.output?.let { ProbeLine("Output", it.channelCount, it.sampleRate, it.errorMessage) }
            }
        }
    }
}

@Composable
private fun ProbeLine(label: String, channels: Int, sampleRate: Int, error: String?) {
    if (error != null) {
        Text("$label probe error: $error", color = MaterialTheme.colorScheme.error)
    } else {
        Text("$label: $channels ch @ $sampleRate Hz")
    }
}
