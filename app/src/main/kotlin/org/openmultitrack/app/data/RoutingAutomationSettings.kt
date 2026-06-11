package org.openmultitrack.app.data

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
}

data class MixerRoutingAutomationConfig(
    val level: RoutingAutomationLevel = RoutingAutomationLevel.PROMPT,
    val method: RoutingAutomationMethod = RoutingAutomationMethod.PER_CHANNEL,
    val restorePolicy: RoutingRestorePolicy = RoutingRestorePolicy.RESPECT_LIVE,
    val recordSnapshotSlot: Int = 0,
    val soundcheckSnapshotSlot: Int = 0,
    /** When true, restore even if live routing diverged (expert). */
    val forceRestoreOnConflict: Boolean = false,
)
