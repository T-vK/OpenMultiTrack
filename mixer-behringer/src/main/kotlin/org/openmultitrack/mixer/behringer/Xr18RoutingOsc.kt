package org.openmultitrack.mixer.behringer

/** Low-level X-Air routing OSC read/write with verification. */
internal object Xr18RoutingOsc {
    private const val SETTLE_MS = 60L

    fun parseAllChannels(replies: Map<String, List<Any>>): Map<Int, XAirChannelInputState> =
        buildMap {
            for (ch in 1..XAirInputSourceCatalog.CHANNEL_COUNT) {
                val idx = ch - 1
                val insrc = (replies[OscPath.channelConfigInsrc(ch)]?.firstOrNull() as? Int) ?: 0
                val rtnsrc = (replies[OscPath.channelConfigRtnsrc(ch)]?.firstOrNull() as? Int) ?: 0
                val rtnSw = (replies[OscPath.channelPreampRtnSw(ch)]?.firstOrNull() as? Int) ?: 0
                put(idx, XAirChannelInputState(insrc, rtnsrc, rtnSw))
            }
        }

    fun queryPaths(): List<String> = buildList {
        for (ch in 1..XAirInputSourceCatalog.CHANNEL_COUNT) {
            add(OscPath.channelConfigInsrc(ch))
            add(OscPath.channelConfigRtnsrc(ch))
            add(OscPath.channelPreampRtnSw(ch))
        }
    }

    fun readChannel(replies: Map<String, List<Any>>, channelIndex: Int): XAirChannelInputState? {
        val ch = channelIndex + 1
        if (ch !in 1..XAirInputSourceCatalog.CHANNEL_COUNT) return null
        val insrc = replies[OscPath.channelConfigInsrc(ch)]?.firstOrNull() as? Int ?: return null
        val rtnsrc = replies[OscPath.channelConfigRtnsrc(ch)]?.firstOrNull() as? Int ?: 0
        val rtnSw = replies[OscPath.channelPreampRtnSw(ch)]?.firstOrNull() as? Int ?: 0
        return XAirChannelInputState(insrc, rtnsrc, rtnSw)
    }

    fun channelQueryPaths(channelIndex: Int): List<String> {
        val ch = channelIndex + 1
        return listOf(
            OscPath.channelConfigInsrc(ch),
            OscPath.channelConfigRtnsrc(ch),
            OscPath.channelPreampRtnSw(ch),
        )
    }

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

    suspend fun writeAndVerify(
        client: OscUdpClient,
        channelIndex: Int,
        target: XAirChannelInputState,
        maxAttempts: Int = 4,
    ): Boolean {
        repeat(maxAttempts) { attempt ->
            sendChannelTarget(client, channelIndex, target)
            val waitMs = SETTLE_MS * (attempt + 1)
            Thread.sleep(waitMs)
            val replies = client.query(channelQueryPaths(channelIndex), timeoutMs = 1200, rounds = 3)
            val live = readChannel(replies, channelIndex)
            if (live != null && live.matchesRouting(target)) {
                return true
            }
        }
        return false
    }
}
