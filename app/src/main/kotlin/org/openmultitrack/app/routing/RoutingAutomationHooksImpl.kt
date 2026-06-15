package org.openmultitrack.app.routing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openmultitrack.app.audio.TransportTraceHub
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerRoutingAutomationConfig
import org.openmultitrack.app.data.RoutingAutomationLevel
import org.openmultitrack.app.data.RoutingAutomationMethod
import org.openmultitrack.app.data.RoutingAutomationTrigger
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.session.AppMode
import java.util.concurrent.atomic.AtomicReference

data class RoutingApplyPromptState(
    val mixerId: String,
    val kind: RoutingOverrideKind,
    val method: RoutingAutomationMethod,
    val channelCount: Int = 0,
    val snapshotSlot: Int = 0,
    val snapshotName: String? = null,
)

data class RoutingRestorePromptState(
    val mixerId: String,
    val kind: RoutingOverrideKind,
    val method: RoutingAutomationMethod,
    val conflicts: List<RoutingChannelConflict>,
    val snapshotSlot: Int = 0,
    val snapshotName: String? = null,
)

/**
 * Connects [RoutingOverrideCoordinator] to UI prompts via deferred user responses.
 */
class RoutingAutomationHooksImpl(
    private val settings: AppSettingsStore,
    private val coordinator: RoutingOverrideCoordinator,
    private val onApplyPrompt: (RoutingApplyPromptState) -> Unit,
    private val onRestorePrompt: (RoutingRestorePromptState) -> Unit,
) : RoutingAutomationHooks {
    private val applyDeferred = AtomicReference<CompletableDeferred<Boolean>?>(null)
    private val restoreDeferred = AtomicReference<CompletableDeferred<Boolean>?>(null)

    fun confirmApply() {
        applyDeferred.getAndSet(null)?.complete(true)
    }

    fun cancelApply() {
        applyDeferred.getAndSet(null)?.complete(false)
    }

    fun confirmRestore() {
        restoreDeferred.getAndSet(null)?.complete(true)
    }

    fun cancelRestore() {
        restoreDeferred.getAndSet(null)?.complete(false)
    }

    override suspend fun beforeRecordApply(profile: MixerProfile, armedChannels: Set<Int>): RoutingHookResult {
        TransportTraceHub.mark(profile.id, "routing hooks beforeRecordApply (${armedChannels.size} ch)")
        return beforeApply(profile, RoutingOverrideKind.RECORD, armedChannels).also { result ->
            TransportTraceHub.mark(profile.id, "routing hooks beforeRecordApply → $result")
        }
    }

    override suspend fun beforeSoundcheckApply(
        profile: MixerProfile,
        trackChannels: Set<Int>,
    ): RoutingHookResult {
        TransportTraceHub.mark(profile.id, "routing hooks beforeSoundcheckCapture (${trackChannels.size} ch)")
        return beforeCapture(profile, RoutingOverrideKind.SOUNDCHECK, trackChannels).also { result ->
            TransportTraceHub.mark(profile.id, "routing hooks beforeSoundcheckCapture → $result")
        }
    }

    override suspend fun onAppModeEntered(profile: MixerProfile, mode: AppMode) {
        val config = settings.routingAutomationForMixer(profile.id)
        if (config.trigger != RoutingAutomationTrigger.ON_MODE_ENTER) return
        val kind = when (mode) {
            AppMode.MULTITRACK_RECORD -> RoutingOverrideKind.IDLE
            AppMode.VIRTUAL_SOUNDCHECK -> RoutingOverrideKind.SOUNDCHECK
            AppMode.SIMPLE_PLAY -> return
        }
        recallForModeEnter(profile, config, kind)
    }

    private suspend fun recallForModeEnter(
        profile: MixerProfile,
        config: MixerRoutingAutomationConfig,
        kind: RoutingOverrideKind,
    ): RoutingHookResult {
        when (val peek = coordinator.peekApply(profile, config, kind, emptySet())) {
            is RoutingApplyOutcome.Disabled,
            is RoutingApplyOutcome.SkippedNoOsc,
            is RoutingApplyOutcome.SkippedEmptyScope,
            -> return RoutingHookResult.Skipped
            is RoutingApplyOutcome.SkippedUnreachable ->
                return routingFailed(profile, kind, "Mixer not reachable on LAN — check Wi‑Fi and OSC IP")
            is RoutingApplyOutcome.Applied -> return RoutingHookResult.Proceed
            is RoutingApplyOutcome.Failed -> return routingFailed(profile, kind, peek.message)
            is RoutingApplyOutcome.AlreadyMatched -> Unit
            is RoutingApplyOutcome.ReadyToApply -> {
                if (!confirmApplyPrompt(profile, config, kind, peek)) {
                    return RoutingHookResult.Cancelled
                }
            }
        }
        return when (val outcome = coordinator.recallSnapshot(profile, config, kind)) {
            is RoutingApplyOutcome.Applied -> RoutingHookResult.Proceed
            is RoutingApplyOutcome.Failed -> routingFailed(profile, kind, outcome.message)
            is RoutingApplyOutcome.SkippedUnreachable ->
                routingFailed(profile, kind, "Mixer not reachable on LAN — check Wi‑Fi and OSC IP")
            else -> RoutingHookResult.Skipped
        }
    }

    private suspend fun beforeCapture(
        profile: MixerProfile,
        kind: RoutingOverrideKind,
        channels: Set<Int>,
    ): RoutingHookResult {
        val config = settings.routingAutomationForMixer(profile.id)
        if (config.trigger != RoutingAutomationTrigger.ON_TRANSPORT_BUTTON) {
            return RoutingHookResult.Skipped
        }
        return runApplyFlow(
            profile = profile,
            config = config,
            kind = kind,
            channels = channels,
            captureOnly = true,
        )
    }

    private suspend fun beforeApply(
        profile: MixerProfile,
        kind: RoutingOverrideKind,
        channels: Set<Int>,
    ): RoutingHookResult {
        val config = settings.routingAutomationForMixer(profile.id)
        if (config.trigger != RoutingAutomationTrigger.ON_TRANSPORT_BUTTON) {
            return RoutingHookResult.Skipped
        }
        return runApplyFlow(
            profile = profile,
            config = config,
            kind = kind,
            channels = channels,
            captureOnly = false,
        )
    }

    private suspend fun runApplyFlow(
        profile: MixerProfile,
        config: MixerRoutingAutomationConfig,
        kind: RoutingOverrideKind,
        channels: Set<Int>,
        captureOnly: Boolean,
    ): RoutingHookResult {
        when (val peek = coordinator.peekApply(profile, config, kind, channels)) {
            is RoutingApplyOutcome.Disabled,
            is RoutingApplyOutcome.SkippedNoOsc,
            is RoutingApplyOutcome.SkippedEmptyScope,
            -> return RoutingHookResult.Skipped
            is RoutingApplyOutcome.SkippedUnreachable ->
                return routingFailed(profile, kind, "Mixer not reachable on LAN — check Wi‑Fi and OSC IP")
            is RoutingApplyOutcome.Applied -> return RoutingHookResult.Proceed
            is RoutingApplyOutcome.Failed -> return routingFailed(profile, kind, peek.message)
            is RoutingApplyOutcome.AlreadyMatched -> Unit
            is RoutingApplyOutcome.ReadyToApply -> {
                if (!confirmApplyPrompt(profile, config, kind, peek)) {
                    return RoutingHookResult.Cancelled
                }
            }
        }
        val outcome = if (captureOnly) {
            coordinator.captureOverrideOnly(profile, config, kind, channels)
        } else {
            coordinator.applyConfirmed(
                profile,
                config,
                kind,
                channels,
                recordingActive = kind == RoutingOverrideKind.RECORD,
            )
        }
        return when (outcome) {
            is RoutingApplyOutcome.Applied -> RoutingHookResult.Proceed
            is RoutingApplyOutcome.Failed -> routingFailed(profile, kind, outcome.message)
            is RoutingApplyOutcome.SkippedUnreachable ->
                routingFailed(profile, kind, "Mixer not reachable on LAN — check Wi‑Fi and OSC IP")
            is RoutingApplyOutcome.SkippedEmptyScope -> RoutingHookResult.Skipped
            else -> routingFailed(profile, kind, "Routing apply failed ($outcome)")
        }
    }

    private suspend fun confirmApplyPrompt(
        profile: MixerProfile,
        config: MixerRoutingAutomationConfig,
        kind: RoutingOverrideKind,
        peek: RoutingApplyOutcome.ReadyToApply,
    ): Boolean {
        if (config.level != RoutingAutomationLevel.PROMPT) return true
        val deferred = CompletableDeferred<Boolean>()
        applyDeferred.set(deferred)
        withContext(Dispatchers.Main.immediate) {
            onApplyPrompt(
                RoutingApplyPromptState(
                    mixerId = profile.id,
                    kind = kind,
                    method = config.method,
                    channelCount = peek.channelCount,
                    snapshotSlot = peek.snapshotSlot,
                ),
            )
        }
        return deferred.await()
    }

    private fun routingFailed(
        profile: MixerProfile,
        kind: RoutingOverrideKind,
        message: String,
    ): RoutingHookResult.Failed {
        OmtLog.w("RoutingHooks", "$kind apply failed for ${profile.displayName}: $message")
        AppLogBuffer.append("W", "Routing", "$kind: $message")
        return RoutingHookResult.Failed(message)
    }

    override suspend fun afterRecordRestore() {
        val mixerId = coordinator.loadPending()?.mixerId
        if (mixerId != null) {
            TransportTraceHub.mark(mixerId, "routing hooks afterRecordRestore begin")
        }
        afterRestore(RoutingOverrideKind.RECORD)
        if (mixerId != null) {
            TransportTraceHub.mark(mixerId, "routing hooks afterRecordRestore end")
        }
    }

    override suspend fun afterSoundcheckPlaybackStarted(profile: MixerProfile): RoutingHookResult {
        TransportTraceHub.mark(profile.id, "routing hooks afterSoundcheckPlaybackStarted")
        val pending = coordinator.loadPending() ?: return RoutingHookResult.Skipped
        if (pending.kind != RoutingOverrideKind.SOUNDCHECK || pending.mixerId != profile.id) {
            return RoutingHookResult.Skipped
        }
        val config = settings.routingAutomationForMixer(profile.id)
        if (config.level == RoutingAutomationLevel.OFF) return RoutingHookResult.Skipped
        if (config.trigger != RoutingAutomationTrigger.ON_TRANSPORT_BUTTON) {
            return RoutingHookResult.Skipped
        }
        return when (val outcome = coordinator.reapplyOverrideOnly(config, pending)) {
            is RoutingApplyOutcome.Applied -> RoutingHookResult.Proceed.also {
                TransportTraceHub.mark(profile.id, "routing hooks afterSoundcheckPlaybackStarted → Proceed")
            }
            is RoutingApplyOutcome.Failed ->
                routingFailed(profile, RoutingOverrideKind.SOUNDCHECK, outcome.message).also {
                    TransportTraceHub.mark(profile.id, "routing hooks afterSoundcheckPlaybackStarted → Failed")
                }
            is RoutingApplyOutcome.SkippedUnreachable ->
                routingFailed(profile, RoutingOverrideKind.SOUNDCHECK, "Mixer not reachable on LAN — check Wi‑Fi and OSC IP")
            else -> RoutingHookResult.Skipped
        }
    }

    override suspend fun afterSoundcheckRestore() = afterRestore(RoutingOverrideKind.SOUNDCHECK)

    private suspend fun afterRestore(expectedKind: RoutingOverrideKind) {
        val pending = coordinator.loadPending() ?: return
        if (pending.kind != expectedKind) return
        val config = settings.routingAutomationForMixer(pending.mixerId)
        if (config.level == RoutingAutomationLevel.OFF) return
        val port = coordinator.createRoutingPort(pending.oscHost)
        when (val peek = coordinator.peekRestore(config, pending, port)) {
            RoutingRestoreOutcome.NothingPending,
            RoutingRestoreOutcome.SkippedUnreachable,
            -> return
            RoutingRestoreOutcome.Restored -> return
            is RoutingRestoreOutcome.Failed -> {
                OmtLog.w("RoutingHooks", "restore failed: ${peek.message}")
                return
            }
            is RoutingRestoreOutcome.Conflicts -> {
                if (!confirmRestorePrompt(pending, config, peek.conflicts)) {
                    coordinator.clearPending()
                    return
                }
            }
            is RoutingRestoreOutcome.ReadyToRestore -> {
                if (!confirmRestorePrompt(pending, config, emptyList())) {
                    coordinator.clearPending()
                    return
                }
            }
        }
        when (val outcome = coordinator.restoreConfirmed(config, pending)) {
            is RoutingRestoreOutcome.Failed ->
                OmtLog.w("RoutingHooks", "restore confirmed failed: ${outcome.message}")
            else -> Unit
        }
    }

    private suspend fun confirmRestorePrompt(
        pending: PendingRoutingRestore,
        config: MixerRoutingAutomationConfig,
        conflicts: List<RoutingChannelConflict>,
    ): Boolean {
        val needsPrompt = when {
            config.method == RoutingAutomationMethod.SNAPSHOT_SLOT ->
                config.level == RoutingAutomationLevel.PROMPT && config.idleSnapshotSlot in 1..64
            conflicts.isNotEmpty() ->
                RoutingOverrideCoordinator.shouldAskConflicts(config, conflicts)
            else -> config.level == RoutingAutomationLevel.PROMPT
        }
        if (!needsPrompt) return true
        val deferred = CompletableDeferred<Boolean>()
        restoreDeferred.set(deferred)
        withContext(Dispatchers.Main.immediate) {
            onRestorePrompt(
                RoutingRestorePromptState(
                    mixerId = pending.mixerId,
                    kind = pending.kind,
                    method = config.method,
                    conflicts = conflicts,
                    snapshotSlot = config.idleSnapshotSlot,
                ),
            )
        }
        return deferred.await()
    }

    override suspend fun onStartupPendingRestore() {
        val pending = coordinator.loadPending() ?: return
        if (pending.recordingWasActive && settings.activeRecordingMixerId == pending.mixerId) {
            return
        }
        afterRestore(pending.kind)
    }
}
