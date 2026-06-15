package org.openmultitrack.app.routing

import org.openmultitrack.app.data.MixerRoutingAutomationConfig
import org.openmultitrack.app.data.RoutingAutomationMethod
import org.openmultitrack.mixer.behringer.MixerSnapshotOption

fun MixerRoutingAutomationConfig.snapshotSlotFor(kind: RoutingOverrideKind): Int = when (kind) {
    RoutingOverrideKind.IDLE -> idleSnapshotSlot
    RoutingOverrideKind.RECORD -> recordSnapshotSlot
    RoutingOverrideKind.SOUNDCHECK -> soundcheckSnapshotSlot
}

fun snapshotDisplayName(
    slot: Int,
    snapshots: List<MixerSnapshotOption>,
): String? {
    if (slot !in 1..64) return null
    val name = snapshots.find { it.slot == slot }?.name?.takeIf { it.isNotBlank() }
    return name ?: "slot $slot"
}

fun RoutingApplyPromptState.withSnapshotName(
    snapshots: List<MixerSnapshotOption>,
): RoutingApplyPromptState {
    if (method != RoutingAutomationMethod.SNAPSHOT_SLOT || snapshotSlot !in 1..64) return this
    return copy(snapshotName = snapshotDisplayName(snapshotSlot, snapshots))
}

fun RoutingRestorePromptState.withSnapshotName(
    snapshots: List<MixerSnapshotOption>,
): RoutingRestorePromptState {
    if (method != RoutingAutomationMethod.SNAPSHOT_SLOT || snapshotSlot !in 1..64) return this
    return copy(snapshotName = snapshotDisplayName(snapshotSlot, snapshots))
}
