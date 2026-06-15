package org.openmultitrack.app.data

enum class RoutingAutomationTrigger {
    /** Recall when switching app mode (multitrack record ↔ soundcheck). */
    ON_MODE_ENTER,
    /** Recall on record / stop / play / stop transport buttons. */
    ON_TRANSPORT_BUTTON,
}

enum class RoutingAutomationLevel {
    OFF,
    PROMPT,
    AUTO,
}

enum class RoutingAutomationMethod {
    /** Set `/ch/NN/config/insrc`, `rtnsrc`, `preamp/rtnsw` per channel. */
    PER_CHANNEL,
    /** Recall `/-snap/load` mixer snapshot slots. */
    SNAPSHOT_SLOT,
}

enum class RoutingRestorePolicy {
    /** Always write captured baseline on restore. */
    STRICT,
    /** Skip channels the engineer changed away from our override value. */
    RESPECT_LIVE,
    /** Show diff and let user choose (PROMPT level only). */
    ASK_ON_CONFLICT,
    /** Skip restore on transport stop / mode exit. */
    NONE,
    /** Recall [MixerRoutingAutomationConfig.restoreSnapshotSlot] (snapshot mode). */
    RECALL_SNAPSHOT,
}

data class MixerRoutingAutomationConfig(
    val level: RoutingAutomationLevel = RoutingAutomationLevel.PROMPT,
    val method: RoutingAutomationMethod = RoutingAutomationMethod.PER_CHANNEL,
    val trigger: RoutingAutomationTrigger = RoutingAutomationTrigger.ON_TRANSPORT_BUTTON,
    val restorePolicy: RoutingRestorePolicy = RoutingRestorePolicy.RESPECT_LIVE,
    val idleSnapshotSlot: Int = 0,
    val recordSnapshotSlot: Int = 0,
    val soundcheckSnapshotSlot: Int = 0,
    /** Snapshot recalled on restore when [restorePolicy] is [RoutingRestorePolicy.RECALL_SNAPSHOT]. */
    val restoreSnapshotSlot: Int = 0,
    /** When true, restore even if live routing diverged (expert). */
    val forceRestoreOnConflict: Boolean = false,
)

fun MixerRoutingAutomationConfig.snapshotRestoreSlot(): Int? {
    if (restorePolicy == RoutingRestorePolicy.NONE) return null
    if (method != RoutingAutomationMethod.SNAPSHOT_SLOT) return null
    return when (restorePolicy) {
        RoutingRestorePolicy.RECALL_SNAPSHOT ->
            restoreSnapshotSlot.takeIf { it in 1..64 }
        else -> idleSnapshotSlot.takeIf { it in 1..64 }
    }
}

fun MixerRoutingAutomationConfig.shouldRestoreOnTransportStop(): Boolean {
    if (level == RoutingAutomationLevel.OFF) return false
    if (trigger != RoutingAutomationTrigger.ON_TRANSPORT_BUTTON) return false
    return when (method) {
        RoutingAutomationMethod.SNAPSHOT_SLOT -> snapshotRestoreSlot() != null
        RoutingAutomationMethod.PER_CHANNEL -> restorePolicy != RoutingRestorePolicy.NONE
    }
}
