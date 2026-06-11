package org.openmultitrack.mixer.behringer

class FakeMixerRoutingPort(
    private var reachable: Boolean = true,
    private var channels: MutableMap<Int, XAirChannelInputState> = mutableMapOf(),
) : MixerRoutingPort {
    val writes = mutableListOf<Pair<Int, XAirChannelInputState>>()
    var snapshotLoads = mutableListOf<Int>()

    fun seedChannel(index: Int, state: XAirChannelInputState) {
        channels[index] = state
    }

    override suspend fun probe(timeoutMs: Long): Boolean = reachable

    override suspend fun readChannelInput(channelIndex: Int): XAirChannelInputState? =
        channels[channelIndex]

    override suspend fun readAllChannelInputs(): Map<Int, XAirChannelInputState> =
        channels.toMap()

    override suspend fun readChannelInputs(channelIndices: Iterable<Int>): Map<Int, XAirChannelInputState> =
        channelIndices.mapNotNull { ch -> channels[ch]?.let { ch to it } }.toMap()

    override suspend fun captureAndApplyRouting(
        channelIndices: Iterable<Int>,
        targets: Map<Int, XAirChannelInputState>,
        deferApply: Boolean,
        soundcheck: Boolean,
        probeTimeoutMs: Long,
    ): RoutingCaptureApplyResult {
        if (!reachable) {
            return RoutingCaptureApplyResult(false, emptyMap(), false)
        }
        val live = readChannelInputs(channelIndices)
        if (deferApply) {
            return RoutingCaptureApplyResult(true, live, true)
        }
        val applied = if (soundcheck) {
            applySoundcheckRouting(channelIndices, live)
        } else {
            applyRecordRouting(channelIndices, live)
        }
        return RoutingCaptureApplyResult(true, live, applied)
    }

    override suspend fun writeChannelInput(channelIndex: Int, state: XAirChannelInputState): Boolean {
        writes += channelIndex to state
        channels[channelIndex] = state
        return true
    }

    override suspend fun writeChannelInputOnly(channelIndex: Int, state: XAirChannelInputState) {
        channels[channelIndex] = state
    }

    override suspend fun confirmChannelRouting(
        channelIndex: Int,
        target: XAirChannelInputState,
    ): RoutingConfirmResult {
        val live = channels[channelIndex]
        return RoutingConfirmResult(channelIndex, target, live)
    }

    override suspend fun applyRecordRouting(
        channelIndices: Iterable<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean = channelIndices.all { ch ->
        val target = XAirInputSourceCatalog.recordTarget(ch)
        val live = liveByChannel[ch]
        if (live != null && live.matchesRouting(target)) return@all true
        writeChannelInput(ch, target)
    }

    override suspend fun applySoundcheckRouting(
        channelIndices: Iterable<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean = channelIndices.all { ch ->
        val target = XAirInputSourceCatalog.soundcheckTarget(ch)
        val live = liveByChannel[ch]
        if (live != null && live.matchesRouting(target)) return@all true
        writeChannelInput(ch, target)
    }

    override suspend fun restoreChannels(
        baseline: Map<Int, XAirChannelInputState>,
        channels: Set<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean = channels.all { ch ->
        val st = baseline[ch] ?: return@all true
        writeChannelInput(ch, st)
    }

    override suspend fun loadSnapshot(slot: Int): Boolean {
        snapshotLoads += slot
        return true
    }
}
