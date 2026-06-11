package org.openmultitrack.mixer.behringer

/** Low-level X-Air routing OSC read/write with verification. */
internal object Xr18RoutingOsc {
    private const val SETTLE_MS = 50L

    /** Optional diagnostic hook (set from app layer). */
    var onVerifyFailure: ((channelIndex: Int, target: XAirChannelInputState, live: XAirChannelInputState?, replies: Map<String, List<Any>>) -> Unit)? = null

    fun parseAllChannels(replies: Map<String, List<Any>>): Map<Int, XAirChannelInputState> =
        parseChannels(replies, (0 until XAirInputSourceCatalog.CHANNEL_COUNT).toList())

    fun parseChannels(
        replies: Map<String, List<Any>>,
        channelIndices: Iterable<Int>,
    ): Map<Int, XAirChannelInputState> = buildMap {
        for (idx in channelIndices) {
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

    fun queryPathsForChannels(channelIndices: Collection<Int>): List<String> =
        channelIndices.sorted().flatMap { channelQueryPaths(it) }

    fun confirmAgainst(
        channelIndex: Int,
        target: XAirChannelInputState,
        live: XAirChannelInputState?,
        replyPaths: Set<String>,
    ): RoutingConfirmResult = RoutingConfirmResult(channelIndex, target, live, replyPaths)

    suspend fun sendChannelTarget(
        client: OscUdpClient,
        channelIndex: Int,
        target: XAirChannelInputState,
        sendXremote: Boolean = true,
    ) {
        val ch = channelIndex + 1
        if (sendXremote) {
            client.send(OscPath.xremote())
        }
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

    /** Fire phase-1 then phase-2 OSC for all [targets] with only two settle pauses total. */
    suspend fun sendChannelTargetsBurst(
        client: OscUdpClient,
        targets: Map<Int, XAirChannelInputState>,
    ) {
        if (targets.isEmpty()) return
        for ((idx, target) in targets) {
            val ch = idx + 1
            if (target.usesUsbReturn) {
                client.send(
                    OscPath.channelConfigRtnsrc(ch),
                    listOf(OscArgument.IntArg(target.rtnsrc)),
                )
            } else {
                client.send(
                    OscPath.channelPreampRtnSw(ch),
                    listOf(OscArgument.IntArg(0)),
                )
            }
        }
        Thread.sleep(SETTLE_MS)
        for ((idx, target) in targets) {
            val ch = idx + 1
            if (target.usesUsbReturn) {
                client.send(
                    OscPath.channelPreampRtnSw(ch),
                    listOf(OscArgument.IntArg(1)),
                )
            } else {
                client.send(
                    OscPath.channelConfigInsrc(ch),
                    listOf(OscArgument.IntArg(target.insrc)),
                )
            }
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

    /**
     * Write only channels that differ from [liveByChannel], then verify all targets in one query batch.
     * Much faster than per-channel write+query loops when many strips are already correct.
     */
    suspend fun applyChannelTargetsBatch(
        client: OscUdpClient,
        targets: Map<Int, XAirChannelInputState>,
        liveByChannel: Map<Int, XAirChannelInputState>,
        maxBatchAttempts: Int = 3,
    ): Boolean {
        if (targets.isEmpty()) return true
        val needChange = targets.filter { (ch, target) ->
            val live = liveByChannel[ch]
            live == null || !live.matchesRouting(target)
        }
        if (needChange.isEmpty()) {
            Xr18RoutingLog.info("apply batch skip ${targets.size} targets already match")
            return true
        }

        val queryPaths = queryPathsForChannels(needChange.keys)
        Xr18RoutingLog.info(
            "apply batch ${needChange.size}/${targets.size} channels need change, " +
                "${queryPaths.size} verify paths",
        )
        client.send(OscPath.xremote())
        repeat(maxBatchAttempts) { attempt ->
            Xr18RoutingLog.stepSuspend("write burst attempt=${attempt + 1}") {
                sendChannelTargetsBurst(client, needChange)
            }
            if (attempt > 0) {
                Thread.sleep(SETTLE_MS * attempt)
            }
            val replies = client.query(
                queryPaths,
                timeoutMs = 1500,
                rounds = 2,
                label = "verify attempt=${attempt + 1}",
            )
            val allOk = needChange.keys.all { ch ->
                val target = needChange[ch]!!
                val live = readChannel(replies, ch)
                live != null && live.matchesRouting(target)
            }
            if (allOk) return true
            if (attempt == maxBatchAttempts - 1) {
                val failedCh = needChange.keys.firstOrNull { ch ->
                    val target = needChange[ch]!!
                    val live = readChannel(replies, ch)
                    live == null || !live.matchesRouting(target)
                }
                if (failedCh != null) {
                    val target = needChange[failedCh]!!
                    val live = readChannel(replies, failedCh)
                    onVerifyFailure?.invoke(failedCh, target, live, replies)
                }
            }
        }
        return false
    }

    suspend fun restoreChannelsBatch(
        client: OscUdpClient,
        baseline: Map<Int, XAirChannelInputState>,
        channels: Set<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean {
        val targets = channels.mapNotNull { ch -> baseline[ch]?.let { ch to it } }.toMap()
        return applyChannelTargetsBatch(client, targets, liveByChannel)
    }
}
