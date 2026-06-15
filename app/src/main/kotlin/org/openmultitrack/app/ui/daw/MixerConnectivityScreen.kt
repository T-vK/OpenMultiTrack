package org.openmultitrack.app.ui.daw

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.health.ConnectivityAction
import org.openmultitrack.app.health.ConnectivityCheckItem
import org.openmultitrack.app.health.ConnectivityChecklist
import org.openmultitrack.app.health.ConnectivitySection
import org.openmultitrack.app.health.ConnectivityStatus
import org.openmultitrack.domain.mixer.HealthLevel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixerConnectivityScreen(
    checklist: ConnectivityChecklist,
    onDismiss: () -> Unit,
    onAction: (ConnectivityAction) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var expandedItemId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mixer connectivity") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onAction(ConnectivityAction.REFRESH) }) {
                        Text("Refresh")
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
                ConnectivitySummaryHeader(checklist)
            }
            items(checklist.sections, key = { it.group.name }) { section ->
                ConnectivitySectionCard(
                    section = section,
                    expandedItemId = expandedItemId,
                    onToggleExpand = { id ->
                        expandedItemId = if (expandedItemId == id) null else id
                    },
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ConnectivitySummaryHeader(checklist: ConnectivityChecklist) {
    val (summary, summaryColor) = when (checklist.overall) {
        HealthLevel.OK -> "All essential connections look good" to MaterialTheme.colorScheme.primary
        HealthLevel.DEGRADED -> "Some items need attention" to MaterialTheme.colorScheme.tertiary
        HealthLevel.BLOCKED -> "Blocked — fix items below to continue" to MaterialTheme.colorScheme.error
        HealthLevel.UNKNOWN -> "Checking connections…" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = checklist.mixerName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = summaryColor,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(checklist.updatedAtMs))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ConnectivitySectionCard(
    section: ConnectivitySection,
    expandedItemId: String?,
    onToggleExpand: (String) -> Unit,
    onAction: (ConnectivityAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            section.items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
                ConnectivityCheckRow(
                    item = item,
                    expanded = expandedItemId == item.id,
                    onToggleExpand = {
                        if (!item.technicalDetail.isNullOrBlank()) {
                            onToggleExpand(item.id)
                        }
                    },
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ConnectivityCheckRow(
    item: ConnectivityCheckItem,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onAction: (ConnectivityAction) -> Unit,
) {
    val hasTechnical = !item.technicalDetail.isNullOrBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasTechnical) {
                    Modifier.clickable(onClick = onToggleExpand)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusGlyph(status = item.status)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                item.detail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (hasTechnical) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide details" else "Show details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            item.action?.let { action ->
                TextButton(onClick = { onAction(action) }) {
                    Text(item.actionLabel ?: "Fix")
                }
            }
        }
        AnimatedVisibility(visible = expanded && hasTechnical) {
            Text(
                text = item.technicalDetail.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun StatusGlyph(status: ConnectivityStatus) {
    val (bg, fg, icon) = when (status) {
        ConnectivityStatus.OK -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            Icons.Default.Check,
        )
        ConnectivityStatus.WARNING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.Remove,
        )
        ConnectivityStatus.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            Icons.Default.Close,
        )
        ConnectivityStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary,
            Icons.Default.Sync,
        )
        ConnectivityStatus.OFF,
        ConnectivityStatus.NOT_APPLICABLE,
        -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Remove,
        )
        ConnectivityStatus.UNKNOWN -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Remove,
        )
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = status.name,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
    }
}
