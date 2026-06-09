package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.scribble.ScribbleImportSupport
import org.openmultitrack.domain.mixer.MixerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerPickerScreen(
    mixers: List<MixerProfile>,
    activeMixerId: String?,
    onDismiss: () -> Unit,
    onSelectMixer: (String) -> Unit,
    onAddNewDevice: () -> Unit,
    onLoadChannelNames: (String) -> Unit,
    onRemoveMixer: (String) -> Unit,
) {
    var removeConfirm by remember { mutableStateOf<MixerProfile?>(null) }

    removeConfirm?.let { mixer ->
        AlertDialog(
            onDismissRequest = { removeConfirm = null },
            title = { Text("Remove ${mixer.displayName}?") },
            text = {
                Text("This removes the mixer from OpenMultiTrack. USB settings and routing for this device will be cleared.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        removeConfirm = null
                        onRemoveMixer(mixer.id)
                        onDismiss()
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeConfirm = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mixers") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(mixers, key = { it.id }) { mixer ->
                    val isActive = mixer.id == activeMixerId
                    Surface(
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        modifier = Modifier.clickable {
                            onSelectMixer(mixer.id)
                            onDismiss()
                        },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(mixer.displayName, style = MaterialTheme.typography.titleMedium)
                                    mixer.productName?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isActive) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(
                                    onClick = { removeConfirm = mixer },
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove mixer",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (ScribbleImportSupport.supports(mixer)) {
                                OutlinedButton(
                                    onClick = { onLoadChannelNames(mixer.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                ) {
                                    Text("Sync channel names from mixer to app")
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            item {
                Button(
                    onClick = {
                        onDismiss()
                        onAddNewDevice()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add new device", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
