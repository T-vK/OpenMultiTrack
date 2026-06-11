package org.openmultitrack.mixer.behringer

/** Testable boundary for XR18 routing OSC (UDP in production). */
interface MixerRoutingPort {
    suspend fun probe(timeoutMs: Long = 800): Boolean

    suspend fun readChannelInput(channelIndex: Int): XAirChannelInputState?

    suspend fun readAllChannelInputs(): Map<Int, XAirChannelInputState>

    /** Read routing for [channelIndices] only (fewer OSC paths than [readAllChannelInputs]). */
    suspend fun readChannelInputs(channelIndices: Iterable<Int>): Map<Int, XAirChannelInputState> {
        val indices = channelIndices.toSet()
        if (indices.isEmpty()) return emptyMap()
        val all = readAllChannelInputs()
        return indices.mapNotNull { ch -> all[ch]?.let { ch to it } }.toMap()
    }

    /**
     * One OSC session: probe → scoped read → optional batched apply.
     * Production implementation keeps a single UDP socket for all steps.
     */
    suspend fun captureAndApplyRouting(
        channelIndices: Iterable<Int>,
        targets: Map<Int, XAirChannelInputState>,
        deferApply: Boolean,
        soundcheck: Boolean,
        probeTimeoutMs: Long = 2500,
    ): RoutingCaptureApplyResult

    suspend fun writeChannelInput(channelIndex: Int, state: XAirChannelInputState): Boolean

    /** Write routing OSC without read-back (diagnostics / e2e). */
    suspend fun writeChannelInputOnly(channelIndex: Int, state: XAirChannelInputState)

    /** Query live routing and compare to [target] (separate OSC read after any writes). */
    suspend fun confirmChannelRouting(
        channelIndex: Int,
        target: XAirChannelInputState,
    ): RoutingConfirmResult

    suspend fun applyRecordRouting(
        channelIndices: Iterable<Int>,
        liveByChannel: Map<Int, XAirChannelInputState> = emptyMap(),
    ): Boolean

    suspend fun applySoundcheckRouting(
        channelIndices: Iterable<Int>,
        liveByChannel: Map<Int, XAirChannelInputState> = emptyMap(),
    ): Boolean

    suspend fun restoreChannels(
        baseline: Map<Int, XAirChannelInputState>,
        channels: Set<Int>,
        liveByChannel: Map<Int, XAirChannelInputState> = emptyMap(),
    ): Boolean

    suspend fun loadSnapshot(slot: Int): Boolean
}
