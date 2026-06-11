package org.openmultitrack.app.routing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerRoutingAutomationConfig
import org.openmultitrack.app.data.RoutingAutomationLevel
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.mixer.behringer.Xr18RoutingService
import java.util.concurrent.atomic.AtomicReference

data class RoutingApplyPromptState(
    val mixerId: String,
    val kind: RoutingOverrideKind,
    val channelCount: Int,
)

data class RoutingRestorePromptState(
    val mixerId: String,
    val kind: RoutingOverrideKind,
    val conflicts: List<RoutingChannelConflict>,
)

/**
 * Connects [RoutingOverrideCoordinator] to UI prompts via deferred user responses.
 */
class RoutingAutomationHooksImpl(
    private val settings: AppSettingsStore,
    private val coordinator: RoutingOverrideCoordinator,
    private val onApplyPrompt: (RoutingApplyPromptState) -> Unit,
    private val onRestorePrompt: (RoutingRestorePromptState) -> Unit,
    private val onStartupRestorePrompt: (PendingRoutingRestore) -> Unit,
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

    override suspend fun beforeRecordApply(profile: MixerProfile, armedChannels: Set<Int>): RoutingHookResult =
        beforeApply(profile, RoutingOverrideKind.RECORD, armedChannels)

    override suspend fun beforeSoundcheckApply(
        profile: MixerProfile,
        trackChannels: Set<Int>,
    ): RoutingHookResult = beforeApply(profile, RoutingOverrideKind.SOUNDCHECK, trackChannels)

    private suspend fun beforeApply(
        profile: MixerProfile,
        kind: RoutingOverrideKind,
        channels: Set<Int>,
    ): RoutingHookResult {
        val config = settings.routingAutomationForMixer(profile.id)
        when (val peek = coordinator.peekApply(profile, config, kind, channels)) {
            is RoutingApplyOutcome.Disabled,
            is RoutingApplyOutcome.SkippedNoOsc,
            is RoutingApplyOutcome.SkippedUnreachable,
            is RoutingApplyOutcome.SkippedEmptyScope,
            -> return RoutingHookResult.Skipped
            is RoutingApplyOutcome.Applied -> return RoutingHookResult.Proceed
            is RoutingApplyOutcome.Failed -> return RoutingHookResult.Failed(peek.message)
            is RoutingApplyOutcome.ReadyToApply -> {
                val deferred = CompletableDeferred<Boolean>()
                applyDeferred.set(deferred)
                withContext(Dispatchers.Main.immediate) {
                    onApplyPrompt(
                        RoutingApplyPromptState(profile.id, kind, peek.channelCount),
                    )
                }
                if (!deferred.await()) return RoutingHookResult.Cancelled
            }
        }
        return when (
            coordinator.applyConfirmed(
                profile,
                config,
                kind,
                channels,
                recordingActive = kind == RoutingOverrideKind.RECORD,
            )
        ) {
            is RoutingApplyOutcome.Applied -> RoutingHookResult.Proceed
            is RoutingApplyOutcome.Failed -> RoutingHookResult.Failed(
                "Mixer routing could not be verified — ${kind.name.lowercase()} cancelled",
            )
            else -> RoutingHookResult.Failed("Mixer routing apply failed")
        }
    }

    override suspend fun afterRecordRestore() = afterRestore(RoutingOverrideKind.RECORD)

    override suspend fun afterSoundcheckRestore() = afterRestore(RoutingOverrideKind.SOUNDCHECK)

    private suspend fun afterRestore(expectedKind: RoutingOverrideKind) {
        val pending = coordinator.loadPending() ?: return
        if (pending.kind != expectedKind) return
        val config = settings.routingAutomationForMixer(pending.mixerId)
        if (config.level == RoutingAutomationLevel.OFF) return
        val port = Xr18RoutingService(pending.oscHost)
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
                val deferred = CompletableDeferred<Boolean>()
                restoreDeferred.set(deferred)
                withContext(Dispatchers.Main.immediate) {
                    onRestorePrompt(
                        RoutingRestorePromptState(
                            pending.mixerId,
                            pending.kind,
                            peek.conflicts,
                        ),
                    )
                }
                if (!deferred.await()) return
            }
            is RoutingRestoreOutcome.ReadyToRestore -> {
                if (config.level == RoutingAutomationLevel.PROMPT) {
                    val deferred = CompletableDeferred<Boolean>()
                    restoreDeferred.set(deferred)
                    withContext(Dispatchers.Main.immediate) {
                        onRestorePrompt(
                            RoutingRestorePromptState(pending.mixerId, pending.kind, emptyList()),
                        )
                    }
                    if (!deferred.await()) return
                }
            }
        }
        when (val outcome = coordinator.restoreConfirmed(config, pending)) {
            is RoutingRestoreOutcome.Failed ->
                OmtLog.w("RoutingHooks", "restore confirmed failed: ${outcome.message}")
            else -> Unit
        }
    }

    override suspend fun onStartupPendingRestore() {
        val pending = coordinator.loadPending() ?: return
        if (pending.recordingWasActive && settings.activeRecordingMixerId == pending.mixerId) {
            return
        }
        withContext(Dispatchers.Main.immediate) {
            onStartupRestorePrompt(pending)
        }
    }
}
