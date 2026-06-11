package org.openmultitrack.app.ui.routing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.routing.RoutingApplyPromptState
import org.openmultitrack.app.routing.RoutingOverrideKind
import org.openmultitrack.app.routing.RoutingRestorePromptState
import org.openmultitrack.mixer.behringer.XAirChannelInputState

@Composable
fun RoutingApplyDialog(
    prompt: RoutingApplyPromptState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (prompt.kind) {
        RoutingOverrideKind.RECORD -> "Route armed channels for recording?"
        RoutingOverrideKind.SOUNDCHECK -> "Route channels for soundcheck playback?"
    }
    val body = when (prompt.kind) {
        RoutingOverrideKind.RECORD ->
            "Temporarily switch ${prompt.channelCount} armed channel(s) to A/D input. " +
                "Original mixer routing will be restored when recording stops."
        RoutingOverrideKind.SOUNDCHECK ->
            "Temporarily switch ${prompt.channelCount} channel(s) with tracks to USB playback input. " +
                "Original routing will be restored when playback stops."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Skip") }
        },
    )
}

@Composable
fun RoutingRestoreDialog(
    prompt: RoutingRestorePromptState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore mixer input routing?") },
        text = {
            Column {
                Text(
                    "Revert temporary routing changes from ${prompt.kind.name.lowercase()} mode.",
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (prompt.conflicts.isNotEmpty()) {
                    Text(
                        "Some channels were changed on the mixer during playback:",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(prompt.conflicts) { conflict ->
                            Text(
                                "Ch ${conflict.channelIndex + 1}: was ${conflict.baseline.describe()}, " +
                                    "now ${conflict.live.describe()}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Restore") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep current") }
        },
    )
}

@Composable
fun MixerInputSourcesScreen(
    mixerName: String,
    channels: Map<Int, XAirChannelInputState>,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Input sources — $mixerName") },
        text = {
            when {
                loading -> Text("Reading mixer over OSC…")
                error != null -> Text(error)
                channels.isEmpty() -> Text("No channel data. Check Wi‑Fi and mixer IP in mixer settings.")
                else -> LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(channels.keys.sorted()) { ch ->
                        val st = channels[ch]!!
                        Text(
                            "Ch ${ch + 1}: ${st.describe()} (${if (st.usesUsbReturn) "USB" else "A/D"})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh) { Text("Refresh") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
