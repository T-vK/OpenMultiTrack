package org.openmultitrack.mixer.behringer

/** Testable boundary for XR18 routing OSC (UDP in production). */
interface MixerRoutingPort {
    suspend fun probe(timeoutMs: Long = 800): Boolean

    suspend fun readChannelInput(channelIndex: Int): XAirChannelInputState?

    suspend fun readAllChannelInputs(): Map<Int, XAirChannelInputState>

    suspend fun writeChannelInput(channelIndex: Int, state: XAirChannelInputState): Boolean

    /** Write routing OSC without read-back (diagnostics / e2e). */
    suspend fun writeChannelInputOnly(channelIndex: Int, state: XAirChannelInputState)

    /** Query live routing and compare to [target] (separate OSC read after any writes). */
    suspend fun confirmChannelRouting(
        channelIndex: Int,
        target: XAirChannelInputState,
    ): RoutingConfirmResult

    suspend fun applyRecordRouting(channelIndices: Iterable<Int>): Boolean

    suspend fun applySoundcheckRouting(channelIndices: Iterable<Int>): Boolean

    suspend fun restoreChannels(
        baseline: Map<Int, XAirChannelInputState>,
        channels: Set<Int>,
    ): Boolean

    suspend fun loadSnapshot(slot: Int): Boolean
}
