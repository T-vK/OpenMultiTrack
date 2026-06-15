package org.openmultitrack.app.ui.daw

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.device.PrerequisiteItem
import org.openmultitrack.domain.mixer.AudioTransportHealth
import org.openmultitrack.domain.mixer.HealthIssue
import org.openmultitrack.domain.mixer.HealthLevel
import org.openmultitrack.domain.mixer.MixerHealthSnapshot
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.mixer.OscHealth
import org.openmultitrack.domain.mixer.ProbeState
import org.openmultitrack.domain.mixer.UsbHealth
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.displayLabel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerConnectivityScreen(
    mixer: MixerProfile,
    health: MixerHealthSnapshot,
    appMode: AppMode?,
    prerequisites: List<PrerequisiteItem>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMixerSettings: () -> Unit,
    onPrerequisiteAction: (org.openmultitrack.app.device.PrerequisiteKind) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mixer connectivity") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OverviewSection(
                    mixerName = mixer.displayName,
                    health = health,
                    appMode = appMode,
                )
            }
            item {
                UsbSection(usb = health.usb, onRefresh = onRefresh)
            }
            health.osc?.let { osc ->
                item {
                    OscSection(
                        osc = osc,
                        onOpenMixerSettings = onOpenMixerSettings,
                    )
                }
            }
            health.audio?.let { audio ->
                item {
                    AudioTransportSection(audio = audio)
                }
            }
            if (health.issues.isNotEmpty()) {
                item {
                    IssuesSection(issues = health.issues)
                }
            }
            if (prerequisites.isNotEmpty()) {
                item {
                    PrerequisitesSection(
                        prerequisites = prerequisites,
                        onAction = onPrerequisiteAction,
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Refresh connection status")
                }
            }
        }
    }
}

@Composable
private fun OverviewSection(
    mixerName: String,
    health: MixerHealthSnapshot,
    appMode: AppMode?,
) {
    ConnectivityCard(title = "Overview") {
        StatusRow("Mixer", mixerName)
        StatusRow("Overall", health.overall.label(), valueColor = health.overall.color())
        appMode?.let { mode ->
            StatusRow("Mode", mode.displayLabel)
        }
        StatusRow(
            "Updated",
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(health.updatedAtMs)),
        )
        val summary = buildList {
            if (health.usb.attached) add("USB attached")
            if (health.usb.probeState == ProbeState.OK) add("probe OK")
            health.osc?.takeIf { it.configured }?.let { add("OSC configured") }
            health.audio?.activityLabel?.let { add(it) }
        }.joinToString(" · ").ifBlank { "Waiting for connection details" }
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun UsbSection(
    usb: UsbHealth,
    onRefresh: () -> Unit,
) {
    ConnectivityCard(title = "USB") {
        StatusRow("Attached", if (usb.attached) "Yes" else "No", valueColor = boolColor(usb.attached))
        StatusRow(
            "Permission",
            if (usb.permissionGranted) "Granted" else "Required",
            valueColor = boolColor(usb.permissionGranted),
        )
        usb.deviceName?.let { StatusRow("Device", it) }
        usb.stableId?.let { StatusRow("Stable ID", it) }
        StatusRow("Probe", usb.probeState.label())
        usb.probeSummary?.let { StatusRow("Capabilities", it) }
        if (!usb.permissionGranted && usb.attached) {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Request USB access")
            }
        }
    }
}

@Composable
private fun OscSection(
    osc: OscHealth,
    onOpenMixerSettings: () -> Unit,
) {
    ConnectivityCard(title = "LAN / OSC") {
        StatusRow("Supported", if (osc.supported) "Yes" else "No")
        StatusRow(
            "Configured",
            if (osc.configured) "Yes" else "No",
            valueColor = boolColor(osc.configured),
        )
        osc.host?.let { StatusRow("Host", it) }
        if (osc.supported && !osc.configured) {
            OutlinedButton(
                onClick = onOpenMixerSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Set mixer IP in settings")
            }
        }
    }
}

@Composable
private fun AudioTransportSection(audio: AudioTransportHealth) {
    ConnectivityCard(title = "Audio transport") {
        StatusRow("Capture channels", audio.captureChannels.toString())
        StatusRow("Playback channels", audio.playbackChannels.toString())
        StatusRow("Recording", if (audio.isRecording) "Active" else "Idle")
        StatusRow("Playback", if (audio.isPlaying) "Active" else "Idle")
        StatusRow("Monitor", if (audio.isMonitoring) "On" else "Off")
        if (audio.isUsbDegraded) {
            StatusRow("USB stream", "Interrupted", valueColor = MaterialTheme.colorScheme.error)
        }
        audio.activityLabel?.let { StatusRow("Activity", it) }
    }
}

@Composable
private fun IssuesSection(issues: List<HealthIssue>) {
    ConnectivityCard(title = "Issues") {
        issues.forEachIndexed { index, issue ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            Text(
                text = issue.title,
                style = MaterialTheme.typography.titleSmall,
                color = issue.severity.color(),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = issue.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PrerequisitesSection(
    prerequisites: List<PrerequisiteItem>,
    onAction: (org.openmultitrack.app.device.PrerequisiteKind) -> Unit,
) {
    ConnectivityCard(title = "App permissions") {
        prerequisites.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            OutlinedButton(onClick = { onAction(item.kind) }) {
                Text(item.actionLabel)
            }
        }
    }
}

@Composable
private fun ConnectivityCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            content()
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun boolColor(ok: Boolean) =
    if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

@Composable
private fun HealthLevel.color() = when (this) {
    HealthLevel.OK -> MaterialTheme.colorScheme.primary
    HealthLevel.DEGRADED -> MaterialTheme.colorScheme.tertiary
    HealthLevel.BLOCKED -> MaterialTheme.colorScheme.error
    HealthLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun HealthLevel.label() = when (this) {
    HealthLevel.OK -> "Ready"
    HealthLevel.DEGRADED -> "Degraded"
    HealthLevel.BLOCKED -> "Blocked"
    HealthLevel.UNKNOWN -> "Unknown"
}

private fun ProbeState.label() = when (this) {
    ProbeState.NONE -> "Not probed"
    ProbeState.PROBING -> "Probing…"
    ProbeState.OK -> "OK"
    ProbeState.FAILED -> "Failed"
}
