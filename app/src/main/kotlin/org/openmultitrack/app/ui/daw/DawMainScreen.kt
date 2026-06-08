package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmultitrack.app.DawUiState
import org.openmultitrack.app.scribble.Flow8BleScribbleImporter
import org.openmultitrack.app.scribble.ScribbleImportSupport
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.app.ui.settings.SettingsSheet
import org.openmultitrack.app.ui.settings.SettingsUiState
import org.openmultitrack.app.audio.LiveWaveformSnapshot
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
private val ToolbarShape = RoundedCornerShape(8.dp)
private val ToolbarControlHeight = 36.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DawMainScreen(
    state: DawUiState,
    monitorGain: Float,
    recordWaveformWindowSec: Float,
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
    onLoadScribbleStrip: (String) -> Unit,
    playbackWaveformWindowSec: Float,
    onSelectSoundcheckSession: (String, String) -> Unit,
    onToggleSoundcheckPlayback: (String) -> Unit,
    onStopSoundcheck: (String) -> Unit,
    onSeekSoundcheck: (String, Float) -> Unit,
    onPanSoundcheckView: (String, Float) -> Unit,
    onZoomSoundcheckView: (String, Float, Float) -> Unit,
    onSetSoundcheckLoopRegion: (String, Float, Float) -> Unit,
    onToggleSoundcheckLoop: (String) -> Unit,
    onConfirmFlow8PairingImport: () -> Unit,
    onDismissFlow8PairingDialog: () -> Unit,
    onHideArmChange: (Boolean) -> Unit,
    onHideMonitorChange: (Boolean) -> Unit,
    onHideSoloChange: (Boolean) -> Unit,
    onShowWaveformsChange: (Boolean) -> Unit,
    onStripNumberModeChange: (StripNumberMode) -> Unit,
    onStripIconModeChange: (StripIconMode) -> Unit,
    onRecordWaveformWindowChange: (Float) -> Unit,
    onPlaybackWaveformWindowChange: (Float) -> Unit,
    onDismissStatusToast: () -> Unit,
    onFinalizeIncompleteRecording: (String) -> Unit,
) {
    val activeId = state.activeMixerId
    val session = activeId?.let { state.sessionByMixer[it] }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "OpenMultiTrack",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
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
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                if (state.mixers.isNotEmpty() && session != null && activeId != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
                if (state.mixers.isNotEmpty() && session != null && activeId != null) {
                    val activeProfile = state.mixers.firstOrNull { it.id == activeId }
                    TransportToolbar(
                        mixers = state.mixers,
                        activeMixerId = activeId,
                        session = session,
                        outputDevices = state.outputDevices,
                        showLoadScribble = activeProfile != null && ScribbleImportSupport.supports(activeProfile),
                        onSelectMixer = onSelectMixer,
                        onRemoveMixer = onRemoveMixer,
                        onSetAppMode = onSetAppMode,
                        onLoadScribbleStrip = { onLoadScribbleStrip(activeId) },
                        onSetMonitorOutput = { onSetMonitorOutput(activeId, it) },
                        onStartMonitor = { onStartMonitor(activeId) },
                        onStopMonitor = { onStopMonitor(activeId) },
                        onStartRecord = { onStartRecord(activeId) },
                        onStopRecord = { onStopRecord(activeId) },
                        onToggleSoundcheckPlayback = { onToggleSoundcheckPlayback(activeId) },
                        onStopSoundcheck = { onStopSoundcheck(activeId) },
                        onToggleSoundcheckLoop = { onToggleSoundcheckLoop(activeId) },
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
                    EmptyMixersPrompt(onAddMixer = onAddMixer)
                } else {
                    if (state.pendingRecordingResume) {
                        IncompleteRecordingBanner(
                            message = "Interrupted recording detected — resuming automatically. " +
                                "Silence is inserted for any time not captured.",
                            onFinalize = activeId?.let { id -> { onFinalizeIncompleteRecording(id) } },
                        )
                    }
                    session?.warningMessage?.let { WarningBanner(it) }
                    when (session?.appMode) {
                        AppMode.VIRTUAL_SOUNDCHECK -> session?.let { s ->
                            SoundcheckPanel(
                                session = s,
                                normalized = waveformNormalized,
                                showWaveforms = state.showWaveforms,
                                numberMode = state.stripNumberMode,
                                iconMode = state.stripIconMode,
                                hideArm = state.hideArmButton,
                                hideMonitor = state.hideMonitorButton,
                                hideSolo = state.hideSoloButton,
                                onSelectSession = { onSelectSoundcheckSession(activeId!!, it) },
                                onSeek = { onSeekSoundcheck(activeId!!, it) },
                                onPanView = { onPanSoundcheckView(activeId!!, it) },
                                onZoomView = { scale, focal -> onZoomSoundcheckView(activeId!!, scale, focal) },
                                onSetLoopRegion = { start, end ->
                                    onSetSoundcheckLoopRegion(activeId!!, start, end)
                                },
                            )
                        }
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
                recordWaveformWindowSec = recordWaveformWindowSec,
                playbackWaveformWindowSec = playbackWaveformWindowSec,
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
            onRecordWaveformWindowChange = onRecordWaveformWindowChange,
            onPlaybackWaveformWindowChange = onPlaybackWaveformWindowChange,
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
    showLoadScribble: Boolean,
    onSelectMixer: (String) -> Unit,
    onRemoveMixer: (String) -> Unit,
    onSetAppMode: (String, AppMode) -> Unit,
    onLoadScribbleStrip: () -> Unit,
    onSetMonitorOutput: (Int) -> Unit,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onToggleSoundcheckPlayback: () -> Unit,
    onStopSoundcheck: () -> Unit,
    onToggleSoundcheckLoop: () -> Unit,
) {
    var mixerMenuOpen by remember { mutableStateOf(false) }
    var removeMixerConfirm by remember { mutableStateOf<MixerProfile?>(null) }
    var monitorDeviceMenuOpen by remember { mutableStateOf(false) }
    val activeMixer = mixers.firstOrNull { it.id == activeMixerId }

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
        val showLabels = maxWidth >= 640.dp
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    MixerSelectorChip(
                        label = activeMixer?.displayName ?: "Mixer",
                        showLabel = showLabels,
                        onClick = { mixerMenuOpen = true },
                    )
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

                AppModeToggle(
                    appMode = session.appMode,
                    showLabel = showLabels,
                    onToggle = {
                        val next = if (session.appMode == AppMode.MULTITRACK_RECORD) {
                            AppMode.VIRTUAL_SOUNDCHECK
                        } else {
                            AppMode.MULTITRACK_RECORD
                        }
                        onSetAppMode(activeMixerId, next)
                    },
                )

                if (showLoadScribble) {
                    LoadScribbleStripButton(
                        showLabel = showLabels,
                        onClick = onLoadScribbleStrip,
                    )
                }

                Spacer(Modifier.weight(1f))

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

                if (session.appMode == AppMode.VIRTUAL_SOUNDCHECK) {
                    SoundcheckLoopControl(
                        session = session,
                        showLabels = showLabels,
                        onToggleLoop = onToggleSoundcheckLoop,
                    )
                    SoundcheckPlayControl(
                        session = session,
                        showLabels = showLabels,
                        onTogglePlay = onToggleSoundcheckPlayback,
                        onStop = onStopSoundcheck,
                    )
                } else {
                    RecordControl(
                        session = session,
                        showLabels = showLabels,
                        onStartRecord = onStartRecord,
                        onStopRecord = onStopRecord,
                    )
                }

                if (session.transportState == TransportState.RECORDING_DEGRADED) {
                    TransportWarningChip()
                }
            }
        }
    }
}

@Composable
private fun MixerSelectorChip(
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    ToolbarChip(onClick = onClick) {
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadScribbleStripButton(
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    ToolbarChip(onClick = onClick) {
        Icon(
            Icons.Default.Label,
            contentDescription = "Load scribble strip from mixer",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(
                "Load strip labels",
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun AppModeToggle(
    appMode: AppMode,
    showLabel: Boolean,
    onToggle: () -> Unit,
) {
    val isRecordingMode = appMode == AppMode.MULTITRACK_RECORD
    val icon = if (isRecordingMode) Icons.Default.Mic else Icons.Default.VolumeUp
    val label = if (isRecordingMode) "Recording mode" else "Virtual soundcheck"
    ToolbarChip(
        onClick = onToggle,
        selected = true,
        accent = if (isRecordingMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isRecordingMode) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
        )
        if (showLabel) {
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun TransportWarningChip() {
    Surface(
        shape = ToolbarShape,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, RecordRed.copy(alpha = 0.35f)),
    ) {
        Text(
            "USB",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun ToolbarChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable RowScope.() -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (selected) 0.45f else 0.3f)
    val background = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(ToolbarControlHeight),
        shape = ToolbarShape,
        color = background,
        border = BorderStroke(1.dp, borderColor),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun ToolbarActionButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val background = when {
        active -> activeColor
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val foreground = when {
        active -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(ToolbarControlHeight)
            .widthIn(min = ToolbarControlHeight),
        shape = ToolbarShape,
        color = background,
        border = BorderStroke(
            1.dp,
            if (active) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
        contentColor = foreground,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
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
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    Row(
        modifier = Modifier
            .height(ToolbarControlHeight)
            .clip(ToolbarShape)
            .border(BorderStroke(1.dp, borderColor), ToolbarShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clickable(enabled = enabled) {
                    if (session.isMonitoring) onStopMonitor() else onStartMonitor()
                }
                .padding(horizontal = if (showLabels) 10.dp else 8.dp)
                .height(ToolbarControlHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Headphones,
                contentDescription = "Monitor",
                modifier = Modifier.size(18.dp),
                tint = if (session.isMonitoring) MonitorBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showLabels) {
                Spacer(Modifier.width(6.dp))
                Text(
                    if (session.isMonitoring) "Monitoring" else "Monitor",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (session.isMonitoring) MonitorBlue else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        VerticalDivider(
            modifier = Modifier.height(22.dp),
            color = borderColor,
        )
        Box {
            Row(
                modifier = Modifier
                    .clickable(enabled = outputDevices.isNotEmpty()) {
                        onMonitorDeviceMenuOpen(true)
                    }
                    .padding(horizontal = 8.dp)
                    .height(ToolbarControlHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Monitor output",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun SoundcheckLoopControl(
    session: MixerSessionUiState,
    showLabels: Boolean,
    onToggleLoop: () -> Unit,
) {
    val hasRegion = session.soundcheckLoopStartSec != null && session.soundcheckLoopEndSec != null
    val active = session.soundcheckLoopSelecting || (hasRegion && session.soundcheckLoopEnabled)
    ToolbarActionButton(
        onClick = onToggleLoop,
        active = active,
        activeColor = if (session.soundcheckLoopEnabled) SoloAmber else MonitorBlue,
        enabled = session.selectedSoundcheckDir != null,
    ) {
        Icon(Icons.Default.Repeat, contentDescription = "Loop", modifier = Modifier.size(18.dp))
        if (showLabels) {
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    session.soundcheckLoopSelecting -> "Set loop"
                    hasRegion && session.soundcheckLoopEnabled -> "Loop on"
                    hasRegion -> "Loop off"
                    else -> "Loop"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SoundcheckPlayControl(
    session: MixerSessionUiState,
    showLabels: Boolean,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
) {
    val enabled = session.selectedSoundcheckDir != null && session.probe != null
    if (session.isPlaying) {
        ToolbarActionButton(
            onClick = onStop,
            active = true,
            activeColor = MonitorBlue,
        ) {
            Icon(Icons.Default.Stop, contentDescription = "Stop playback", modifier = Modifier.size(18.dp))
            if (showLabels) {
                Spacer(Modifier.width(6.dp))
                Text("Stop", style = MaterialTheme.typography.labelLarge)
            }
        }
    } else {
        ToolbarChip(
            onClick = onTogglePlay,
            enabled = enabled,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play to USB returns",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            if (showLabels) {
                Spacer(Modifier.width(6.dp))
                Text("Play", style = MaterialTheme.typography.labelLarge)
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
        ToolbarActionButton(
            onClick = onStopRecord,
            active = true,
            activeColor = RecordRed,
        ) {
            Icon(Icons.Default.Stop, contentDescription = "Stop recording", modifier = Modifier.size(18.dp))
            if (showLabels) {
                Spacer(Modifier.width(6.dp))
                Text("Stop", style = MaterialTheme.typography.labelLarge)
            }
        }
    } else {
        ToolbarChip(
            onClick = onStartRecord,
            enabled = enabled,
        ) {
            Icon(
                Icons.Filled.FiberManualRecord,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = RecordRed,
            )
            if (showLabels) {
                Spacer(Modifier.width(6.dp))
                Text("Record", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EmptyMixersPrompt(onAddMixer: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Add an audio interface to begin", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Plug in a mixer, then tap Add to scan USB.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAddMixer) { Text("Add mixer") }
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
private fun IncompleteRecordingBanner(
    message: String,
    onFinalize: (() -> Unit)?,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            onFinalize?.let { finalize ->
                TextButton(onClick = finalize) {
                    Text("Finalize")
                }
            }
        }
    }
}

@Composable
private fun ChannelStripList(
    strips: List<ChannelStripState>,
    normalized: Boolean,
    waveformPeaks: Map<Int, LiveWaveformSnapshot>,
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
                    waveform = if (showWaveforms) waveformPeaks[strip.index] else null,
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
    waveform: LiveWaveformSnapshot?,
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
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
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
                waveform = waveform,
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
    waveform: LiveWaveformSnapshot?,
    color: Color,
    normalized: Boolean,
    modifier: Modifier = Modifier,
) {
    val data = waveform?.peaks
    val capacity = waveform?.capacity ?: 0
    if (data == null || data.isEmpty() || capacity <= 0) {
        Box(modifier = modifier)
        return
    }
    val displayPeaks = org.openmultitrack.app.ui.daw.scalePeaksForDisplay(data, normalized)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val mid = h / 2f
        val slotWidth = w / capacity
        val stroke = (slotWidth * 0.75f).coerceIn(1f, 4f)
        val minBar = h * 0.06f
        displayPeaks.forEachIndexed { i, peak ->
            val amp = peak.coerceIn(0f, 1f)
            val barH = maxOf(amp * h * 0.9f, if (amp > 0.02f) minBar else 0f)
            val x = (i + 0.5f) * slotWidth
            drawLine(
                color = color.copy(alpha = 0.9f),
                start = Offset(x, mid - barH / 2f),
                end = Offset(x, mid + barH / 2f),
                strokeWidth = stroke,
            )
        }
    }
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
