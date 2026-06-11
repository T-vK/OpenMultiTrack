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

    override suspend fun writeChannelInput(channelIndex: Int, state: XAirChannelInputState): Boolean {
        writes += channelIndex to state
        channels[channelIndex] = state
        return true
    }

    override suspend fun applyRecordRouting(channelIndices: Iterable<Int>): Boolean =
        channelIndices.all { writeChannelInput(it, XAirInputSourceCatalog.recordTarget(it)) }

    override suspend fun applySoundcheckRouting(channelIndices: Iterable<Int>): Boolean =
        channelIndices.all { writeChannelInput(it, XAirInputSourceCatalog.soundcheckTarget(it)) }

    override suspend fun restoreChannels(
        baseline: Map<Int, XAirChannelInputState>,
        channels: Set<Int>,
    ): Boolean = channels.all { ch ->
        val st = baseline[ch] ?: return@all true
        writeChannelInput(ch, st)
    }

    override suspend fun loadSnapshot(slot: Int): Boolean {
        snapshotLoads += slot
        return true
    }
}
