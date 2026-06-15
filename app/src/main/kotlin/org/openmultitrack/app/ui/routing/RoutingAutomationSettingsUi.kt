package org.openmultitrack.app.ui.routing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openmultitrack.app.data.MixerRoutingAutomationConfig
import org.openmultitrack.app.data.RoutingAutomationMethod
import org.openmultitrack.app.data.RoutingAutomationTrigger
import org.openmultitrack.app.data.RoutingRestorePolicy
import org.openmultitrack.mixer.behringer.MixerSnapshotOption

fun snapshotSlotOptions(
    snapshots: List<MixerSnapshotOption>,
    selectedSlot: Int,
): List<Int> = buildList {
    add(0)
    snapshots.filter { it.name.isNotBlank() }.forEach { add(it.slot) }
    if (selectedSlot > 0 && selectedSlot !in this) add(selectedSlot)
}.distinct().sorted()

fun snapshotSlotLabel(
    slot: Int,
    snapshots: List<MixerSnapshotOption>,
    loading: Boolean,
): String = when (slot) {
    0 -> "Not set"
    else -> {
        val pad = slot.toString().padStart(2, '0')
        val name = snapshots.find { it.slot == slot }?.name.orEmpty()
        when {
            name.isNotBlank() -> name
            loading -> "Slot $pad…"
            else -> "Slot $pad (empty)"
        }
    }
}

fun snapshotDropdownEnabled(
    loading: Boolean,
    snapshots: List<MixerSnapshotOption>,
    selectedSlot: Int,
): Boolean {
    if (!loading) return true
    if (selectedSlot > 0) {
        val selectedName = snapshots.find { it.slot == selectedSlot }?.name
        if (!selectedName.isNullOrBlank()) return true
    }
    return snapshots.any { it.name.isNotBlank() }
}

fun snapshotDropdownButtonText(
    selectedSlot: Int,
    snapshots: List<MixerSnapshotOption>,
    loading: Boolean,
): String? {
    if (!loading) return null
    if (selectedSlot == 0 && snapshots.none { it.name.isNotBlank() }) {
        return "Loading snapshots…"
    }
    return null
}

fun snapshotLoadingMenuHint(loading: Boolean, scannedCount: Int): String? = when {
    !loading -> null
    scannedCount >= 64 -> "Refreshing from mixer…"
    scannedCount > 0 -> "Loading more snapshots… ($scannedCount/64 scanned)"
    else -> null
}

fun snapshotPickerDescription(
    loading: Boolean,
    namedCount: Int,
    scannedCount: Int,
): String = when {
    loading && namedCount > 0 && scannedCount == 0 ->
        "$namedCount snapshots shown — refreshing from mixer."
    loading && namedCount == 0 && scannedCount == 0 ->
        "Reading snapshot names from the mixer…"
    loading && namedCount == 0 ->
        "Reading snapshot names from the mixer… ($scannedCount/64 slots scanned)"
    loading ->
        "$namedCount snapshots found — still reading ($scannedCount/64 slots scanned)."
    namedCount > 0 -> "$namedCount named snapshots on the mixer."
    else -> "No named snapshots found on the mixer."
}

fun restorePolicyLabel(
    policy: RoutingRestorePolicy,
    method: RoutingAutomationMethod,
): String = when (policy) {
    RoutingRestorePolicy.NONE -> "Do nothing"
    RoutingRestorePolicy.STRICT ->
        if (method == RoutingAutomationMethod.SNAPSHOT_SLOT) {
            "Recall idle snapshot"
        } else {
            "Always restore captured baseline"
        }
    RoutingRestorePolicy.RESPECT_LIVE -> "Skip engineer-changed channels"
    RoutingRestorePolicy.ASK_ON_CONFLICT -> "Ask when conflicts detected"
    RoutingRestorePolicy.RECALL_SNAPSHOT -> "Recall a specific snapshot"
}

fun restorePolicyOptions(method: RoutingAutomationMethod): List<RoutingRestorePolicy> =
    if (method == RoutingAutomationMethod.SNAPSHOT_SLOT) {
        listOf(
            RoutingRestorePolicy.NONE,
            RoutingRestorePolicy.RECALL_SNAPSHOT,
            RoutingRestorePolicy.STRICT,
        )
    } else {
        listOf(
            RoutingRestorePolicy.NONE,
            RoutingRestorePolicy.STRICT,
            RoutingRestorePolicy.RESPECT_LIVE,
            RoutingRestorePolicy.ASK_ON_CONFLICT,
        )
    }

@Composable
fun RoutingSnapshotDropdown(
    label: String,
    description: String,
    snapshots: List<MixerSnapshotOption>,
    loading: Boolean,
    scannedCount: Int,
    selectedSlot: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = snapshotSlotOptions(snapshots, selectedSlot)
    val enabled = snapshotDropdownEnabled(loading, snapshots, selectedSlot)
    val buttonText = snapshotDropdownButtonText(selectedSlot, snapshots, loading)
        ?: snapshotSlotLabel(selectedSlot, snapshots, loading)
    val loadingHint = snapshotLoadingMenuHint(loading, scannedCount)

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Box {
            OutlinedButton(
                onClick = { if (enabled) expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (loading && buttonText.startsWith("Loading")) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(buttonText, modifier = Modifier.weight(1f))
                    Icon(
                        androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                loadingHint?.let { hint ->
                    DropdownMenuItem(
                        text = { Text(hint, style = MaterialTheme.typography.bodySmall) },
                        onClick = {},
                        enabled = false,
                    )
                }
                options.forEach { slot ->
                    DropdownMenuItem(
                        text = { Text(snapshotSlotLabel(slot, snapshots, loading)) },
                        onClick = {
                            expanded = false
                            onSelect(slot)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun RoutingEnumDropdown(
    label: String,
    description: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        options.getOrElse(selectedIndex) { options.firstOrNull().orEmpty() },
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                        contentDescription = null,
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option, modifier = Modifier.widthIn(max = 320.dp)) },
                        onClick = {
                            expanded = false
                            onSelect(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun OscMixerRoutingSettingsPage(
    config: MixerRoutingAutomationConfig,
    snapshots: List<MixerSnapshotOption>,
    snapshotsLoading: Boolean,
    snapshotsScanned: Int,
    onConfigChange: (MixerRoutingAutomationConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val namedCount = snapshots.count { it.name.isNotBlank() }
    val snapshotDescription = snapshotPickerDescription(
        loading = snapshotsLoading,
        namedCount = namedCount,
        scannedCount = snapshotsScanned,
    )

    Column(modifier = modifier) {
        Text(
            "Switch mixer input routing over LAN when recording or playing back in the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val methodLabels = RoutingAutomationMethod.entries.map {
            when (it) {
                RoutingAutomationMethod.PER_CHANNEL -> "Per-channel input sources"
                RoutingAutomationMethod.SNAPSHOT_SLOT -> "Mixer snapshot slots"
            }
        }
        RoutingEnumDropdown(
            label = "Automation method",
            description = "Per-channel OSC or recall named mixer snapshots.",
            options = methodLabels,
            selectedIndex = config.method.ordinal,
            onSelect = { index ->
                onConfigChange(config.copy(method = RoutingAutomationMethod.entries[index]))
            },
        )

        if (config.method == RoutingAutomationMethod.SNAPSHOT_SLOT) {
            val triggerLabels = RoutingAutomationTrigger.entries.map {
                when (it) {
                    RoutingAutomationTrigger.ON_MODE_ENTER ->
                        "When entering record or soundcheck mode"
                    RoutingAutomationTrigger.ON_TRANSPORT_BUTTON ->
                        "On record, play, and stop buttons"
                }
            }
            RoutingEnumDropdown(
                label = "When to recall snapshots",
                description = "Recall on app mode change, or on record / play / stop buttons.",
                options = triggerLabels,
                selectedIndex = config.trigger.ordinal,
                onSelect = { index ->
                    onConfigChange(config.copy(trigger = RoutingAutomationTrigger.entries[index]))
                },
            )
            RoutingSnapshotDropdown(
                label = "Idle snapshot",
                description = "Snapshot for when not recording or playing back. $snapshotDescription",
                snapshots = snapshots,
                loading = snapshotsLoading,
                scannedCount = snapshotsScanned,
                selectedSlot = config.idleSnapshotSlot,
                onSelect = { onConfigChange(config.copy(idleSnapshotSlot = it)) },
            )
            RoutingSnapshotDropdown(
                label = "Record snapshot",
                description = "Snapshot recalled when recording starts. $snapshotDescription",
                snapshots = snapshots,
                loading = snapshotsLoading,
                scannedCount = snapshotsScanned,
                selectedSlot = config.recordSnapshotSlot,
                onSelect = { onConfigChange(config.copy(recordSnapshotSlot = it)) },
            )
            RoutingSnapshotDropdown(
                label = "Soundcheck snapshot",
                description = "Snapshot recalled when soundcheck playback starts. $snapshotDescription",
                snapshots = snapshots,
                loading = snapshotsLoading,
                scannedCount = snapshotsScanned,
                selectedSlot = config.soundcheckSnapshotSlot,
                onSelect = { onConfigChange(config.copy(soundcheckSnapshotSlot = it)) },
            )
        }

        val restorePolicies = restorePolicyOptions(config.method)
        val restoreLabels = restorePolicies.map { restorePolicyLabel(it, config.method) }
        val restoreIndex = restorePolicies.indexOf(config.restorePolicy).coerceAtLeast(0)
        RoutingEnumDropdown(
            label = "Restore policy",
            description = "What to do on transport stop or when leaving an override.",
            options = restoreLabels,
            selectedIndex = restoreIndex,
            onSelect = { index ->
                onConfigChange(config.copy(restorePolicy = restorePolicies[index]))
            },
        )

        if (
            config.method == RoutingAutomationMethod.SNAPSHOT_SLOT &&
            config.restorePolicy == RoutingRestorePolicy.RECALL_SNAPSHOT
        ) {
            RoutingSnapshotDropdown(
                label = "Restore snapshot",
                description = "Snapshot recalled when restoring after record or playback. $snapshotDescription",
                snapshots = snapshots,
                loading = snapshotsLoading,
                scannedCount = snapshotsScanned,
                selectedSlot = config.restoreSnapshotSlot,
                onSelect = { onConfigChange(config.copy(restoreSnapshotSlot = it)) },
            )
        }
    }
}
