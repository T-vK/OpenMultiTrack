package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.health.ConnectivitySummary
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.mixer.HealthLevel
import org.openmultitrack.domain.mixer.MixerHealthSnapshot

@Composable
fun SoundcheckSessionInfoBar(
    session: MixerSessionUiState,
    health: MixerHealthSnapshot? = null,
    connectivitySummary: ConnectivitySummary? = null,
    onOpenConnectivity: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val selected = session.soundcheckSessions.firstOrNull { it.sessionDir == session.selectedSoundcheckDir }
    val title = selected?.title ?: "No recording selected"
    val position = session.playbackPositionSec
    val duration = session.playbackDurationSec
    val hasSession = session.selectedSoundcheckDir != null
    val activity = session.activityStatus
    val showConnectivityIcons = activity == null && connectivitySummary != null
    val statusLine = when {
        activity != null -> activity.displayLabel
        else -> null
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (hasSession) {
                        "${formatTransportTime(position)} / ${formatTransportTime(duration)}"
                    } else {
                        "0:00 / 0:00"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .testTag(DawTransportSemantics.SOUNDCHECK_TRANSPORT_TEST_TAG)
                        .clearAndSetSemantics {
                            contentDescription = if (hasSession) {
                                DawTransportSemantics.SOUNDCHECK_TRANSPORT_PREFIX +
                                    "${formatTransportTime(position)} of ${formatTransportTime(duration)}"
                            } else {
                                DawTransportSemantics.SOUNDCHECK_TRANSPORT_PREFIX + "0:00 of 0:00"
                            }
                        },
                )
            }
            if (!statusLine.isNullOrBlank() || showConnectivityIcons) {
                val statusColor = when {
                    activity != null -> MaterialTheme.colorScheme.onSecondaryContainer
                    health?.primaryIssue?.severity == HealthLevel.BLOCKED -> MaterialTheme.colorScheme.error
                    health?.primaryIssue?.severity == HealthLevel.DEGRADED -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (activity?.showSpinner == true) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    if (showConnectivityIcons) {
                        MixerConnectivitySummaryIcons(
                            summary = connectivitySummary,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (!statusLine.isNullOrBlank()) {
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
