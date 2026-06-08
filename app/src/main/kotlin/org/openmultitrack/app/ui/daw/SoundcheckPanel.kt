package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.service.SoundcheckSessionItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SoundcheckPanel(
    sessions: List<SoundcheckSessionItem>,
    selectedDir: String?,
    isPlaying: Boolean,
    positionSec: Float,
    durationSec: Float,
    onRefresh: () -> Unit,
    onSelectSession: (String) -> Unit,
    onSeek: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Session library", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Play multitrack recordings back to mixer USB returns.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh session list")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No completed sessions yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Record in Recording mode, then return here to play back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(sessions, key = { it.sessionDir }) { session ->
                    SoundcheckSessionRow(
                        session = session,
                        selected = session.sessionDir == selectedDir,
                        onClick = { onSelectSession(session.sessionDir) },
                    )
                }
            }
            if (selectedDir != null && durationSec > 0f) {
                Spacer(Modifier.height(12.dp))
                SoundcheckTransport(
                    isPlaying = isPlaying,
                    positionSec = positionSec,
                    durationSec = durationSec,
                    onSeek = onSeek,
                )
            }
        }
    }
}

@Composable
private fun SoundcheckSessionRow(
    session: SoundcheckSessionItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val date = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
        .format(Date(session.startedAtEpochMs))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$date · ${session.channelCount} ch · ${formatDuration(session.durationSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SoundcheckTransport(
    isPlaying: Boolean,
    positionSec: Float,
    durationSec: Float,
    onSeek: (Float) -> Unit,
) {
    var dragPosition by remember(positionSec) { mutableFloatStateOf(positionSec) }
    var dragging by remember { mutableStateOf(false) }
    val shownPosition = if (dragging) dragPosition else positionSec
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatDuration(shownPosition), style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(durationSec), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = shownPosition.coerceIn(0f, durationSec.coerceAtLeast(0.01f)),
            onValueChange = {
                dragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragPosition)
            },
            valueRange = 0f..durationSec.coerceAtLeast(0.01f),
            modifier = Modifier.fillMaxWidth(),
        )
        if (isPlaying) {
            Text(
                "Playing to USB returns — use Stop in the toolbar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(sec: Float): String {
    val total = sec.toInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
