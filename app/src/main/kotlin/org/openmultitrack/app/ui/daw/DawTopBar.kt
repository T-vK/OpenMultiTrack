package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.session.AppMode

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
    onToggleAppMode: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onToggleSoundcheckPlayback: () -> Unit,
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    Surface(
        shape = ToolbarShape,
        border = border,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.height(ToolbarControlHeight),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                val recording = appMode == AppMode.MULTITRACK_RECORD
                val modeLabel = if (recording) "Recording Mode" else "Virtual Soundcheck"
                Surface(
                    onClick = onToggleAppMode,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier.height(ToolbarControlHeight),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            if (recording) Icons.Default.Mic else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            modeLabel,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                clusterDivider()
                when (appMode) {
                    AppMode.MULTITRACK_RECORD -> RecordClusterButton(
                        session = session,
                        onStartRecord = onStartRecord,
                        onStopRecord = onStopRecord,
                    )
                    AppMode.VIRTUAL_SOUNDCHECK -> PlaybackClusterButton(
                        session = session,
                        onTogglePlayback = onToggleSoundcheckPlayback,
                    )
                }
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

@Composable
private fun RecordClusterButton(
    session: MixerSessionUiState?,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
) {
    val isRecording = session?.isRecording == true
    val enabled = session?.probe != null
    Surface(
        onClick = { if (isRecording) onStopRecord() else onStartRecord() },
        enabled = enabled || isRecording,
        color = if (isRecording) RecordRed else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier
            .height(ToolbarControlHeight)
            .widthIn(min = ToolbarControlHeight),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                contentDescription = if (isRecording) "Stop recording" else "Record",
                modifier = Modifier.size(20.dp),
                tint = if (isRecording) Color.White else RecordRed,
            )
        }
    }
}

@Composable
private fun PlaybackClusterButton(
    session: MixerSessionUiState?,
    onTogglePlayback: () -> Unit,
) {
    val isPlaying = session?.isPlaying == true
    val enabled = session?.selectedSoundcheckDir != null && session.probe != null
    Surface(
        onClick = onTogglePlayback,
        enabled = enabled,
        color = if (isPlaying) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        },
        modifier = Modifier
            .height(ToolbarControlHeight)
            .widthIn(min = ToolbarControlHeight),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause playback" else "Play",
                modifier = Modifier.size(22.dp),
                tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LoopMenuButton(
    session: MixerSessionUiState?,
    onSetLoopIn: () -> Unit,
    onSetLoopOut: () -> Unit,
    onToggleLoop: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasSession = session?.selectedSoundcheckDir != null
    val hasRegion = session?.soundcheckLoopStartSec != null && session.soundcheckLoopEndSec != null

    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = hasSession,
        ) {
            Icon(
                Icons.Default.Repeat,
                contentDescription = "Loop",
                tint = when {
                    session?.soundcheckLoopEnabled == true -> LoopAmber
                    session?.soundcheckLoopSelecting == true -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
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
    onToggleAppMode: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onToggleSoundcheckPlayback: () -> Unit,
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
                        onToggleAppMode = onToggleAppMode,
                        onStartRecord = onStartRecord,
                        onStopRecord = onStopRecord,
                        onToggleSoundcheckPlayback = onToggleSoundcheckPlayback,
                    )
                }
            },
            actions = {
                if (appMode == AppMode.VIRTUAL_SOUNDCHECK && showMixerCluster) {
                    LoopMenuButton(
                        session = session,
                        onSetLoopIn = onSetSoundcheckLoopIn,
                        onSetLoopOut = onSetSoundcheckLoopOut,
                        onToggleLoop = onToggleSoundcheckLoop,
                    )
                }
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
