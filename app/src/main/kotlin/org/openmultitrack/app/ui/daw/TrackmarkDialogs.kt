package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.sessionio.session.SessionCueFile
import org.openmultitrack.sessionio.session.SessionTrackmark

@Composable
fun AddTrackmarkDialog(
    initialPositionSec: Float,
    defaultTitle: String,
    durationSec: Float,
    onDismiss: () -> Unit,
    onConfirm: (title: String, startSec: Float) -> Unit,
) {
    var title by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    var timeText by remember(initialPositionSec) {
        mutableStateOf(SessionCueFile.formatUserTimestamp(initialPositionSec))
    }
    var timeError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add trackmark") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = {
                        timeText = it
                        timeError = null
                    },
                    label = { Text("Timestamp") },
                    supportingText = {
                        Text(
                            timeError ?: "Use M:SS, H:MM:SS, or seconds",
                            color = if (timeError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = SessionCueFile.parseUserTimestamp(timeText)
                    if (parsed == null) {
                        timeError = "Invalid timestamp"
                        return@TextButton
                    }
                    if (parsed > durationSec) {
                        timeError = "Beyond recording length"
                        return@TextButton
                    }
                    onConfirm(title, parsed)
                },
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
fun TrackmarkListDialog(
    trackmarks: List<SessionTrackmark>,
    durationSec: Float,
    currentPositionSec: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit,
) {
    val sorted = remember(trackmarks) { trackmarks.sortedBy { it.startSec } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trackmarks") },
        text = {
            if (sorted.isEmpty()) {
                Text("No trackmarks yet. Use the bookmark button to add one.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(sorted) { index, mark ->
                        val endSec = sorted.getOrNull(index + 1)?.startSec ?: durationSec
                        val lengthSec = (endSec - mark.startSec).coerceAtLeast(0f)
                        val isActive = currentPositionSec >= mark.startSec &&
                            (index == sorted.lastIndex || currentPositionSec < endSec)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(mark.startSec) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    mark.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    formatTransportTime(mark.startSec),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                formatTransportTime(lengthSec),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index < sorted.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
