package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
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
            Column {
                TopAppBar(
                    title = { Text("OpenMultiTrack", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
                if (state.mixers.isNotEmpty() && session != null && activeId != null) {
                    TransportToolbar(
                        mixers = state.mixers,
                        activeMixerId = activeId,
                        session = session,
                        outputDevices = state.outputDevices,
                        onSelectMixer = onSelectMixer,
                        onSetAppMode = onSetAppMode,
                        onSetMonitorOutput = { onSetMonitorOutput(activeId, it) },
                        onStartMonitor = { onStartMonitor(activeId) },
                        onStopMonitor = { onStopMonitor(activeId) },
                        onStartRecord = { onStartRecord(activeId) },
                        onStopRecord = { onStopRecord(activeId) },
                    )
                }
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
                session?.warningMessage?.let { WarningBanner(it) }
                session?.statusMessage?.let { msg ->
                    if (session.warningMessage == null) {
                        Text(
                            msg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when (session?.appMode) {
                    AppMode.VIRTUAL_SOUNDCHECK -> SoundcheckPlaceholder()
                    else -> session?.let { s ->
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { menuOpen = false; onOpenSettings() }) { Text("Settings") }
                    TextButton(onClick = { menuOpen = false; onOpenLog() }) { Text("Log viewer") }
                    TextButton(onClick = { menuOpen = false; onRefreshUsb() }) { Text("Refresh USB") }
                }
            },
            confirmButton = { TextButton(onClick = { menuOpen = false }) { Text("Close") } },
        )
    }

    if (state.showSettings) {
        ModalBottomSheet(onDismissRequest = onCloseSettings) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Settings", style = MaterialTheme.typography.titleLarge)
                Text("Monitor gain")
                Slider(value = monitorGain, onValueChange = onMonitorGainChange, valueRange = 0.5f..8f)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransportToolbar(
    mixers: List<MixerProfile>,
    activeMixerId: String,
    session: MixerSessionUiState,
    outputDevices: List<LabeledAudioDevice>,
    onSelectMixer: (String) -> Unit,
    onSetAppMode: (String, AppMode) -> Unit,
    onSetMonitorOutput: (Int) -> Unit,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
) {
    var mixerMenuOpen by remember { mutableStateOf(false) }
    var monitorDeviceMenuOpen by remember { mutableStateOf(false) }
    val activeMixer = mixers.firstOrNull { it.id == activeMixerId }
    val selectedOutput = outputDevices.firstOrNull { it.device.id == session.monitorOutputDeviceId }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Mixer dropdown
            Box {
                TextButton(onClick = { mixerMenuOpen = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            activeMixer?.displayName ?: "Mixer",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(88.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                DropdownMenu(expanded = mixerMenuOpen, onDismissRequest = { mixerMenuOpen = false }) {
                    mixers.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.displayName) },
                            onClick = {
                                mixerMenuOpen = false
                                onSelectMixer(m.id)
                            },
                        )
                    }
                }
            }

            // Record | Soundcheck segmented toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f, fill = false)) {
                SegmentedButton(
                    selected = session.appMode == AppMode.MULTITRACK_RECORD,
                    onClick = { onSetAppMode(activeMixerId, AppMode.MULTITRACK_RECORD) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("Record", fontSize = 11.sp) },
                )
                SegmentedButton(
                    selected = session.appMode == AppMode.VIRTUAL_SOUNDCHECK,
                    onClick = { onSetAppMode(activeMixerId, AppMode.VIRTUAL_SOUNDCHECK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("Soundcheck", fontSize = 11.sp) },
                )
            }

            // Monitor toggle + output dropdown
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconToggleButton(
                    checked = session.isMonitoring,
                    onCheckedChange = { checked ->
                        if (checked) onStartMonitor() else onStopMonitor()
                    },
                    enabled = session.probe != null && session.appMode == AppMode.MULTITRACK_RECORD,
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = "Monitor")
                }
                Box {
                    IconButton(
                        onClick = { monitorDeviceMenuOpen = true },
                        modifier = Modifier.size(32.dp),
                        enabled = outputDevices.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Monitor output")
                    }
                    DropdownMenu(
                        expanded = monitorDeviceMenuOpen,
                        onDismissRequest = { monitorDeviceMenuOpen = false },
                    ) {
                        outputDevices.forEach { labeled ->
                            DropdownMenuItem(
                                text = {
                                    Text(labeled.label, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                },
                                onClick = {
                                    monitorDeviceMenuOpen = false
                                    onSetMonitorOutput(labeled.device.id)
                                },
                            )
                        }
                    }
                }
            }

            // Record transport
            if (session.isRecording) {
                FilledIconToggleButton(
                    checked = true,
                    onCheckedChange = { onStopRecord() },
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop recording", tint = Color.White)
                }
            } else {
                IconButton(
                    onClick = onStartRecord,
                    enabled = session.probe != null && session.appMode == AppMode.MULTITRACK_RECORD,
                ) {
                    Icon(
                        Icons.Filled.FiberManualRecord,
                        contentDescription = "Record",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // Transport indicator
            val indicator = when (session.transportState) {
                TransportState.RECORDING -> "●"
                TransportState.RECORDING_DEGRADED -> "⚠"
                TransportState.PLAYING -> "▶"
                else -> null
            }
            indicator?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
        selectedOutput?.let {
            Text(
                "Monitor: ${it.shortLabel}",
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SoundcheckPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Soundcheck library — coming soon", style = MaterialTheme.typography.bodyLarge)
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
        TextButton(onClick = onAddMixer) { Text("Add mixer") }
        TextButton(onClick = onRefresh) { Text("Scan USB") }
    }
}

@Composable
private fun WarningBanner(message: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            message,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
        )
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
    ) {
        items(strips, key = { it.index }) { strip ->
            ChannelStripRow(strip, onArm, onMonitor, onSolo)
        }
    }
}

@Composable
private fun ChannelStripRow(
    strip: ChannelStripState,
    onArm: (Int) -> Unit,
    onMonitor: (Int) -> Unit,
    onSolo: (Int) -> Unit,
) {
    val stripHeight = 36.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(strip.colorArgb)),
        )
        Text(
            "${strip.index + 1}",
            modifier = Modifier.width(20.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        StripIconToggle(
            checked = strip.armed,
            checkedIcon = Icons.Filled.FiberManualRecord,
            uncheckedIcon = Icons.Filled.FiberManualRecord,
            tint = MaterialTheme.colorScheme.error,
            contentDescription = "Arm",
            onClick = { onArm(strip.index) },
        )
        StripIconToggle(
            checked = strip.monitoring,
            checkedIcon = Icons.Filled.Headphones,
            uncheckedIcon = Icons.Filled.Headphones,
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = "Monitor",
            onClick = { onMonitor(strip.index) },
        )
        StripIconToggle(
            checked = strip.solo,
            checkedIcon = Icons.Filled.VolumeUp,
            uncheckedIcon = Icons.Filled.VolumeUp,
            tint = MaterialTheme.colorScheme.tertiary,
            contentDescription = "Solo",
            onClick = { onSolo(strip.index) },
        )
        WaveformPlaceholder(
            modifier = Modifier.weight(1f).height(28.dp),
            color = Color(strip.colorArgb),
        )
    }
}

@Composable
private fun StripIconToggle(
    checked: Boolean,
    checkedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    uncheckedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = Modifier.size(30.dp),
    ) {
        Icon(
            imageVector = if (checked) checkedIcon else uncheckedIcon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = if (checked) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaveformPlaceholder(modifier: Modifier, color: Color) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Text("〜", modifier = Modifier.align(Alignment.Center), fontSize = 10.sp, color = color.copy(alpha = 0.5f))
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
