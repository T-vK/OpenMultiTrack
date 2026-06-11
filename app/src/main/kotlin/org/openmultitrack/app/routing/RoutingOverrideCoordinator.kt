package org.openmultitrack.app.routing

import org.openmultitrack.app.data.MixerRoutingAutomationConfig
import org.openmultitrack.app.data.RoutingAutomationLevel
import org.openmultitrack.app.data.RoutingAutomationMethod
import org.openmultitrack.app.data.RoutingRestorePolicy
import org.openmultitrack.app.scribble.ScribbleImportSupport
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.mixer.behringer.MixerRoutingPort
import org.openmultitrack.mixer.behringer.XAirChannelInputState
import org.openmultitrack.mixer.behringer.XAirInputSourceCatalog
import org.openmultitrack.mixer.behringer.Xr18RoutingService
import java.util.UUID

sealed class RoutingApplyOutcome {
    data object Disabled : RoutingApplyOutcome()
    data object SkippedNoOsc : RoutingApplyOutcome()
    data object SkippedUnreachable : RoutingApplyOutcome()
    data object SkippedEmptyScope : RoutingApplyOutcome()
    data class ReadyToApply(val channelCount: Int, val kind: RoutingOverrideKind) : RoutingApplyOutcome()
    data class Applied(val transactionId: String) : RoutingApplyOutcome()
    data class Failed(val message: String) : RoutingApplyOutcome()
}

sealed class RoutingRestoreOutcome {
    data object NothingPending : RoutingRestoreOutcome()
    data object SkippedUnreachable : RoutingRestoreOutcome()
    data class Conflicts(val conflicts: List<RoutingChannelConflict>) : RoutingRestoreOutcome()
    data class ReadyToRestore(val conflictCount: Int) : RoutingRestoreOutcome()
    data object Restored : RoutingRestoreOutcome()
    data class Failed(val message: String) : RoutingRestoreOutcome()
}

/**
 * Owns XR18 routing override transactions. Never throws — failures are outcomes.
 */
class RoutingOverrideCoordinator(
    private val baselineStore: RoutingBaselineStore,
    private val routingFactory: (String) -> MixerRoutingPort = { host -> Xr18RoutingService(host) },
) {
    suspend fun peekApply(
        profile: MixerProfile,
        config: MixerRoutingAutomationConfig,
        kind: RoutingOverrideKind,
        channelIndices: Set<Int>,
    ): RoutingApplyOutcome {
        if (!isEligible(profile, config)) return RoutingApplyOutcome.Disabled
        if (channelIndices.isEmpty()) return RoutingApplyOutcome.SkippedEmptyScope
        if (config.level == RoutingAutomationLevel.PROMPT) {
            return RoutingApplyOutcome.ReadyToApply(channelIndices.size, kind)
        }
        return applyInternal(profile, config, kind, channelIndices)
    }

    suspend fun applyConfirmed(
        profile: MixerProfile,
        config: MixerRoutingAutomationConfig,
        kind: RoutingOverrideKind,
        channelIndices: Set<Int>,
        recordingActive: Boolean = false,
    ): RoutingApplyOutcome = applyInternal(profile, config, kind, channelIndices, recordingActive)

    suspend fun peekRestore(
        config: MixerRoutingAutomationConfig,
        pending: PendingRoutingRestore,
        port: MixerRoutingPort,
    ): RoutingRestoreOutcome {
        if (config.level == RoutingAutomationLevel.OFF) return RoutingRestoreOutcome.NothingPending
        if (!port.probe()) return RoutingRestoreOutcome.SkippedUnreachable
        val live = port.readAllChannelInputs()
        val conflicts = detectConflicts(pending, live)
        return when {
            conflicts.isNotEmpty() && shouldAskConflicts(config, conflicts) ->
                RoutingRestoreOutcome.Conflicts(conflicts)
            config.level == RoutingAutomationLevel.PROMPT ->
                RoutingRestoreOutcome.ReadyToRestore(conflicts.size)
            else -> restoreInternal(pending, config, port, live)
        }
    }

    suspend fun restoreConfirmed(
        config: MixerRoutingAutomationConfig,
        pending: PendingRoutingRestore,
    ): RoutingRestoreOutcome {
        val port = routingFactory(pending.oscHost)
        if (!port.probe()) return RoutingRestoreOutcome.SkippedUnreachable
        val live = port.readAllChannelInputs()
        return restoreInternal(pending, config, port, live)
    }

    suspend fun loadPending(): PendingRoutingRestore? = baselineStore.load()

    suspend fun clearPending() = baselineStore.clear()

    suspend fun readInputSources(host: String): Map<Int, XAirChannelInputState> {
        val port = routingFactory(host)
        if (!port.probe()) return emptyMap()
        return port.readAllChannelInputs()
    }

    private suspend fun applyInternal(
        profile: MixerProfile,
        config: MixerRoutingAutomationConfig,
        kind: RoutingOverrideKind,
        channelIndices: Set<Int>,
        recordingActive: Boolean = false,
    ): RoutingApplyOutcome {
        val host = profile.oscHost ?: return RoutingApplyOutcome.SkippedNoOsc
        val port = routingFactory(host)
        if (!port.probe()) return RoutingApplyOutcome.SkippedUnreachable

        val all = port.readAllChannelInputs()
        val baseline = channelIndices.associateWith { ch -> all[ch] }.filterValues { it != null }
            .mapValues { it.value!! }
        if (baseline.isEmpty()) return RoutingApplyOutcome.Failed("Could not read mixer routing")

        val overrideTargets = channelIndices.associateWith { ch ->
            when (kind) {
                RoutingOverrideKind.RECORD -> XAirInputSourceCatalog.recordTarget(ch)
                RoutingOverrideKind.SOUNDCHECK -> XAirInputSourceCatalog.soundcheckTarget(ch)
            }
        }

        val transactionId = UUID.randomUUID().toString()
        val pending = PendingRoutingRestore(
            transactionId = transactionId,
            mixerId = profile.id,
            oscHost = host,
            kind = kind,
            affectedChannels = channelIndices,
            baselineByChannel = baseline,
            overrideByChannel = overrideTargets,
            method = config.method,
            snapshotSlot = when (kind) {
                RoutingOverrideKind.RECORD -> config.recordSnapshotSlot
                RoutingOverrideKind.SOUNDCHECK -> config.soundcheckSnapshotSlot
            },
            capturedAtEpochMs = System.currentTimeMillis(),
            recordingWasActive = recordingActive,
        )
        baselineStore.save(pending)

        val ok = when (config.method) {
            RoutingAutomationMethod.PER_CHANNEL -> when (kind) {
                RoutingOverrideKind.RECORD -> port.applyRecordRouting(channelIndices)
                RoutingOverrideKind.SOUNDCHECK -> port.applySoundcheckRouting(channelIndices)
            }
            RoutingAutomationMethod.SNAPSHOT_SLOT -> {
                val slot = pending.snapshotSlot
                if (slot !in 1..64) {
                    baselineStore.clear()
                    return RoutingApplyOutcome.Failed("Snapshot slot not configured")
                }
                port.loadSnapshot(slot)
            }
        }
        if (!ok) {
            baselineStore.clear()
            return RoutingApplyOutcome.Failed("Mixer routing apply failed")
        }
        return RoutingApplyOutcome.Applied(transactionId)
    }

    private suspend fun restoreInternal(
        pending: PendingRoutingRestore,
        config: MixerRoutingAutomationConfig,
        port: MixerRoutingPort,
        live: Map<Int, XAirChannelInputState>,
    ): RoutingRestoreOutcome {
        val conflicts = detectConflicts(pending, live)
        val channelsToRestore = when {
            config.forceRestoreOnConflict || config.restorePolicy == RoutingRestorePolicy.STRICT ->
                pending.affectedChannels
            config.restorePolicy == RoutingRestorePolicy.RESPECT_LIVE ->
                pending.affectedChannels.filter { ch -> conflicts.none { it.channelIndex == ch } }.toSet()
            else -> pending.affectedChannels
        }
        val ok = port.restoreChannels(pending.baselineByChannel, channelsToRestore)
        return if (ok) {
            baselineStore.clear()
            RoutingRestoreOutcome.Restored
        } else {
            RoutingRestoreOutcome.Failed("Mixer routing restore failed")
        }
    }

    companion object {
        fun isEligible(profile: MixerProfile, config: MixerRoutingAutomationConfig): Boolean =
            config.level != RoutingAutomationLevel.OFF &&
                ScribbleImportSupport.supportsOsc(profile) &&
                !profile.oscHost.isNullOrBlank()

        fun detectConflicts(
            pending: PendingRoutingRestore,
            live: Map<Int, XAirChannelInputState>,
        ): List<RoutingChannelConflict> =
            pending.affectedChannels.mapNotNull { ch ->
                val baseline = pending.baselineByChannel[ch] ?: return@mapNotNull null
                val applied = pending.overrideByChannel[ch] ?: return@mapNotNull null
                val current = live[ch] ?: return@mapNotNull null
                if (current == applied) return@mapNotNull null
                if (current == baseline) return@mapNotNull null
                RoutingChannelConflict(ch, baseline, applied, current)
            }

        fun shouldAskConflicts(
            config: MixerRoutingAutomationConfig,
            conflicts: List<RoutingChannelConflict>,
        ): Boolean = conflicts.isNotEmpty() &&
            config.level == RoutingAutomationLevel.PROMPT &&
            config.restorePolicy == RoutingRestorePolicy.ASK_ON_CONFLICT &&
            !config.forceRestoreOnConflict
    }
}
