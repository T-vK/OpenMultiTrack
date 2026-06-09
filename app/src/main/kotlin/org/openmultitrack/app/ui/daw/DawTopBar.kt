package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.displayLabel
import org.openmultitrack.domain.session.isPlaybackMode

private val ToolbarShape = RoundedCornerShape(8.dp)
private val ToolbarControlHeight = 40.dp
private val MixerNameMaxWidth = 160.dp
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
private fun MixerControlCluster(
    activeMixerName: String?,
    appMode: AppMode?,
    session: MixerSessionUiState?,
    onOpenMixerPicker: () -> Unit,
    onOpenMixerSettings: () -> Unit,
    onSetAppMode: (AppMode) -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onToggleSoundcheckPlayback: () -> Unit,
    onStopSoundcheck: () -> Unit,
    onToggleSoundcheckLoop: () -> Unit,
    onSetSoundcheckLoopIn: () -> Unit,
    onSetSoundcheckLoopOut: () -> Unit,
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    Surface(
        shape = ToolbarShape,
        border = border,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.height(ToolbarControlHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onOpenMixerPicker,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
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
                        modifier = Modifier.widthIn(max = MixerNameMaxWidth),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            clusterDivider()
            IconButton(
                onClick = onOpenMixerSettings,
                modifier = Modifier.size(ToolbarControlHeight),
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Mixer settings")
            }
            if (appMode != null) {
                clusterDivider()
                AppModeDropdown(
                    appMode = appMode,
                    onSetAppMode = onSetAppMode,
                )
                clusterDivider()
                when {
                    appMode == AppMode.MULTITRACK_RECORD -> RecordTransportCluster(
                        session = session,
                        onStartRecord = onStartRecord,
                        onStopRecord = onStopRecord,
                    )
                    appMode.isPlaybackMode -> PlaybackTransportCluster(
                        session = session,
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
}

@Composable
private fun TransportButtonCluster(
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = ToolbarShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
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
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
) {
    val isRecording = session?.isRecording == true
    val canRecord = session?.probe != null && !isRecording
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
    }
}

@Composable
private fun PlaybackTransportCluster(
    session: MixerSessionUiState?,
    onTogglePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    onToggleLoop: () -> Unit,
    onSetLoopIn: () -> Unit,
    onSetLoopOut: () -> Unit,
) {
    val isPlaying = session?.isPlaying == true
    val hasSession = session?.selectedSoundcheckDir != null && session.probe != null
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
private fun AppModeDropdown(
    appMode: AppMode,
    onSetAppMode: (AppMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val modeIcon = when (appMode) {
        AppMode.MULTITRACK_RECORD -> Icons.Default.Mic
        AppMode.SIMPLE_PLAY -> Icons.Default.PlayArrow
        AppMode.VIRTUAL_SOUNDCHECK -> Icons.AutoMirrored.Filled.VolumeUp
    }
    Box {
        Surface(
            onClick = { expanded = true },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            modifier = Modifier.height(ToolbarControlHeight),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    modeIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    appMode.displayLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = "Change mode",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.displayLabel) },
                    onClick = {
                        expanded = false
                        if (mode != appMode) onSetAppMode(mode)
                    },
                )
            }
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
    remoteConnectionState: RemoteConnectionState,
    onOpenMixerPicker: () -> Unit,
    onOpenMixerSettings: () -> Unit,
    onSetAppMode: (AppMode) -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onToggleSoundcheckPlayback: () -> Unit,
    onStopSoundcheck: () -> Unit,
    onToggleSoundcheckLoop: () -> Unit,
    onSetSoundcheckLoopIn: () -> Unit,
    onSetSoundcheckLoopOut: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRemoteControl: () -> Unit,
    onOpenMenu: () -> Unit = {},
) {
    Column {
        if (isRemoteClient && remoteHostLabel != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Remote → $remoteHostLabel",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    MixerControlCluster(
                        activeMixerName = activeMixerName,
                        appMode = appMode,
                        session = session,
                        onOpenMixerPicker = onOpenMixerPicker,
                        onOpenMixerSettings = onOpenMixerSettings,
                        onSetAppMode = onSetAppMode,
                        onStartRecord = onStartRecord,
                        onStopRecord = onStopRecord,
                        onToggleSoundcheckPlayback = onToggleSoundcheckPlayback,
                        onStopSoundcheck = onStopSoundcheck,
                        onToggleSoundcheckLoop = onToggleSoundcheckLoop,
                        onSetSoundcheckLoopIn = onSetSoundcheckLoopIn,
                        onSetSoundcheckLoopOut = onSetSoundcheckLoopOut,
                    )
                }
            },
            actions = {
                val remoteActive = remoteConnectionState == RemoteConnectionState.CONNECTED
                IconButton(onClick = onOpenRemoteControl) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = "Remote control",
                        tint = if (remoteActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "App settings")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}
