package org.openmultitrack.app.routing

/** Set from [org.openmultitrack.app.MainViewModel]; invoked on the service/IO thread. */
object RoutingAutomationBridge {
    var hooks: RoutingAutomationHooks? = null
}

interface RoutingAutomationHooks {
    suspend fun beforeRecordApply(armedChannels: Set<Int>): RoutingHookResult
    suspend fun afterRecordRestore()
    suspend fun beforeSoundcheckApply(trackChannels: Set<Int>): RoutingHookResult
    suspend fun afterSoundcheckRestore()
    suspend fun onStartupPendingRestore()
}

sealed class RoutingHookResult {
    data object Proceed : RoutingHookResult()
    data object Skipped : RoutingHookResult()
    data object Cancelled : RoutingHookResult()
}
