package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.DawUiState
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.usb.LabeledAudioDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DawMainScreen(
    state: DawUiState,
    monitorGain: Float,
    waveformNormalized: Boolean,
    onAddMixer: () -> Unit,
    onSelectMixer: (String) -> Unit,
    onAddMixerDevice: (UsbAudioDeviceDescriptor) -> Unit,
    onDismissAddMixer: () -> Unit,
    onToggleArm: (String, Int) -> Unit,
    onToggleMonitor: (String, Int) -> Unit,
    onToggleSolo: (String, Int) -> Unit,
    onSetMonitorOutput: (String, Int) -> Unit,
    onStartMonitor: (String) -> Unit,
    onStopMonitor: (String) -> Unit,
    onStartRecord: (String) -> Unit,
    onStopRecord: (String) -> Unit,
    onSetAppMode: (String, AppMode) -> Unit,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onOpenLog: () -> Unit,
    onCloseLog: () -> Unit,
    onMonitorGainChange: (Float) -> Unit,
    onRefreshUsb: () -> Unit,
) {
    val activeId = state.activeMixerId
    val session = activeId?.let { state.sessionByMixer[it] }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OpenMultiTrack", fontWeight = FontWeight.Bold)
                        session?.mixerProfile?.displayName?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onAddMixer) {
                        Icon(Icons.Default.Add, contentDescription = "Add mixer")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
        bottomBar = {
            if (activeId != null && session != null) {
                TransportBar(
                    session = session,
                    onStartRecord = { onStartRecord(activeId) },
                    onStopRecord = { onStopRecord(activeId) },
                    onStartMonitor = { onStartMonitor(activeId) },
                    onStopMonitor = { onStopMonitor(activeId) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.mixers.isEmpty()) {
                EmptyMixersPrompt(onAddMixer = onAddMixer, onRefresh = onRefreshUsb)
            } else {
                MixerTabs(
                    mixers = state.mixers,
                    activeId = activeId,
                    onSelect = onSelectMixer,
                )
                session?.warningMessage?.let { msg ->
                    WarningBanner(msg)
                }
                session?.let { s ->
                    ModeToggle(
                        mode = s.appMode,
                        onMode = { onSetAppMode(s.mixerId, it) },
                    )
                    if (s.appMode == AppMode.MULTITRACK_RECORD) {
                        MonitorOutputPicker(
                            devices = state.outputDevices,
                            selectedId = s.monitorOutputDeviceId,
                            onSelect = { onSetMonitorOutput(s.mixerId, it) },
                        )
                    }
                    ChannelStripList(
                        strips = s.channelStrips,
                        normalized = waveformNormalized,
                        onArm = { onToggleArm(s.mixerId, it) },
                        onMonitor = { onToggleMonitor(s.mixerId, it) },
                        onSolo = { onToggleSolo(s.mixerId, it) },
                    )
                }
            }
        }
    }

    if (state.showAddMixerDialog) {
        AddMixerDialog(
            devices = state.availableUsbDevices,
            onAdd = onAddMixerDevice,
            onDismiss = onDismissAddMixer,
        )
    }

    if (menuOpen) {
        AlertDialog(
            onDismissRequest = { menuOpen = false },
            title = { Text("Menu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { menuOpen = false; onOpenSettings() }) { Text("Settings") }
                    TextButton(onClick = { menuOpen = false; onOpenLog() }) { Text("Log viewer") }
                    TextButton(onClick = { menuOpen = false; onRefreshUsb() }) { Text("Refresh USB") }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuOpen = false }) { Text("Close") }
            },
        )
    }

    if (state.showSettings) {
        ModalBottomSheet(onDismissRequest = onCloseSettings) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                Text("Monitor gain (Android)")
                Slider(
                    value = monitorGain,
                    onValueChange = onMonitorGainChange,
                    valueRange = 0.5f..8f,
                )
                Text("Gain: ${"%.1f".format(monitorGain)}×")
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (state.showLogViewer) {
        ModalBottomSheet(onDismissRequest = onCloseLog) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Debug log", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { AppLogBuffer.clear() }) { Text("Clear") }
                }
                Text(
                    AppLogBuffer.allText().ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.height(320.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyMixersPrompt(onAddMixer: () -> Unit, onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Add an audio interface to begin", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddMixer) { Text("Add mixer") }
        TextButton(onClick = onRefresh) { Text("Scan USB") }
    }
}

@Composable
private fun MixerTabs(mixers: List<MixerProfile>, activeId: String?, onSelect: (String) -> Unit) {
    val index = mixers.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
    TabRow(selectedTabIndex = index) {
        mixers.forEachIndexed { i, m ->
            Tab(
                selected = i == index,
                onClick = { onSelect(m.id) },
                text = { Text(m.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun WarningBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ModeToggle(mode: AppMode, onMode: (AppMode) -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == AppMode.MULTITRACK_RECORD,
            onClick = { onMode(AppMode.MULTITRACK_RECORD) },
            label = { Text("Record") },
        )
        FilterChip(
            selected = mode == AppMode.VIRTUAL_SOUNDCHECK,
            onClick = { onMode(AppMode.VIRTUAL_SOUNDCHECK) },
            label = { Text("Soundcheck") },
        )
    }
}

@Composable
private fun MonitorOutputPicker(
    devices: List<LabeledAudioDevice>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text("Monitor output", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            devices.take(6).forEach { labeled ->
                FilterChip(
                    selected = labeled.device.id == selectedId,
                    onClick = { onSelect(labeled.device.id) },
                    label = { Text(labeled.shortLabel, maxLines = 1) },
                )
            }
        }
    }
}

@Composable
private fun ChannelStripList(
    strips: List<ChannelStripState>,
    normalized: Boolean,
    onArm: (Int) -> Unit,
    onMonitor: (Int) -> Unit,
    onSolo: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
    ) {
        items(strips, key = { it.index }) { strip ->
            ChannelStripRow(strip, normalized, onArm, onMonitor, onSolo)
        }
    }
}

@Composable
private fun ChannelStripRow(
    strip: ChannelStripState,
    normalized: Boolean,
    onArm: (Int) -> Unit,
    onMonitor: (Int) -> Unit,
    onSolo: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(strip.colorArgb)),
            )
            Text(
                "${strip.index + 1}",
                modifier = Modifier.width(24.dp),
                fontWeight = FontWeight.Bold,
            )
            StripButton("R", strip.armed, MaterialTheme.colorScheme.error) { onArm(strip.index) }
            StripButton("M", strip.monitoring, MaterialTheme.colorScheme.primary) { onMonitor(strip.index) }
            StripButton("S", strip.solo, MaterialTheme.colorScheme.tertiary) { onSolo(strip.index) }
            WaveformPlaceholder(
                modifier = Modifier.weight(1f).height(48.dp),
                color = Color(strip.colorArgb),
            )
        }
    }
}

@Composable
private fun StripButton(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(36.dp).height(36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) activeColor else MaterialTheme.colorScheme.surface,
            contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WaveformPlaceholder(modifier: Modifier, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Text(
            "〜",
            modifier = Modifier.align(Alignment.Center),
            color = color.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun TransportBar(
    session: MixerSessionUiState,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                session.isRecording -> Button(onClick = onStopRecord) { Text("Stop") }
                else -> Button(onClick = onStartRecord, enabled = session.probe != null) { Text("Record") }
            }
            when {
                session.isMonitoring -> Button(onClick = onStopMonitor) { Text("Stop monitor") }
                else -> Button(onClick = onStartMonitor, enabled = session.probe != null) { Text("Monitor") }
            }
            Text(
                when (session.transportState) {
                    TransportState.RECORDING -> "REC"
                    TransportState.RECORDING_DEGRADED -> "REC ⚠"
                    TransportState.PLAYING -> "PLAY"
                    else -> ""
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AddMixerDialog(
    devices: List<UsbAudioDeviceDescriptor>,
    onAdd: (UsbAudioDeviceDescriptor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add audio interface") },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text("No USB devices found.")
                } else {
                    devices.forEach { d ->
                        TextButton(onClick = { onAdd(d) }) {
                            Text("${d.productName ?: d.deviceName} (${d.guessedModel ?: "USB"})")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
