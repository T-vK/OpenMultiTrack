package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.abbrevLabel
import org.openmultitrack.domain.session.displayLabel
import org.openmultitrack.domain.session.shortLabel
import org.openmultitrack.domain.session.isPlaybackMode

private val ToolbarShape = RoundedCornerShape(8.dp)
private val ToolbarControlHeight = 40.dp
private val MixerNameWideMaxWidth = 160.dp
private val MixerNameMediumMaxWidth = 112.dp
private val MixerNameNarrowMaxWidth = 80.dp

internal enum class AppModeLabelDensity {
    FULL,
    SHORT,
    ABBREV,
    ICON_ONLY,
}

private fun AppMode.toolbarLabel(density: AppModeLabelDensity): String? = when (density) {
    AppModeLabelDensity.FULL -> displayLabel
    AppModeLabelDensity.SHORT -> shortLabel
    AppModeLabelDensity.ABBREV -> abbrevLabel
    AppModeLabelDensity.ICON_ONLY -> null
}

/** Which toolbar items stay in the top bar vs overflow into the navigation drawer. */
internal data class ToolbarLayout(
    val mixerNameMaxWidth: Dp,
    val showMixerSettingsInBar: Boolean,
    val showModeInBar: Boolean,
    val modeLabelDensity: AppModeLabelDensity,
    val showStorageInBar: Boolean,
    val showOpenInBar: Boolean,
    val showRemoteInBar: Boolean,
    val showSettingsInBar: Boolean,
) {
    val showMixerSettingsInDrawer: Boolean get() = !showMixerSettingsInBar
    val showModeInDrawer: Boolean get() = !showModeInBar
    val showOpenInDrawer: Boolean get() = !showOpenInBar
    val showRemoteInDrawer: Boolean get() = !showRemoteInBar
    val showSettingsInDrawer: Boolean get() = !showSettingsInBar
    val showStorageInDrawer: Boolean get() = !showStorageInBar
}

internal fun computeToolbarLayout(
    barWidth: Dp,
    appMode: AppMode?,
    showRecordingStorageInfoButton: Boolean,
): ToolbarLayout {
    val isPlaybackMode = appMode?.isPlaybackMode == true
    val isRecordingMode = appMode == AppMode.MULTITRACK_RECORD

    return ToolbarLayout(
        mixerNameMaxWidth = when {
            barWidth >= 720.dp -> MixerNameWideMaxWidth
            barWidth >= 520.dp -> MixerNameMediumMaxWidth
            else -> MixerNameNarrowMaxWidth
        },
        showMixerSettingsInBar = barWidth >= 580.dp,
        showModeInBar = barWidth >= 500.dp,
        modeLabelDensity = when {
            barWidth >= 900.dp -> AppModeLabelDensity.FULL
            barWidth >= 760.dp -> AppModeLabelDensity.SHORT
            barWidth >= 580.dp -> AppModeLabelDensity.ABBREV
            else -> AppModeLabelDensity.ICON_ONLY
        },
        showStorageInBar = showRecordingStorageInfoButton &&
            isRecordingMode &&
            barWidth >= 460.dp,
        showOpenInBar = isPlaybackMode && barWidth >= 640.dp,
        showRemoteInBar = barWidth >= 600.dp,
        showSettingsInBar = barWidth >= 720.dp,
    )
}

private fun AppMode.toolbarIcon(): ImageVector = when (this) {
    AppMode.MULTITRACK_RECORD -> Icons.Default.Album
    AppMode.VIRTUAL_SOUNDCHECK -> Icons.Default.GraphicEq
    AppMode.SIMPLE_PLAY -> Icons.Default.PlayCircle
}

private val RecordRed = Color(0xFFE53935)
private val LoopAmber = Color(0xFFFFB300)

@Composable
private fun RecordingStorageGlyph(
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    val badgeSize = iconSize * 0.48f
    Box(modifier = modifier.size(iconSize)) {
        Icon(
            imageVector = Icons.Default.SdCard,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            tint = tint,
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(badgeSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            shadowElevation = 1.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "i",
                    fontSize = (badgeSize.value * 0.62f).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = (badgeSize.value * 0.62f).sp,
                    maxLines = 1,
                )
            }
        }
    }
}

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
    activeMixer: MixerProfile?,
    appMode: AppMode?,
    layout: ToolbarLayout,
    onOpenMixerPicker: () -> Unit,
    onOpenMixerSettings: () -> Unit,
    onSetAppMode: (AppMode) -> Unit,
) {
    TransportActionRow {
        Surface(
            onClick = onOpenMixerPicker,
            color = Color.Transparent,
            modifier = Modifier.height(ToolbarControlHeight),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = mixerProfileIcon(activeMixer),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    activeMixer?.displayName ?: "Select mixer",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = layout.mixerNameMaxWidth),
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (layout.showMixerSettingsInBar) {
            clusterDivider()
            TransportBarIconButton(
                onClick = onOpenMixerSettings,
                enabled = true,
                icon = Icons.Default.Tune,
                contentDescription = "Mixer settings",
            )
        }
        if (layout.showModeInBar && appMode != null) {
            clusterDivider()
            AppModeDropdown(
                appMode = appMode,
                labelDensity = layout.modeLabelDensity,
                onSetAppMode = onSetAppMode,
            )
        }
    }
}

@Composable
private fun AppModeDropdown(
    appMode: AppMode,
    labelDensity: AppModeLabelDensity,
    onSetAppMode: (AppMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val modeIcon = appMode.toolbarIcon()
    val label = appMode.toolbarLabel(labelDensity)
    Box {
        Surface(
            onClick = { expanded = true },
            color = Color.Transparent,
            modifier = Modifier.height(ToolbarControlHeight),
        ) {
            Row(
                Modifier.padding(horizontal = if (label == null) 6.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    modeIcon,
                    contentDescription = appMode.displayLabel,
                    modifier = Modifier.size(20.dp),
                    tint = when (appMode) {
                        AppMode.MULTITRACK_RECORD -> RecordRed
                        AppMode.VIRTUAL_SOUNDCHECK -> MaterialTheme.colorScheme.primary
                        AppMode.SIMPLE_PLAY -> MaterialTheme.colorScheme.tertiary
                    },
                )
                if (label != null) {
                    Text(
                        label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.widthIn(max = 96.dp),
                    )
                }
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
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                mode.toolbarIcon(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = when (mode) {
                                    AppMode.MULTITRACK_RECORD -> RecordRed
                                    AppMode.VIRTUAL_SOUNDCHECK -> MaterialTheme.colorScheme.primary
                                    AppMode.SIMPLE_PLAY -> MaterialTheme.colorScheme.tertiary
                                },
                            )
                            Text(mode.displayLabel)
                        }
                    },
                    onClick = {
                        expanded = false
                        if (mode != appMode) onSetAppMode(mode)
                    },
                )
            }
        }
    }
}

/** Lightweight transport row that matches plain top-bar icon buttons. */
@Composable
private fun TransportActionRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .height(ToolbarControlHeight)
            .clip(ToolbarShape)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                ToolbarShape,
            )
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun TransportBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(ToolbarControlHeight),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
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
    val elapsed = session?.recordElapsedSec ?: 0f
    val onClick = if (isRecording) onStopRecord else onStartRecord
    TransportActionRow {
        Surface(
            onClick = onClick,
            enabled = hostReady,
            color = Color.Transparent,
            modifier = Modifier.height(ToolbarControlHeight),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (isRecording) "Stop recording" else "Record",
                    modifier = Modifier.size(22.dp),
                    tint = RecordRed,
                )
                Text(
                    text = if (isRecording) formatAdaptiveTransportTime(elapsed) else "0:00",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRecording) RecordRed else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaybackTransportCluster(
    session: MixerSessionUiState?,
    isRemoteClient: Boolean,
    chaptersEnabled: Boolean,
    onTogglePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    onToggleLoop: () -> Unit,
    onSetLoopIn: () -> Unit,
    onSetLoopOut: () -> Unit,
    onPreviousTrackmark: () -> Unit,
    onNextTrackmark: () -> Unit,
    onAddTrackmark: () -> Unit,
    onShowTrackmarkList: () -> Unit,
) {
    val isPlaying = session?.isPlaying == true
    val hostReady = session?.probe != null ||
        (isRemoteClient && session?.captureChannelCount?.let { it > 0 } == true)
    val hasSession = session?.selectedSoundcheckDir != null && hostReady
    val canStop = hasSession && (isPlaying || (session?.playbackPositionSec ?: 0f) > 0.05f)
    val hasTrackmarks = chaptersEnabled && session?.trackmarks?.isNotEmpty() == true
    TransportActionRow {
        if (chaptersEnabled) {
            TransportBarIconButton(
                onClick = onPreviousTrackmark,
                enabled = hasTrackmarks,
                icon = Icons.Default.SkipPrevious,
                contentDescription = "Previous trackmark",
            )
        }
        TransportBarIconButton(
            onClick = onTogglePlayback,
            enabled = hasSession,
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
        )
        TransportBarIconButton(
            onClick = onStopPlayback,
            enabled = canStop,
            icon = Icons.Default.Stop,
            contentDescription = "Stop playback",
        )
        if (chaptersEnabled) {
            TransportBarIconButton(
                onClick = onNextTrackmark,
                enabled = hasTrackmarks,
                icon = Icons.Default.SkipNext,
                contentDescription = "Next trackmark",
            )
            TransportBarIconButton(
                onClick = onAddTrackmark,
                enabled = hasSession,
                icon = Icons.Default.Bookmark,
                contentDescription = "Add trackmark",
            )
            TransportBarIconButton(
                onClick = onShowTrackmarkList,
                enabled = hasSession,
                icon = Icons.Default.FormatListBulleted,
                contentDescription = "Trackmark list",
            )
        }
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
        TransportBarIconButton(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingStorageToolbarIcon(
    session: MixerSessionUiState?,
    tooltipOpen: Boolean,
    onTooltipOpenChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val storageTooltipText = remember(session?.storageFreeBytes, session?.storageRecordEstimateSec) {
        formatRecordingStorageInfo(
            session?.storageFreeBytes ?: 0L,
            session?.storageRecordEstimateSec ?: 0f,
        )
    }
    val storageTooltipState = rememberTooltipState()
    LaunchedEffect(tooltipOpen) {
        if (tooltipOpen) {
            storageTooltipState.show()
            kotlinx.coroutines.delay(5_000)
            storageTooltipState.dismiss()
            onTooltipOpenChange(false)
        } else {
            storageTooltipState.dismiss()
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(storageTooltipText)
            }
        },
        state = storageTooltipState,
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    onTooltipOpenChange(true)
                }
            },
        ) {
            RecordingStorageGlyph(
                tint = MaterialTheme.colorScheme.onSurface,
                iconSize = 22.dp,
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
internal fun DawTopBar(
    layout: ToolbarLayout,
    activeMixer: MixerProfile?,
    appMode: AppMode?,
    session: MixerSessionUiState?,
    showMixerCluster: Boolean,
    isRemoteClient: Boolean,
    remoteHostLabel: String?,
    remoteConnectedClientCount: Int = 0,
    isRemoteHost: Boolean = false,
    remoteConnectionState: RemoteConnectionState = RemoteConnectionState.DISCONNECTED,
    showOpenSessionHint: Boolean = false,
    onExitRemoteMode: () -> Unit = {},
    onOpenMixerPicker: () -> Unit,
    onOpenMixerSettings: () -> Unit = {},
    onSetAppMode: (AppMode) -> Unit = {},
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onToggleSoundcheckPlayback: () -> Unit,
    onStopSoundcheck: () -> Unit,
    onToggleSoundcheckLoop: () -> Unit,
    onSetSoundcheckLoopIn: () -> Unit,
    onSetSoundcheckLoopOut: () -> Unit,
    showRecordingStorageInfoButton: Boolean = false,
    storageTooltipOpen: Boolean = false,
    onStorageTooltipOpenChange: (Boolean) -> Unit = {},
    chaptersEnabled: Boolean = false,
    onPreviousTrackmark: () -> Unit = {},
    onNextTrackmark: () -> Unit = {},
    onAddTrackmark: () -> Unit = {},
    onShowTrackmarkList: () -> Unit = {},
    onOpenSessionPicker: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenRemoteControl: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
) {
    val remoteHighlight = isRemoteClient ||
        (isRemoteHost && remoteConnectedClientCount > 0) ||
        remoteConnectionState == RemoteConnectionState.CONNECTING
    val openSessionEnabled = appMode?.isPlaybackMode == true &&
        session?.soundcheckSessions?.isNotEmpty() == true
    val showRecordTransport = appMode == AppMode.MULTITRACK_RECORD
    val showPlaybackTransport = appMode?.isPlaybackMode == true
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
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            title = {
                if (showMixerCluster) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MixerControlCluster(
                            activeMixer = activeMixer,
                            appMode = appMode,
                            layout = layout,
                            onOpenMixerPicker = onOpenMixerPicker,
                            onOpenMixerSettings = onOpenMixerSettings,
                            onSetAppMode = onSetAppMode,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                    chaptersEnabled = chaptersEnabled,
                                    onTogglePlayback = onToggleSoundcheckPlayback,
                                    onStopPlayback = onStopSoundcheck,
                                    onToggleLoop = onToggleSoundcheckLoop,
                                    onSetLoopIn = onSetSoundcheckLoopIn,
                                    onSetLoopOut = onSetSoundcheckLoopOut,
                                    onPreviousTrackmark = onPreviousTrackmark,
                                    onNextTrackmark = onNextTrackmark,
                                    onAddTrackmark = onAddTrackmark,
                                    onShowTrackmarkList = onShowTrackmarkList,
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                if (layout.showOpenInBar) {
                    OpenSessionButton(
                        enabled = openSessionEnabled,
                        showHint = showOpenSessionHint,
                        onClick = onOpenSessionPicker,
                    )
                }
                if (layout.showStorageInBar && showRecordingStorageInfoButton) {
                    RecordingStorageToolbarIcon(
                        session = session,
                        tooltipOpen = storageTooltipOpen,
                        onTooltipOpenChange = onStorageTooltipOpenChange,
                    )
                }
                if (layout.showRemoteInBar) {
                    IconButton(onClick = onOpenRemoteControl) {
                        Icon(
                            Icons.Default.Cast,
                            contentDescription = "Remote control",
                            tint = if (remoteHighlight) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                if (layout.showSettingsInBar) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "App settings")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenSessionButton(
    enabled: Boolean,
    showHint: Boolean,
    onClick: () -> Unit,
) {
    val openTooltipState = rememberTooltipState()
    LaunchedEffect(showHint, enabled) {
        if (showHint && enabled) {
            openTooltipState.show()
        } else {
            openTooltipState.dismiss()
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text("Load most recent recording")
            }
        },
        state = openTooltipState,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(Icons.Default.FolderOpen, contentDescription = "Open recording")
        }
    }
}

@Composable
internal fun DawNavigationDrawer(
    drawerState: DrawerState,
    layout: ToolbarLayout,
    appMode: AppMode?,
    session: MixerSessionUiState?,
    isRemoteClient: Boolean,
    isRemoteHost: Boolean,
    remoteConnectedClientCount: Int,
    remoteConnectionState: RemoteConnectionState,
    showOpenSessionHint: Boolean,
    showRecordingStorageInfoButton: Boolean,
    mixerSettingsEnabled: Boolean,
    onOpenMixerSettings: () -> Unit,
    onSetAppMode: (AppMode) -> Unit,
    onOpenSessionPicker: () -> Unit,
    onOpenRemoteControl: () -> Unit,
    onOpenSettings: () -> Unit,
    onStorageTooltipOpenChange: (Boolean) -> Unit,
    onOpenLog: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val remoteHighlight = isRemoteClient ||
        (isRemoteHost && remoteConnectedClientCount > 0) ||
        remoteConnectionState == RemoteConnectionState.CONNECTING
    val openSessionEnabled = appMode?.isPlaybackMode == true &&
        session?.soundcheckSessions?.isNotEmpty() == true
    val openSessionLabel = if (showOpenSessionHint) {
        "Open recording (newer available)"
    } else {
        "Open recording"
    }

    fun closeAnd(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalDrawerSheet {
        Text(
            text = "OpenMultiTrack",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        )
        if (layout.showMixerSettingsInDrawer) {
            NavigationDrawerItem(
                icon = {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (mixerSettingsEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                },
                label = { Text("Mixer settings") },
                selected = false,
                onClick = {
                    if (mixerSettingsEnabled) {
                        closeAnd(onOpenMixerSettings)
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        if (layout.showModeInDrawer) {
            if (layout.showMixerSettingsInDrawer) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            }
            Text(
                "Mode",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            )
            AppMode.entries.forEach { mode ->
                val modeTint = when (mode) {
                    AppMode.MULTITRACK_RECORD -> RecordRed
                    AppMode.VIRTUAL_SOUNDCHECK -> MaterialTheme.colorScheme.primary
                    AppMode.SIMPLE_PLAY -> MaterialTheme.colorScheme.tertiary
                }
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            imageVector = mode.toolbarIcon(),
                            contentDescription = null,
                            tint = modeTint,
                        )
                    },
                    label = { Text(mode.displayLabel) },
                    selected = mode == appMode,
                    onClick = {
                        closeAnd {
                            if (mode != appMode) onSetAppMode(mode)
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        }
        if (layout.showStorageInDrawer && showRecordingStorageInfoButton) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            NavigationDrawerItem(
                icon = {
                    RecordingStorageGlyph(
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconSize = 24.dp,
                    )
                },
                label = { Text("Storage info") },
                selected = false,
                onClick = { closeAnd { onStorageTooltipOpenChange(true) } },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        if (layout.showOpenInDrawer && appMode?.isPlaybackMode == true) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            NavigationDrawerItem(
                icon = {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = if (openSessionEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                },
                label = { Text(openSessionLabel) },
                selected = false,
                onClick = {
                    if (openSessionEnabled) {
                        closeAnd(onOpenSessionPicker)
                    }
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        if (layout.showRemoteInDrawer) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = null,
                        tint = if (remoteHighlight) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                label = { Text("Remote control") },
                selected = false,
                onClick = { closeAnd(onOpenRemoteControl) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        if (layout.showSettingsInDrawer) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = false,
                onClick = { closeAnd(onOpenSettings) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Article, contentDescription = null) },
            label = { Text("Log viewer") },
            selected = false,
            onClick = { closeAnd(onOpenLog) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
    }
}

