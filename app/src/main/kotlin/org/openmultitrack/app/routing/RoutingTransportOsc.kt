package org.openmultitrack.app.routing

import org.openmultitrack.app.data.MixerRoutingAutomationConfig
import org.openmultitrack.app.data.RoutingAutomationLevel
import org.openmultitrack.app.data.RoutingAutomationMethod
import org.openmultitrack.app.data.RoutingAutomationTrigger
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.mixer.behringer.XAirInputSourceCatalog

/** Decides when transport buttons should run OSC routing (vs mode-enter only). */
object RoutingTransportOsc {
    fun willApplyOnRecordButton(
        profile: MixerProfile,
        config: MixerRoutingAutomationConfig,
        armedChannels: Set<Int>,
    ): Boolean {
        if (!RoutingOverrideCoordinator.isEligible(profile, config)) return false
        if (config.trigger != RoutingAutomationTrigger.ON_TRANSPORT_BUTTON) return false
        return when (config.method) {
            RoutingAutomationMethod.SNAPSHOT_SLOT ->
                config.recordSnapshotSlot in 1..64
            RoutingAutomationMethod.PER_CHANNEL ->
                XAirInputSourceCatalog.routableIndices(armedChannels).isNotEmpty()
        }
    }

    fun willApplyOnSoundcheckPlay(
        profile: MixerProfile,
        config: MixerRoutingAutomationConfig,
        trackChannels: Set<Int>,
    ): Boolean {
        if (!RoutingOverrideCoordinator.isEligible(profile, config)) return false
        if (config.trigger != RoutingAutomationTrigger.ON_TRANSPORT_BUTTON) return false
        return when (config.method) {
            RoutingAutomationMethod.SNAPSHOT_SLOT ->
                config.soundcheckSnapshotSlot in 1..64
            RoutingAutomationMethod.PER_CHANNEL ->
                XAirInputSourceCatalog.routableIndices(trackChannels).isNotEmpty()
        }
    }

    fun willRestoreOnTransportStop(config: MixerRoutingAutomationConfig): Boolean {
        if (config.level == RoutingAutomationLevel.OFF) return false
        if (config.trigger != RoutingAutomationTrigger.ON_TRANSPORT_BUTTON) return false
        return when (config.method) {
            RoutingAutomationMethod.SNAPSHOT_SLOT ->
                config.idleSnapshotSlot in 1..64
            RoutingAutomationMethod.PER_CHANNEL -> true
        }
    }
}
