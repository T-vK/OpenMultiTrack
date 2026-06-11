package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.PostRecordPromptMode
import org.openmultitrack.app.SoundcheckLoadPromptState
import org.openmultitrack.app.service.MixerSessionUiState
import java.io.File

@Composable
fun SoundcheckPostRecordDialog(
    prompt: SoundcheckLoadPromptState,
    session: MixerSessionUiState?,
    onDismiss: () -> Unit,
    onLoadSoundcheck: () -> Unit,
    onLoadSimplePlay: () -> Unit,
    onRename: (String) -> Unit,
) {
    val defaultTitle = remember(prompt, session) {
        session?.soundcheckSessions?.firstOrNull { it.sessionDir == prompt.sessionDir }?.title
            ?: File(prompt.sessionDir).name
    }
    var renameText by remember(prompt) { mutableStateOf(defaultTitle) }
    var lastAppliedTitle by remember(prompt) { mutableStateOf(defaultTitle) }

    LaunchedEffect(defaultTitle) {
        if (renameText == lastAppliedTitle) {
            renameText = defaultTitle
            lastAppliedTitle = defaultTitle
        }
    }

    fun applyRename() {
        val title = renameText.trim()
        if (title.isNotEmpty() && title != lastAppliedTitle) {
            onRename(title)
            lastAppliedTitle = title
        }
    }

    val showActions = prompt.mode == PostRecordPromptMode.FULL

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text("Recording saved") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showActions) {
                    Text(
                        "What would you like to do with this recording?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = ::applyRename,
                        enabled = renameText.trim().isNotEmpty() && renameText.trim() != lastAppliedTitle,
                    ) {
                        Text("OK")
                    }
                }
                if (showActions) {
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = onLoadSoundcheck,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Soundcheck with this recording",
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onLoadSimplePlay,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Play this recording",
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("Dismiss", modifier = Modifier.padding(start = 6.dp))
            }
        },
    )
}
