package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.displayLabel
import org.openmultitrack.domain.session.isPlaybackMode

private val ToolbarShape = RoundedCornerShape(8.dp)
private val ToolbarControlHeight = 40.dp
private val BothTransportsMinWidth = 420.dp
private val MixerNameWideMaxWidth = 160.dp
private val MixerNameMediumMaxWidth = 112.dp
private val MixerNameNarrowMaxWidth = 80.dp

private fun AppMode.toolbarIcon(): ImageVector = when (this) {
    AppMode.MULTITRACK_RECORD -> Icons.Default.FiberManualRecord
    AppMode.VIRTUAL_SOUNDCHECK -> Icons.Default.GraphicEq
    AppMode.SIMPLE_PLAY -> Icons.Default.PlayCircle
}

private val RecordRed = Color(0xFFE53935)
private val LoopAmber = Color(0xFFFFB300)

@Composable
internal fun ToolbarChip(
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable RowScope.() -> Unit,
) {
    val border = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }
    Surface(
        onClick = onClick ?: {},
        enabled = enabled && onClick != null,
        shape = ToolbarShape,
        color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = border,
        modifier = Modifier.height(ToolbarControlHeight),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun MixerPickerButton(
    activeMixerName: String?,
    mixerNameMaxWidth: Dp,
    onOpenMixerPicker: () -> Unit,
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    Surface(
        onClick = onOpenMixerPicker,
        shape = ToolbarShape,
        border = border,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.height(ToolbarControlHeight),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                activeMixerName ?: "Select mixer",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = mixerNameMaxWidth),
                style = MaterialTheme.typography.labelLarge,
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TransportButtonCluster(
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = ToolbarShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.height(ToolbarControlHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun TransportIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    background: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = background,
        modifier = Modifier.size(ToolbarControlHeight),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun RecordTransportCluster(
    session: MixerSessionUiState?,
    isRemoteClient: Boolean,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
) {
    val isRecording = session?.isRecording == true
    val hostReady = session?.probe != null || (isRemoteClient && session?.captureChannelCount?.let { it > 0 } == true)
    val canRecord = hostReady && !isRecording
    val elapsed = session?.recordElapsedSec ?: 0f
    TransportButtonCluster {
        TransportIconButton(
            onClick = onStartRecord,
            enabled = canRecord,
            icon = Icons.Default.FiberManualRecord,
            contentDescription = "Record",
            tint = RecordRed,
        )
        clusterDivider()
        TransportIconButton(
            onClick = onStopRecord,
            enabled = isRecording,
            icon = Icons.Default.Stop,
            contentDescription = "Stop recording",
            tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            background = if (isRecording) RecordRed else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        )
        clusterDivider()
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            modifier = Modifier.height(ToolbarControlHeight),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isRecording) formatAdaptiveTransportTime(elapsed) else "0:00",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRecording) RecordRed else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlaybackTransportCluster(
    session: MixerSessionUiState?,
    isRemoteClient: Boolean,
    onTogglePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    onToggleLoop: () -> Unit,
    onSetLoopIn: () -> Unit,
    onSetLoopOut: () -> Unit,
) {
    val isPlaying = session?.isPlaying == true
    val hostReady = session?.probe != null ||
        (isRemoteClient && session?.captureChannelCount?.let { it > 0 } == true)
    val hasSession = session?.selectedSoundcheckDir != null && hostReady
    val canStop = hasSession && (isPlaying || (session?.playbackPositionSec ?: 0f) > 0.05f)
    TransportButtonCluster {
        TransportIconButton(
            onClick = onTogglePlayback,
            enabled = hasSession,
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.primary,
            background = if (isPlaying) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            },
        )
        clusterDivider()
        TransportIconButton(
            onClick = onStopPlayback,
            enabled = canStop,
            icon = Icons.Default.Stop,
            contentDescription = "Stop playback",
            tint = MaterialTheme.colorScheme.onSurface,
        )
        clusterDivider()
        LoopTransportButton(
            session = session,
            onSetLoopIn = onSetLoopIn,
            onSetLoopOut = onSetLoopOut,
            onToggleLoop = onToggleLoop,
        )
    }
}

@Composable
private fun LoopTransportButton(
    session: MixerSessionUiState?,
    onSetLoopIn: () -> Unit,
    onSetLoopOut: () -> Unit,
    onToggleLoop: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSession = session?.selectedSoundcheckDir != null
    val hasRegion = session?.soundcheckLoopStartSec != null && session.soundcheckLoopEndSec != null
    val loopTint = when {
        session?.soundcheckLoopEnabled == true -> LoopAmber
        session?.soundcheckLoopSelecting == true -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box {
        TransportIconButton(
            onClick = { expanded = true },
            enabled = hasSession,
            icon = Icons.Default.Repeat,
            contentDescription = "Loop",
            tint = loopTint,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Set loop in point") },
                onClick = {
                    expanded = false
                    onSetLoopIn()
                },
                enabled = hasSession,
            )
            DropdownMenuItem(
                text = { Text("Set loop out point") },
                onClick = {
                    expanded = false
                    onSetLoopOut()
                },
                enabled = hasSession,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        when {
                            session?.soundcheckLoopSelecting == true -> "Cancel loop selection"
                            session?.soundcheckLoopEnabled == true -> "Disable loop"
                            hasRegion -> "Enable loop"
                            else -> "Start loop region selection"
                        },
                    )
                },
                onClick = {
                    expanded = false
                    onToggleLoop()
                },
                enabled = hasSession,
            )
        }
    }
}

@Composable
private fun clusterDivider() {
    VerticalDivider(
        modifier = Modifier.height(ToolbarControlHeight),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DawTopBar(
    activeMixerName: String?,
    appMode: AppMode?,
    session: MixerSessionUiState?,
    showMixerCluster: Boolean,
    isRemoteClient: Boolean,
    remoteHostLabel: String?,
    remoteConnectedClientCount: Int = 0,
    isRemoteHost: Boolean = false,
    onExitRemoteMode: () -> Unit = {},
    onOpenMixerPicker: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onToggleSoundcheckPlayback: () -> Unit,
    onStopSoundcheck: () -> Unit,
    onToggleSoundcheckLoop: () -> Unit,
    onSetSoundcheckLoopIn: () -> Unit,
    onSetSoundcheckLoopOut: () -> Unit,
    onOpenMenu: () -> Unit = {},
) {
    Column {
        if (isRemoteClient && remoteHostLabel != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "REMOTE CONTROL — $remoteHostLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    ToolbarChip(onClick = onExitRemoteMode) {
                        Text(
                            "Exit",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        } else if (isRemoteHost && remoteConnectedClientCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Remote connected ($remoteConnectedClientCount)",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        CenterAlignedTopAppBar(
            navigationIcon = {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            title = {
                if (showMixerCluster) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val showBothTransports = maxWidth >= BothTransportsMinWidth
                        val mixerNameMaxWidth = when {
                            maxWidth >= 480.dp -> MixerNameWideMaxWidth
                            maxWidth >= 360.dp -> MixerNameMediumMaxWidth
                            else -> MixerNameNarrowMaxWidth
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MixerPickerButton(
                                activeMixerName = activeMixerName,
                                mixerNameMaxWidth = mixerNameMaxWidth,
                                onOpenMixerPicker = onOpenMixerPicker,
                            )
                            val showRecordTransport = showBothTransports ||
                                appMode == AppMode.MULTITRACK_RECORD
                            val showPlaybackTransport = showBothTransports ||
                                appMode?.isPlaybackMode == true
                            if (showRecordTransport) {
                                RecordTransportCluster(
                                    session = session,
                                    isRemoteClient = isRemoteClient,
                                    onStartRecord = onStartRecord,
                                    onStopRecord = onStopRecord,
                                )
                            }
                            if (showPlaybackTransport) {
                                PlaybackTransportCluster(
                                    session = session,
                                    isRemoteClient = isRemoteClient,
                                    onTogglePlayback = onToggleSoundcheckPlayback,
                                    onStopPlayback = onStopSoundcheck,
                                    onToggleLoop = onToggleSoundcheckLoop,
                                    onSetLoopIn = onSetSoundcheckLoopIn,
                                    onSetLoopOut = onSetSoundcheckLoopOut,
                                )
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@Composable
fun DawOverflowMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    appMode: AppMode?,
    session: MixerSessionUiState?,
    isRemoteClient: Boolean,
    isRemoteHost: Boolean,
    remoteConnectedClientCount: Int,
    remoteConnectionState: RemoteConnectionState,
    showRecordingStorageInfoButton: Boolean,
    showOpenSessionHint: Boolean,
    mixerSettingsEnabled: Boolean,
    onOpenMixerSettings: () -> Unit,
    onSetAppMode: (AppMode) -> Unit,
    onOpenSessionPicker: () -> Unit,
    onOpenRemoteControl: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit,
) {
    if (!open) return

    var showStorageInfo by remember { mutableStateOf(false) }
    val storageInfoText = remember(session?.storageFreeBytes, session?.storageRecordEstimateSec) {
        formatRecordingStorageInfo(
            session?.storageFreeBytes ?: 0L,
            session?.storageRecordEstimateSec ?: 0f,
        )
    }
    val remoteHighlight = isRemoteClient ||
        (isRemoteHost && remoteConnectedClientCount > 0) ||
        remoteConnectionState == RemoteConnectionState.CONNECTING
    val openSessionEnabled = appMode?.isPlaybackMode == true &&
        session?.soundcheckSessions?.isNotEmpty() == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Menu") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DawMenuItem(
                    icon = Icons.Default.Tune,
                    label = "Mixer settings",
                    enabled = mixerSettingsEnabled,
                    onClick = {
                        onDismiss()
                        onOpenMixerSettings()
                    },
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(
                    "Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                AppMode.entries.forEach { mode ->
                    DawMenuItem(
                        icon = mode.toolbarIcon(),
                        label = mode.displayLabel,
                        selected = mode == appMode,
                        iconTint = when (mode) {
                            AppMode.MULTITRACK_RECORD -> RecordRed
                            AppMode.VIRTUAL_SOUNDCHECK -> MaterialTheme.colorScheme.primary
                            AppMode.SIMPLE_PLAY -> MaterialTheme.colorScheme.tertiary
                        },
                        onClick = {
                            onDismiss()
                            if (mode != appMode) onSetAppMode(mode)
                        },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (showRecordingStorageInfoButton) {
                    DawMenuItem(
                        icon = Icons.Default.Info,
                        label = "Storage info",
                        onClick = { showStorageInfo = true },
                    )
                }
                if (appMode?.isPlaybackMode == true) {
                    DawMenuItem(
                        icon = Icons.Default.FolderOpen,
                        label = if (showOpenSessionHint) {
                            "Open recording (newer available)"
                        } else {
                            "Open recording"
                        },
                        enabled = openSessionEnabled,
                        onClick = {
                            onDismiss()
                            onOpenSessionPicker()
                        },
                    )
                }
                DawMenuItem(
                    icon = Icons.Default.Cast,
                    label = "Remote control",
                    iconTint = if (remoteHighlight) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    onClick = {
                        onDismiss()
                        onOpenRemoteControl()
                    },
                )
                DawMenuItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    },
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                DawMenuItem(
                    icon = Icons.Default.Menu,
                    label = "Log viewer",
                    onClick = {
                        onDismiss()
                        onOpenLog()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )

    if (showStorageInfo) {
        AlertDialog(
            onDismissRequest = { showStorageInfo = false },
            title = { Text("Storage") },
            text = { Text(storageInfoText) },
            confirmButton = {
                TextButton(onClick = { showStorageInfo = false }) { Text("OK") }
            },
        )
    }
}

@Composable
fun RecordingStorageInfoDialog(
    open: Boolean,
    session: MixerSessionUiState?,
    onDismiss: () -> Unit,
) {
    if (!open) return
    val storageInfoText = remember(session?.storageFreeBytes, session?.storageRecordEstimateSec) {
        formatRecordingStorageInfo(
            session?.storageFreeBytes ?: 0L,
            session?.storageRecordEstimateSec ?: 0f,
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage") },
        text = { Text(storageInfoText) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun DawMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        Color.Transparent
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = background,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
    }
}
