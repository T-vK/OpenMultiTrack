package org.openmultitrack.app.ui.daw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.mixer.HealthLevel
import org.openmultitrack.domain.mixer.MixerHealthSnapshot

@Composable
fun SoundcheckSessionInfoBar(
    session: MixerSessionUiState,
    health: MixerHealthSnapshot? = null,
    modifier: Modifier = Modifier,
) {
    val selected = session.soundcheckSessions.firstOrNull { it.sessionDir == session.selectedSoundcheckDir }
    val title = selected?.title ?: "No recording selected"
    val position = session.playbackPositionSec
    val duration = session.playbackDurationSec
    val hasSession = session.selectedSoundcheckDir != null
    val statusLine = health?.let { snapshot ->
        snapshot.primaryIssue?.detail
            ?: snapshot.usb.probeSummary?.let { "USB ready — $it" }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
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
            if (!statusLine.isNullOrBlank()) {
                val statusColor = when (health?.primaryIssue?.severity ?: health?.overall) {
                    HealthLevel.BLOCKED -> MaterialTheme.colorScheme.error
                    HealthLevel.DEGRADED -> MaterialTheme.colorScheme.tertiary
                    HealthLevel.OK -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
