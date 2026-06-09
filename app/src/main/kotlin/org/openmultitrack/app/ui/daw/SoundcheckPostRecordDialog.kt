package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.SoundcheckLoadPromptState
import org.openmultitrack.app.service.MixerSessionUiState
import java.io.File

private enum class PostRecordStep {
    OPTIONS,
    RENAME,
}

@Composable
fun SoundcheckPostRecordDialog(
    prompt: SoundcheckLoadPromptState,
    session: MixerSessionUiState?,
    onDismiss: () -> Unit,
    onLoadSoundcheck: () -> Unit,
    onLoadSimplePlay: () -> Unit,
    onRename: (String) -> Unit,
) {
    var step by remember(prompt) { mutableStateOf(PostRecordStep.OPTIONS) }
    val defaultTitle = remember(prompt, session) {
        session?.soundcheckSessions?.firstOrNull { it.sessionDir == prompt.sessionDir }?.title
            ?: File(prompt.sessionDir).name
    }
    var renameText by remember(prompt, defaultTitle) { mutableStateOf(defaultTitle) }

    when (step) {
        PostRecordStep.OPTIONS -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Recording saved") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("What would you like to do with this recording?")
                    TextButton(
                        onClick = onLoadSoundcheck,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Load into Virtual Soundcheck")
                    }
                    TextButton(
                        onClick = onLoadSimplePlay,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Load into Simple Play")
                    }
                    TextButton(
                        onClick = { step = PostRecordStep.RENAME },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Change name")
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Do nothing")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
        PostRecordStep.RENAME -> AlertDialog(
            onDismissRequest = { step = PostRecordStep.OPTIONS },
            title = { Text("Rename recording") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = renameText.trim()
                        if (title.isNotEmpty()) {
                            onRename(title)
                        }
                        step = PostRecordStep.OPTIONS
                    },
                    enabled = renameText.trim().isNotEmpty(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { step = PostRecordStep.OPTIONS }) {
                    Text("Back")
                }
            },
        )
    }
}
