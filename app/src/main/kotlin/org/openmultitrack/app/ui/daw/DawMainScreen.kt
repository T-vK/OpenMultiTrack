package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmultitrack.app.DawUiState
import org.openmultitrack.app.scribble.Flow8BleScribbleImporter
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.app.ui.settings.SettingsSheet
import org.openmultitrack.app.ui.settings.SettingsUiState
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.usb.LabeledAudioDevice

private val RecordRed = Color(0xFFE53935)
private val MonitorBlue = Color(0xFF1E88E5)
private val SoloAmber = Color(0xFFFFB300)
private val MinStripHeight = 44.dp
private val ScrollStripHeight = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DawMainScreen(
    state: DawUiState,
    monitorGain: Float,
    waveformNormalized: Boolean,
    onAddMixer: () -> Unit,
    onSelectMixer: (String) -> Unit,
    onRemoveMixer: (String) -> Unit,
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
    onRefreshScribble: (String) -> Unit,
    onConfirmFlow8PairingImport: () -> Unit,
    onDismissFlow8PairingDialog: () -> Unit,
    onHideArmChange: (Boolean) -> Unit,
    onHideMonitorChange: (Boolean) -> Unit,
    onHideSoloChange: (Boolean) -> Unit,
    onShowWaveformsChange: (Boolean) -> Unit,
    onStripNumberModeChange: (StripNumberMode) -> Unit,
    onStripIconModeChange: (StripIconMode) -> Unit,
    onDismissStatusToast: () -> Unit,
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
                        onRemoveMixer = onRemoveMixer,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.mixers.isEmpty()) {
                    EmptyMixersPrompt(onAddMixer = onAddMixer, onRefresh = onRefreshUsb)
                } else {
                    if (state.pendingRecordingResume) {
                        WarningBanner("Incomplete recording found — scribble import paused until you resume or finalize.")
                    }
                    session?.warningMessage?.let { WarningBanner(it) }
                    when (session?.appMode) {
                        AppMode.VIRTUAL_SOUNDCHECK -> SoundcheckPlaceholder()
                        else -> session?.let { s ->
                            ChannelStripList(
                                strips = s.channelStrips,
                                normalized = waveformNormalized,
                                waveformPeaks = s.waveformPeaks,
                                showWaveforms = state.showWaveforms,
                                hideArm = state.hideArmButton,
                                hideMonitor = state.hideMonitorButton,
                                hideSolo = state.hideSoloButton,
                                numberMode = state.stripNumberMode,
                                iconMode = state.stripIconMode,
                                onArm = { onToggleArm(s.mixerId, it) },
                                onMonitor = { onToggleMonitor(s.mixerId, it) },
                                onSolo = { onToggleSolo(s.mixerId, it) },
                            )
                        }
                    }
                }
            }
            StatusToastHost(
                toast = state.statusToast,
                onDismiss = onDismissStatusToast,
            )
        }
    }

    if (state.showAddMixerDialog) {
        AddMixerDialog(
            devices = state.addableUsbDevices,
            alreadyAddedCount = state.mixers.size,
            onAdd = onAddMixerDevice,
            onDismiss = onDismissAddMixer,
        )
    }

    state.flow8PairingDialog?.let {
        AlertDialog(
            onDismissRequest = onDismissFlow8PairingDialog,
            title = { Text(Flow8BleScribbleImporter.PAIRING_DIALOG_TITLE) },
            text = { Text(Flow8BleScribbleImporter.PAIRING_DIALOG_MESSAGE) },
            confirmButton = {
                Button(onClick = onConfirmFlow8PairingImport) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissFlow8PairingDialog) {
                    Text("Cancel")
                }
            },
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
                    activeId?.let { id ->
                        TextButton(onClick = { menuOpen = false; onRefreshScribble(id) }) {
                            Text("Import scribble strip")
                        }
                    }
                    TextButton(onClick = { menuOpen = false; onRefreshUsb() }) { Text("Refresh USB") }
                }
            },
            confirmButton = { TextButton(onClick = { menuOpen = false }) { Text("Close") } },
        )
    }

    if (state.showSettings) {
        SettingsSheet(
            state = SettingsUiState(
                hideArmIcon = state.hideArmButton,
                hideMonitorIcon = state.hideMonitorButton,
                hideSoloIcon = state.hideSoloButton,
                showWaveforms = state.showWaveforms,
                stripNumberMode = state.stripNumberMode,
                stripIconMode = state.stripIconMode,
            ),
            monitorGain = monitorGain,
            onMonitorGainChange = onMonitorGainChange,
            onDismiss = onCloseSettings,
            onHideArmChange = onHideArmChange,
            onHideMonitorChange = onHideMonitorChange,
            onHideSoloChange = onHideSoloChange,
            onShowWaveformsChange = onShowWaveformsChange,
            onStripNumberModeChange = onStripNumberModeChange,
            onStripIconModeChange = onStripIconModeChange,
        )
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
    onRemoveMixer: (String) -> Unit,
    onSetAppMode: (String, AppMode) -> Unit,
    onSetMonitorOutput: (Int) -> Unit,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
) {
    var mixerMenuOpen by remember { mutableStateOf(false) }
    var removeMixerConfirm by remember { mutableStateOf<MixerProfile?>(null) }
    var monitorDeviceMenuOpen by remember { mutableStateOf(false) }
    val activeMixer = mixers.firstOrNull { it.id == activeMixerId }
    val selectedOutput = outputDevices.firstOrNull { it.device.id == session.monitorOutputDeviceId }

    removeMixerConfirm?.let { mixer ->
        AlertDialog(
            onDismissRequest = { removeMixerConfirm = null },
            title = { Text("Remove mixer?") },
            text = {
                Text("Remove ${mixer.displayName} from this app? USB recording settings for this device will be cleared.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeMixerConfirm = null
                        onRemoveMixer(mixer.id)
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeMixerConfirm = null }) { Text("Cancel") }
            },
        )
    }

    BoxWithConstraints {
        val showLabels = maxWidth >= 720.dp
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box {
                        TextButton(onClick = { mixerMenuOpen = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    activeMixer?.displayName ?: "Mixer",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 100.dp),
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
                            if (activeMixer != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Remove ${activeMixer.displayName}",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        mixerMenuOpen = false
                                        removeMixerConfirm = activeMixer
                                    },
                                )
                            }
                        }
                    }

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        SegmentedButton(
                            selected = session.appMode == AppMode.MULTITRACK_RECORD,
                            onClick = { onSetAppMode(activeMixerId, AppMode.MULTITRACK_RECORD) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { SegmentedButtonDefaults.Icon(active = session.appMode == AppMode.MULTITRACK_RECORD) { Icon(Icons.Default.Mic, null, Modifier.size(14.dp)) } },
                            label = { Text("Record", fontSize = 11.sp, maxLines = 1) },
                        )
                        SegmentedButton(
                            selected = session.appMode == AppMode.VIRTUAL_SOUNDCHECK,
                            onClick = { onSetAppMode(activeMixerId, AppMode.VIRTUAL_SOUNDCHECK) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { SegmentedButtonDefaults.Icon(active = session.appMode == AppMode.VIRTUAL_SOUNDCHECK) { Icon(Icons.Default.VolumeUp, null, Modifier.size(14.dp)) } },
                            label = { Text("Soundcheck", fontSize = 11.sp, maxLines = 1) },
                        )
                    }

                    MonitorControl(
                        session = session,
                        outputDevices = outputDevices,
                        showLabels = showLabels,
                        monitorDeviceMenuOpen = monitorDeviceMenuOpen,
                        onMonitorDeviceMenuOpen = { monitorDeviceMenuOpen = it },
                        onStartMonitor = onStartMonitor,
                        onStopMonitor = onStopMonitor,
                        onSetMonitorOutput = onSetMonitorOutput,
                    )

                    RecordControl(
                        session = session,
                        showLabels = showLabels,
                        onStartRecord = onStartRecord,
                        onStopRecord = onStopRecord,
                    )

                    when (session.transportState) {
                        TransportState.RECORDING_DEGRADED -> Text("⚠", color = RecordRed, fontWeight = FontWeight.Bold)
                        else -> Unit
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
    }
}

@Composable
private fun MonitorControl(
    session: MixerSessionUiState,
    outputDevices: List<LabeledAudioDevice>,
    showLabels: Boolean,
    monitorDeviceMenuOpen: Boolean,
    onMonitorDeviceMenuOpen: (Boolean) -> Unit,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
    onSetMonitorOutput: (Int) -> Unit,
) {
    val enabled = session.probe != null && session.appMode == AppMode.MULTITRACK_RECORD
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showLabels) {
            FilledTonalButton(
                onClick = { if (session.isMonitoring) onStopMonitor() else onStartMonitor() },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (session.isMonitoring) MonitorBlue else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (session.isMonitoring) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Default.Headphones, contentDescription = "Monitor", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Monitor", fontSize = 12.sp)
            }
        } else {
            Surface(
                onClick = { if (session.isMonitoring) onStopMonitor() else onStartMonitor() },
                enabled = enabled,
                shape = CircleShape,
                color = if (session.isMonitoring) MonitorBlue else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = "Monitor",
                        tint = if (session.isMonitoring) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Box {
            IconButton(
                onClick = { onMonitorDeviceMenuOpen(true) },
                modifier = Modifier.size(32.dp),
                enabled = outputDevices.isNotEmpty(),
            ) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Monitor output")
            }
            DropdownMenu(expanded = monitorDeviceMenuOpen, onDismissRequest = { onMonitorDeviceMenuOpen(false) }) {
                outputDevices.forEach { labeled ->
                    DropdownMenuItem(
                        text = { Text(labeled.label, maxLines = 2, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onMonitorDeviceMenuOpen(false)
                            onSetMonitorOutput(labeled.device.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordControl(
    session: MixerSessionUiState,
    showLabels: Boolean,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
) {
    val enabled = session.probe != null && session.appMode == AppMode.MULTITRACK_RECORD
    if (session.isRecording) {
        Button(
            onClick = onStopRecord,
            contentPadding = PaddingValues(horizontal = if (showLabels) 12.dp else 8.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RecordRed, contentColor = Color.White),
        ) {
            Icon(Icons.Default.Stop, contentDescription = "Stop recording", modifier = Modifier.size(18.dp))
            if (showLabels) {
                Spacer(Modifier.width(4.dp))
                Text("Stop", fontSize = 12.sp)
            }
        }
    } else {
        FilledTonalButton(
            onClick = onStartRecord,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = if (showLabels) 12.dp else 8.dp, vertical = 6.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = RecordRed,
            ),
        ) {
            Icon(Icons.Filled.FiberManualRecord, contentDescription = "Record", tint = RecordRed, modifier = Modifier.size(18.dp))
            if (showLabels) {
                Spacer(Modifier.width(4.dp))
                Text("Record", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
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
    waveformPeaks: Map<Int, FloatArray>,
    showWaveforms: Boolean,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    onArm: (Int) -> Unit,
    onMonitor: (Int) -> Unit,
    onSolo: (Int) -> Unit,
) {
    var overlayIndex by remember { mutableStateOf<Int?>(null) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        val count = strips.size.coerceAtLeast(1)
        val gap = 2.dp
        val totalGaps = gap * (count - 1).coerceAtLeast(0)
        val naturalHeight = (maxHeight - totalGaps) / count
        val useScroll = naturalHeight < MinStripHeight
        val stripHeight = if (useScroll) ScrollStripHeight else naturalHeight
        val innerPad = (stripHeight * 0.12f).coerceIn(3.dp, 10.dp)
        val labelFontSize = (stripHeight.value * 0.30f).coerceIn(10f, 16f)
        val labelColumnWidth = stripLabelColumnWidth(
            strips,
            numberMode,
            iconMode,
            labelFontSize,
            stripHeight,
            hideArm,
            hideMonitor,
            hideSolo,
        )

        if (strips.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No channels yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Allow USB access if prompted. The app will reconnect automatically when the mixer is plugged in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            return@BoxWithConstraints
        }

        val listModifier = if (useScroll) {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxSize()
        }
        Column(
            modifier = listModifier,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            strips.forEach { strip ->
                ChannelStripRow(
                    strip = strip,
                    stripHeight = stripHeight,
                    innerPad = innerPad,
                    labelFontSize = labelFontSize,
                    labelColumnWidth = labelColumnWidth,
                    peaks = if (showWaveforms) waveformPeaks[strip.index] else null,
                    normalized = normalized,
                    showWaveform = showWaveforms,
                    numberMode = numberMode,
                    iconMode = iconMode,
                    hideArm = hideArm,
                    hideMonitor = hideMonitor,
                    hideSolo = hideSolo,
                    onOpenControls = { overlayIndex = strip.index },
                )
            }
        }
    }

    overlayIndex?.let { index ->
        val strip = strips.firstOrNull { it.index == index } ?: return@let
        ChannelStripControlDialog(
            strip = strip,
            hideArm = false,
            hideMonitor = false,
            hideSolo = false,
            onDismiss = { overlayIndex = null },
            onArm = { onArm(strip.index) },
            onMonitor = { onMonitor(strip.index) },
            onSolo = { onSolo(strip.index) },
        )
    }
}

@Composable
private fun ChannelStripRow(
    strip: ChannelStripState,
    stripHeight: Dp,
    innerPad: Dp,
    labelFontSize: Float,
    labelColumnWidth: Dp,
    peaks: FloatArray?,
    normalized: Boolean,
    showWaveform: Boolean,
    numberMode: StripNumberMode,
    iconMode: StripIconMode,
    hideArm: Boolean,
    hideMonitor: Boolean,
    hideSolo: Boolean,
    onOpenControls: () -> Unit,
) {
    val colorBarHeight = stripHeight - innerPad * 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(stripHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = innerPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(innerPad / 2),
    ) {
        StripIdentityCell(
            strip = strip,
            columnWidth = labelColumnWidth,
            labelFontSize = labelFontSize,
            numberMode = numberMode,
            iconMode = iconMode,
            colorBarHeight = colorBarHeight,
            hideArm = hideArm,
            hideMonitor = hideMonitor,
            hideSolo = hideSolo,
            onClick = onOpenControls,
        )
        if (showWaveform) {
            WaveformView(
                peaks = peaks,
                color = Color(strip.colorArgb),
                normalized = normalized,
                modifier = Modifier
                    .weight(1f)
                    .height(colorBarHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun WaveformView(
    peaks: FloatArray?,
    color: Color,
    normalized: Boolean,
    modifier: Modifier = Modifier,
) {
    val data = peaks
    if (data == null || data.isEmpty()) {
        Box(modifier = modifier)
        return
    }
    val displayPeaks = scalePeaksForDisplay(data, normalized)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val mid = h / 2f
        val step = w / displayPeaks.size.coerceAtLeast(1)
        val stroke = (step * 0.75f).coerceIn(1f, 4f)
        val minBar = h * 0.06f
        displayPeaks.forEachIndexed { i, peak ->
            val amp = peak.coerceIn(0f, 1f)
            val barH = maxOf(amp * h * 0.9f, if (amp > 0.02f) minBar else 0f)
            drawLine(
                color = color.copy(alpha = 0.9f),
                start = Offset(i * step + step / 2f, mid - barH / 2f),
                end = Offset(i * step + step / 2f, mid + barH / 2f),
                strokeWidth = stroke,
            )
        }
    }
}

/** USB peaks are often very small floats; scale for display so live waveforms stay visible. */
private fun scalePeaksForDisplay(peaks: FloatArray, normalized: Boolean): FloatArray {
    if (peaks.isEmpty()) return peaks
    val maxPeak = peaks.max()
    if (maxPeak <= 1e-6f) return peaks
    if (normalized) {
        return FloatArray(peaks.size) { i -> (peaks[i] / maxPeak).coerceIn(0f, 1f) }
    }
    val gain = if (maxPeak < 0.15f) 1f / maxPeak else 1f
    return FloatArray(peaks.size) { i -> (peaks[i] * gain).coerceIn(0f, 1f) }
}

@Composable
private fun AddMixerDialog(
    devices: List<UsbAudioDeviceDescriptor>,
    alreadyAddedCount: Int,
    onAdd: (UsbAudioDeviceDescriptor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add audio interface") },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text(
                        if (alreadyAddedCount > 0) {
                            "All connected USB mixers are already added."
                        } else {
                            "No USB devices found."
                        },
                    )
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
