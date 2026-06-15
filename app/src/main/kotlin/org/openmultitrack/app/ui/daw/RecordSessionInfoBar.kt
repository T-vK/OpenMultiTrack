package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.health.ConnectivitySummary
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.mixer.HealthLevel
import org.openmultitrack.domain.mixer.MixerHealthSnapshot

@Composable
fun RecordSessionInfoBar(
    session: MixerSessionUiState,
    health: MixerHealthSnapshot? = null,
    connectivitySummary: ConnectivitySummary? = null,
    onOpenConnectivity: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val activity = session.activityStatus
    val showConnectivityIcons = activity == null &&
        !session.isRecording &&
        !session.isMonitoring &&
        connectivitySummary != null
    val statusLine = when {
        activity != null -> activity.displayLabel
        session.isRecording -> "🔴 Recording to disk"
        session.isMonitoring -> "🎧 Monitoring live inputs"
        else -> null
    }

    if (!showConnectivityIcons && statusLine == null) return

    val showSpinner = activity?.showSpinner == true
    val progress = activity?.progress
    val statusColor = when {
        activity != null -> MaterialTheme.colorScheme.onSecondaryContainer
        session.isRecording -> MaterialTheme.colorScheme.error
        health?.primaryIssue?.severity == HealthLevel.BLOCKED -> MaterialTheme.colorScheme.error
        health?.primaryIssue?.severity == HealthLevel.DEGRADED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = if (activity != null) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onOpenConnectivity != null) {
                    Modifier.clickable(onClick = onOpenConnectivity)
                } else {
                    Modifier
                },
            ),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                if (showConnectivityIcons) {
                    MixerConnectivitySummaryIcons(
                        summary = connectivitySummary,
                        modifier = Modifier.weight(1f),
                    )
                } else if (statusLine != null) {
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }
}
