package org.openmultitrack.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.data.StorageVolumeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsStorageSection(
    effectiveStorageRootPath: String,
    storageRootPath: String?,
    additionalLibraryRoots: List<String>,
    autoScanRemovableMedia: Boolean,
    storageVolumeOptions: List<StorageVolumeOption>,
    onSetStorageRootPath: (String?) -> Unit,
    onAddAdditionalLibraryRoot: (String) -> Unit,
    onRemoveAdditionalLibraryRoot: (String) -> Unit,
    onAutoScanRemovableMediaChange: (Boolean) -> Unit,
) {
    var addPathDialog by remember { mutableStateOf(false) }
    var newPathText by remember { mutableStateOf("") }

    if (addPathDialog) {
        AlertDialog(
            onDismissRequest = { addPathDialog = false },
            title = { Text("Add library location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a folder that contains mixer recording folders, or pick a suggested volume below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = newPathText,
                        onValueChange = { newPathText = it },
                        singleLine = true,
                        label = { Text("Folder path") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    storageVolumeOptions
                        .filter { option -> option.path !in additionalLibraryRoots }
                        .take(6)
                        .forEach { option ->
                            TextButton(
                                onClick = { newPathText = option.path },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "${option.label}: ${option.path}",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val path = newPathText.trim()
                        if (path.isNotEmpty()) onAddAdditionalLibraryRoot(path)
                        addPathDialog = false
                        newPathText = ""
                    },
                    enabled = newPathText.trim().isNotEmpty(),
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { addPathDialog = false }) { Text("Cancel") }
            },
        )
    }

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(
        "Storage",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Default recording location", style = MaterialTheme.typography.bodyLarge)
        Text(
            "New multitrack recordings are saved under this folder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            effectiveStorageRootPath,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        storageVolumeOptions.forEach { option ->
            val isSelected = if (option.label == "App storage") {
                storageRootPath == null
            } else {
                storageRootPath == option.path
            }
            Surface(
                onClick = {
                    onSetStorageRootPath(
                        if (option.label == "App storage") null else option.path,
                    )
                },
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(option.label, style = MaterialTheme.typography.labelLarge)
                    Text(
                        option.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Scan removable media", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Look for OpenMultiTrack/Recordings on SD cards and USB drives when listing sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = autoScanRemovableMedia, onCheckedChange = onAutoScanRemovableMediaChange)
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Additional library locations", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Extra folders to search for completed recordings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { addPathDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add library location")
            }
        }
        if (additionalLibraryRoots.isEmpty()) {
            Text(
                "No extra locations configured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            additionalLibraryRoots.forEach { path ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        path,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onRemoveAdditionalLibraryRoot(path) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
