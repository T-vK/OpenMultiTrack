package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.R
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.session.AppMode

private val ToolbarShape = RoundedCornerShape(8.dp)
private val ToolbarControlHeight = 40.dp
private val MixerNameMaxWidth = 160.dp

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
    onOpenMixerPicker: () -> Unit,
    onOpenMixerSettings: () -> Unit,
    onToggleAppMode: () -> Unit,
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
            VerticalDivider(
                modifier = Modifier.height(ToolbarControlHeight),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            )
            IconButton(
                onClick = onOpenMixerSettings,
                modifier = Modifier.size(ToolbarControlHeight),
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Mixer settings")
            }
            if (appMode != null) {
                VerticalDivider(
                    modifier = Modifier.height(ToolbarControlHeight),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                )
                val recording = appMode == AppMode.MULTITRACK_RECORD
                val modeLabel = if (recording) "Recording Mode" else "Virtual Soundcheck"
                Surface(
                    onClick = onToggleAppMode,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DawTopBar(
    activeMixerName: String?,
    appMode: AppMode?,
    showMixerCluster: Boolean,
    isRemoteClient: Boolean,
    remoteHostLabel: String?,
    remoteConnectionState: RemoteConnectionState,
    onOpenMixerPicker: () -> Unit,
    onOpenMixerSettings: () -> Unit,
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
                        onOpenMixerPicker = onOpenMixerPicker,
                        onOpenMixerSettings = onOpenMixerSettings,
                        onToggleAppMode = onToggleAppMode,
                    )
                }
            },
            actions = {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "OpenMultiTrack",
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(40.dp)
                        .clip(CircleShape),
                )
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
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}
