package org.openmultitrack.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.data.StorageVolumeOption
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsStorageSection(
    effectiveStorageRootPath: String,
    storageRootPath: String?,
    additionalLibraryRoots: List<String>,
    redundantRecordingRoots: List<String>,
    autoScanRemovableMedia: Boolean,
    alwaysIncludeOpenMultiTrackFolders: Boolean,
    localSpillBufferEnabled: Boolean,
    localSpillBufferMinutes: Int,
    minFreeStorageBytes: Long,
    storageVolumeOptions: List<StorageVolumeOption>,
    onSetStorageRootPath: (String?) -> Unit,
    onAddAdditionalLibraryRoot: (String) -> Unit,
    onRemoveAdditionalLibraryRoot: (String) -> Unit,
    onAddRedundantRecordingRoot: (String) -> Unit,
    onRemoveRedundantRecordingRoot: (String) -> Unit,
    onAutoScanRemovableMediaChange: (Boolean) -> Unit,
    onAlwaysIncludeOpenMultiTrackFoldersChange: (Boolean) -> Unit,
    onLocalSpillBufferEnabledChange: (Boolean) -> Unit,
    onLocalSpillBufferMinutesChange: (Int) -> Unit,
    onMinFreeStorageBytesChange: (Long) -> Unit,
) {
    var addLibraryDialog by remember { mutableStateOf(false) }
    var addMirrorDialog by remember { mutableStateOf(false) }
    var newPathText by remember { mutableStateOf("") }

    if (addLibraryDialog) {
        StoragePathPickerDialog(
            title = "Add library location",
            description = "Extra folders to search for completed recordings.",
            newPathText = newPathText,
            onPathTextChange = { newPathText = it },
            storageVolumeOptions = storageVolumeOptions,
            excludePaths = additionalLibraryRoots,
            onDismiss = { addLibraryDialog = false },
            onConfirm = {
                onAddAdditionalLibraryRoot(it)
                addLibraryDialog = false
                newPathText = ""
            },
        )
    }
    if (addMirrorDialog) {
        StoragePathPickerDialog(
            title = "Add mirror recording location",
            description = "Record the same take to this folder at the same time (redundancy).",
            newPathText = newPathText,
            onPathTextChange = { newPathText = it },
            storageVolumeOptions = storageVolumeOptions,
            excludePaths = redundantRecordingRoots,
            onDismiss = { addMirrorDialog = false },
            onConfirm = {
                onAddRedundantRecordingRoot(it)
                addMirrorDialog = false
                newPathText = ""
            },
        )
    }

    SettingsSubsectionHeader(
        title = "Recording output",
        description = "Where new takes are written.",
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            effectiveStorageRootPath,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp),
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
    }
    PathListBlock(
        title = "Mirror locations",
        description = "Write every take to multiple devices at once.",
        roots = redundantRecordingRoots,
        onAdd = { addMirrorDialog = true },
        onRemove = onRemoveRedundantRecordingRoot,
    )

    SettingsSubsectionHeader(
        title = "Fault tolerance",
        description = "Keep recording when a card disconnects or runs low on space.",
    )
    SettingsSwitchRow(
        title = "Local spill buffer",
        description = "Keep recent audio on internal storage during brief SD/USB disconnects.",
        checked = localSpillBufferEnabled,
        onCheckedChange = onLocalSpillBufferEnabledChange,
    )
    if (localSpillBufferEnabled) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                "Spill window: $localSpillBufferMinutes min",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = localSpillBufferMinutes.toFloat(),
                onValueChange = { onLocalSpillBufferMinutesChange(it.roundToInt()) },
                valueRange = 1f..30f,
                steps = 28,
            )
        }
    }
    MinFreeStorageField(
        minFreeStorageBytes = minFreeStorageBytes,
        onMinFreeStorageBytesChange = onMinFreeStorageBytesChange,
    )

    SettingsSubsectionHeader(
        title = "Session library",
        description = "Where completed recordings are listed for playback.",
    )
    SettingsSwitchRow(
        title = "Auto-include OpenMultiTrack folders",
        description = "Add OpenMultiTrack folders from SD/USB drives to the library.",
        checked = alwaysIncludeOpenMultiTrackFolders,
        onCheckedChange = onAlwaysIncludeOpenMultiTrackFoldersChange,
    )
    SettingsSwitchRow(
        title = "Scan removable media",
        description = "Search SD cards and USB drives for OpenMultiTrack/Recordings.",
        checked = autoScanRemovableMedia,
        onCheckedChange = onAutoScanRemovableMediaChange,
    )
    PathListBlock(
        title = "Extra library folders",
        description = "Additional paths to search for sessions.",
        roots = additionalLibraryRoots,
        onAdd = { addLibraryDialog = true },
        onRemove = onRemoveAdditionalLibraryRoot,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun SettingsSubsectionHeader(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoragePathPickerDialog(
    title: String,
    description: String,
    newPathText: String,
    onPathTextChange: (String) -> Unit,
    storageVolumeOptions: List<StorageVolumeOption>,
    excludePaths: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = newPathText,
                    onValueChange = onPathTextChange,
                    singleLine = true,
                    label = { Text("Folder path") },
                    modifier = Modifier.fillMaxWidth(),
                )
                storageVolumeOptions
                    .filter { option -> option.path !in excludePaths }
                    .take(6)
                    .forEach { option ->
                        TextButton(
                            onClick = { onPathTextChange(option.path) },
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
                    if (path.isNotEmpty()) onConfirm(path)
                },
                enabled = newPathText.trim().isNotEmpty(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PathListBlock(
    title: String,
    description: String,
    roots: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add location")
            }
        }
        if (roots.isEmpty()) {
            Text(
                "None configured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        } else {
            roots.forEach { path ->
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
                    IconButton(onClick = { onRemove(path) }) {
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

@Composable
private fun MinFreeStorageField(
    minFreeStorageBytes: Long,
    onMinFreeStorageBytesChange: (Long) -> Unit,
) {
    var text by remember(minFreeStorageBytes) {
        mutableStateOf(StorageSizeInput.format(minFreeStorageBytes))
    }
    var focused by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Minimum free space", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Stop writing to a device below this threshold. Leave empty to disable. Recording continues on the spill buffer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    val wasFocused = focused
                    focused = focus.isFocused
                    if (wasFocused && !focus.isFocused) {
                        val parsed = StorageSizeInput.parse(text)
                        if (parsed == null && text.isNotBlank()) {
                            error = "Use a value like 500 MB or 1.5 GB"
                        } else {
                            val bytes = parsed ?: 0L
                            onMinFreeStorageBytesChange(bytes)
                            text = StorageSizeInput.format(bytes)
                            error = null
                        }
                    }
                },
            singleLine = true,
            label = { Text("Threshold") },
            placeholder = { Text("e.g. 500 MB (empty = off)") },
            isError = error != null,
            supportingText = error?.let { err -> { Text(err) } },
        )
    }
}
