package org.openmultitrack.app.routing

import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.session.AppMode

/** Set from [org.openmultitrack.app.MainViewModel]; invoked on the service/IO thread. */
object RoutingAutomationBridge {
    var hooks: RoutingAutomationHooks? = null
}

interface RoutingAutomationHooks {
    suspend fun beforeRecordApply(profile: MixerProfile, armedChannels: Set<Int>): RoutingHookResult
    suspend fun afterRecordRestore()
    suspend fun beforeSoundcheckApply(profile: MixerProfile, trackChannels: Set<Int>): RoutingHookResult
    suspend fun afterSoundcheckPlaybackStarted(profile: MixerProfile): RoutingHookResult
    suspend fun afterSoundcheckRestore()
    suspend fun onAppModeEntered(profile: MixerProfile, mode: AppMode)
    suspend fun onStartupPendingRestore()
    suspend fun hasPendingRestore(): Boolean
}

sealed class RoutingHookResult {
    data object Proceed : RoutingHookResult()
    data object Skipped : RoutingHookResult()
    data object Cancelled : RoutingHookResult()
    data class Failed(val message: String) : RoutingHookResult()
}
