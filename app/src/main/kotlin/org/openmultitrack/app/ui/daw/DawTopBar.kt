package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.remote.RemoteRole
import org.openmultitrack.domain.session.AppMode

private val ToolbarShape = RoundedCornerShape(8.dp)
private val ToolbarControlHeight = 36.dp

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DawTopBar(
    activeMixerName: String?,
    appMode: AppMode?,
    isRemoteClient: Boolean,
    remoteHostLabel: String?,
    remoteConnectionState: RemoteConnectionState,
    onOpenMixerPicker: () -> Unit,
    onToggleAppMode: () -> Unit,
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
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onOpenMenu) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToolbarChip(onClick = onOpenMixerPicker) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            activeMixerName ?: "Select mixer",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    if (appMode != null) {
                        val recording = appMode == AppMode.MULTITRACK_RECORD
                        ToolbarChip(onClick = onToggleAppMode, selected = true) {
                            Icon(
                                if (recording) Icons.Default.Mic else Icons.Default.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text(
                                if (recording) "Record" else "Soundcheck",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            },
            actions = {
                val remoteActive = isRemoteClient ||
                    remoteConnectionState == RemoteConnectionState.CONNECTED
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
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}
