package org.openmultitrack.mixer.behringer

/** Low-level X-Air routing OSC read/write with verification. */
internal object Xr18RoutingOsc {
    private const val SETTLE_MS = 100L

    /** Optional diagnostic hook (set from app layer). */
    var onVerifyFailure: ((channelIndex: Int, target: XAirChannelInputState, live: XAirChannelInputState?, replies: Map<String, List<Any>>) -> Unit)? = null

    fun parseAllChannels(replies: Map<String, List<Any>>): Map<Int, XAirChannelInputState> =
        buildMap {
            for (ch in 1..XAirInputSourceCatalog.CHANNEL_COUNT) {
                val idx = ch - 1
                val state = readChannel(replies, idx) ?: continue
                put(idx, state)
            }
        }

    fun queryPaths(): List<String> = buildList {
        for (ch in 1..XAirInputSourceCatalog.CHANNEL_COUNT) {
            add(OscPath.channelPreampRtnSw(ch))
            add(OscPath.channelConfigInsrc(ch))
            add(OscPath.channelConfigRtnsrc(ch))
        }
    }

    fun readChannel(replies: Map<String, List<Any>>, channelIndex: Int): XAirChannelInputState? {
        val ch = channelIndex + 1
        if (ch !in 1..XAirInputSourceCatalog.CHANNEL_COUNT) return null
        val rtnSw = oscInt(replies[OscPath.channelPreampRtnSw(ch)]?.firstOrNull()) ?: return null
        val insrc = oscInt(replies[OscPath.channelConfigInsrc(ch)]?.firstOrNull()) ?: 0
        val rtnsrc = oscInt(replies[OscPath.channelConfigRtnsrc(ch)]?.firstOrNull()) ?: 0
        return XAirChannelInputState(insrc, rtnsrc, rtnSw)
    }

    fun channelQueryPaths(channelIndex: Int): List<String> {
        val ch = channelIndex + 1
        return listOf(
            OscPath.channelPreampRtnSw(ch),
            OscPath.channelConfigInsrc(ch),
            OscPath.channelConfigRtnsrc(ch),
        )
    }

    fun confirmAgainst(
        channelIndex: Int,
        target: XAirChannelInputState,
        live: XAirChannelInputState?,
        replyPaths: Set<String>,
    ): RoutingConfirmResult = RoutingConfirmResult(channelIndex, target, live, replyPaths)

    suspend fun sendChannelTarget(client: OscUdpClient, channelIndex: Int, target: XAirChannelInputState) {
        val ch = channelIndex + 1
        client.send(OscPath.xremote())
        if (target.usesUsbReturn) {
            client.send(
                OscPath.channelConfigRtnsrc(ch),
                listOf(OscArgument.IntArg(target.rtnsrc)),
            )
            Thread.sleep(SETTLE_MS)
            client.send(
                OscPath.channelPreampRtnSw(ch),
                listOf(OscArgument.IntArg(1)),
            )
        } else {
            client.send(
                OscPath.channelPreampRtnSw(ch),
                listOf(OscArgument.IntArg(0)),
            )
            Thread.sleep(SETTLE_MS)
            client.send(
                OscPath.channelConfigInsrc(ch),
                listOf(OscArgument.IntArg(target.insrc)),
            )
        }
        Thread.sleep(SETTLE_MS)
    }

    /**
     * Write [target] routing, then **separately query** live values and compare.
     * Retries the write+query loop when the mixer has not caught up yet.
     */
    suspend fun writeAndConfirm(
        client: OscUdpClient,
        channelIndex: Int,
        target: XAirChannelInputState,
        maxAttempts: Int = 5,
    ): RoutingConfirmResult {
        val paths = channelQueryPaths(channelIndex)
        val initialReplies = client.query(paths, timeoutMs = 2000, rounds = 3)
        val initial = readChannel(initialReplies, channelIndex)
        if (initial != null && initial.matchesRouting(target)) {
            return confirmAgainst(channelIndex, target, initial, initialReplies.keys)
        }

        var last = confirmAgainst(channelIndex, target, initial, initialReplies.keys)
        repeat(maxAttempts) { attempt ->
            sendChannelTarget(client, channelIndex, target)
            Thread.sleep(SETTLE_MS * (attempt + 1))
            val replies = client.query(paths, timeoutMs = 2500, rounds = 4)
            val live = readChannel(replies, channelIndex)
            last = confirmAgainst(channelIndex, target, live, replies.keys)
            if (last.confirmed) return last
            if (attempt == maxAttempts - 1) {
                onVerifyFailure?.invoke(channelIndex, target, live, replies)
            }
        }
        return last
    }

    suspend fun writeAndVerify(
        client: OscUdpClient,
        channelIndex: Int,
        target: XAirChannelInputState,
        maxAttempts: Int = 5,
    ): Boolean = writeAndConfirm(client, channelIndex, target, maxAttempts).confirmed
}
