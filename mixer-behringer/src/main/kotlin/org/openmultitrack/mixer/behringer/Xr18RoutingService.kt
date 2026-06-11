package org.openmultitrack.mixer.behringer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * XR18 channel input routing over OSC.
 *
 * Uses X-Air paths `/config/insrc`, `/config/rtnsrc`, `/preamp/rtnsw` per
 * [Behringer World X-Air OSC wiki](https://behringer.world/wiki/doku.php?id=x-air_osc).
 */
class Xr18RoutingService(
    private val host: String,
    private val port: Int = Xr18Mixer.DEFAULT_PORT,
    private val socketSetup: OscSocketSetup? = null,
    private val clientFactory: (String, Int, OscSocketSetup?) -> OscUdpClient = { h, p, setup ->
        OscUdpClient(h, p, setup)
    },
) : MixerRoutingPort {
    private fun openClient(): OscUdpClient = clientFactory(host, port, socketSetup)

    companion object {
        /** Diagnostic hook for failed post-write verification (set from app). */
        @Volatile
        var onVerifyFailure: ((
            channelIndex: Int,
            target: XAirChannelInputState,
            live: XAirChannelInputState?,
            replyPaths: Set<String>,
        ) -> Unit)? = null

        /** Last failed write+query verification (for user-facing error detail). */
        @Volatile
        var lastVerifyFailure: RoutingConfirmResult? = null
    }

    override suspend fun probe(timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            openClient().use { client ->
                val replies = client.query(listOf(OscPath.info()), timeoutMs = timeoutMs, rounds = 2, label = "probe")
                replies.isNotEmpty()
            }
        }.getOrDefault(false)
    }

    override suspend fun readChannelInput(channelIndex: Int): XAirChannelInputState? =
        readChannelInputs(listOf(channelIndex))[channelIndex]

    override suspend fun readAllChannelInputs(): Map<Int, XAirChannelInputState> =
        readChannelInputs((0 until XAirInputSourceCatalog.CHANNEL_COUNT).toList())

    override suspend fun readChannelInputs(channelIndices: Iterable<Int>): Map<Int, XAirChannelInputState> =
        withContext(Dispatchers.IO) {
            val indices = XAirInputSourceCatalog.routableIndices(channelIndices).toList()
            if (indices.isEmpty()) return@withContext emptyMap()
            runCatching {
                openClient().use { client ->
                    readChannelInputsOnClient(client, indices)
                }
            }.getOrDefault(emptyMap())
        }

    override suspend fun captureAndApplyRouting(
        channelIndices: Iterable<Int>,
        targets: Map<Int, XAirChannelInputState>,
        deferApply: Boolean,
        soundcheck: Boolean,
        probeTimeoutMs: Long,
    ): RoutingCaptureApplyResult = withContext(Dispatchers.IO) {
        val indices = XAirInputSourceCatalog.routableIndices(channelIndices).toList()
        if (indices.isEmpty()) {
            return@withContext RoutingCaptureApplyResult(
                reachable = true,
                liveByChannel = emptyMap(),
                applied = true,
            )
        }
        val sessionT0 = System.nanoTime()
        lastVerifyFailure = null
        runCatching {
            openClient().use { client ->
                wireVerifyFailureHook()
                val probeOk = Xr18RoutingLog.step("session probe") {
                    val replies = client.query(
                        listOf(OscPath.info()),
                        timeoutMs = probeTimeoutMs,
                        rounds = 2,
                        label = "probe",
                    )
                    replies.isNotEmpty()
                }
                if (!probeOk) {
                    return@withContext RoutingCaptureApplyResult(
                        reachable = false,
                        liveByChannel = emptyMap(),
                        applied = false,
                    )
                }
                val live = Xr18RoutingLog.stepSuspend("session read ${indices.size} ch") {
                    readChannelInputsOnClient(client, indices)
                }
                if (deferApply) {
                    val totalMs = (System.nanoTime() - sessionT0) / 1_000_000
                    Xr18RoutingLog.info("session capture-only total ${totalMs}ms (${indices.size} ch)")
                    return@withContext RoutingCaptureApplyResult(
                        reachable = true,
                        liveByChannel = live,
                        applied = true,
                    )
                }
                val scopedTargets = indices.associateWith { ch -> targets[ch]!! }
                val applied = Xr18RoutingLog.stepSuspend("session apply") {
                    Xr18RoutingOsc.applyChannelTargetsBatch(client, scopedTargets, live)
                }
                if (applied) lastVerifyFailure = null
                val totalMs = (System.nanoTime() - sessionT0) / 1_000_000
                Xr18RoutingLog.info(
                    "session ${if (soundcheck) "soundcheck" else "record"} total ${totalMs}ms " +
                        "applied=$applied (${indices.size} ch)",
                )
                RoutingCaptureApplyResult(
                    reachable = true,
                    liveByChannel = live,
                    applied = applied,
                )
            }
        }.getOrDefault(
            RoutingCaptureApplyResult(reachable = false, liveByChannel = emptyMap(), applied = false),
        )
    }

    override suspend fun writeChannelInput(channelIndex: Int, state: XAirChannelInputState): Boolean =
        writeChannelInputVerified(channelIndex, state)

    override suspend fun writeChannelInputOnly(channelIndex: Int, state: XAirChannelInputState) {
        withContext(Dispatchers.IO) {
            val ch = channelIndex + 1
            if (ch !in 1..XAirInputSourceCatalog.CHANNEL_COUNT) return@withContext
            runCatching {
                openClient().use { client ->
                    Xr18RoutingOsc.sendChannelTarget(client, channelIndex, state)
                }
            }
        }
    }

    override suspend fun confirmChannelRouting(
        channelIndex: Int,
        target: XAirChannelInputState,
    ): RoutingConfirmResult = withContext(Dispatchers.IO) {
        runCatching {
            openClient().use { client ->
                val paths = Xr18RoutingOsc.channelQueryPaths(channelIndex)
                val replies = client.query(paths, timeoutMs = 1500, rounds = 2, label = "confirm ch=${channelIndex + 1}")
                val live = Xr18RoutingOsc.readChannel(replies, channelIndex)
                Xr18RoutingOsc.confirmAgainst(channelIndex, target, live, replies.keys)
            }
        }.getOrDefault(RoutingConfirmResult(channelIndex, target, live = null))
    }

    override suspend fun applyRecordRouting(
        channelIndices: Iterable<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean = applyChannelsVerified(channelIndices, liveByChannel) {
        XAirInputSourceCatalog.recordTarget(it)
    }

    override suspend fun applySoundcheckRouting(
        channelIndices: Iterable<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean = applyChannelsVerified(channelIndices, liveByChannel) {
        XAirInputSourceCatalog.soundcheckTarget(it)
    }

    override suspend fun restoreChannels(
        baseline: Map<Int, XAirChannelInputState>,
        channels: Set<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
    ): Boolean = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext true
        val sessionT0 = System.nanoTime()
        lastVerifyFailure = null
        runCatching {
            openClient().use { client ->
                wireVerifyFailureHook()
                val live = if (liveByChannel.isNotEmpty()) {
                    liveByChannel
                } else {
                    Xr18RoutingLog.stepSuspend("restore read ${channels.size} ch") {
                        readChannelInputsOnClient(client, channels)
                    }
                }
                val ok = Xr18RoutingLog.stepSuspend("restore apply") {
                    Xr18RoutingOsc.restoreChannelsBatch(client, baseline, channels, live)
                }
                if (ok) lastVerifyFailure = null
                val totalMs = (System.nanoTime() - sessionT0) / 1_000_000
                Xr18RoutingLog.info("restore session total ${totalMs}ms ok=$ok (${channels.size} ch)")
                ok
            }
        }.getOrDefault(false)
    }

    override suspend fun loadSnapshot(slot: Int): Boolean = withContext(Dispatchers.IO) {
        if (slot !in 1..64) return@withContext false
        runCatching {
            openClient().use { client ->
                client.send(OscPath.xremote())
                client.send(OscPath.snapLoad(), listOf(OscArgument.IntArg(slot)))
                Thread.sleep(300)
                true
            }
        }.getOrDefault(false)
    }

    private suspend fun readChannelInputsOnClient(
        client: OscUdpClient,
        channelIndices: Collection<Int>,
    ): Map<Int, XAirChannelInputState> {
        val indices = channelIndices.sorted()
        val paths = Xr18RoutingOsc.queryPathsForChannels(indices)
        val replies = client.query(paths, timeoutMs = 2000, rounds = 2, label = "read ${indices.size} ch")
        return Xr18RoutingOsc.parseChannels(replies, indices)
    }

    private fun wireVerifyFailureHook() {
        Xr18RoutingOsc.onVerifyFailure = { ch, target, live, replies ->
            val result = RoutingConfirmResult(ch, target, live, replies.keys)
            lastVerifyFailure = result
            onVerifyFailure?.invoke(ch, target, live, replies.keys)
        }
    }

    private suspend fun writeChannelInputVerified(
        channelIndex: Int,
        state: XAirChannelInputState,
    ): Boolean = withContext(Dispatchers.IO) {
        val ch = channelIndex + 1
        if (ch !in 1..XAirInputSourceCatalog.CHANNEL_COUNT) return@withContext false
        runCatching {
            openClient().use { client ->
                Xr18RoutingOsc.writeAndVerify(client, channelIndex, state)
            }
        }.getOrDefault(false)
    }

    private suspend fun applyChannelsVerified(
        channelIndices: Iterable<Int>,
        liveByChannel: Map<Int, XAirChannelInputState>,
        targetFor: (Int) -> XAirChannelInputState,
    ): Boolean = withContext(Dispatchers.IO) {
        val indices = XAirInputSourceCatalog.routableIndices(channelIndices).toList()
        if (indices.isEmpty()) return@withContext true
        val targets = indices.associateWith { ch -> targetFor(ch) }
        lastVerifyFailure = null
        runCatching {
            openClient().use { client ->
                wireVerifyFailureHook()
                val ok = Xr18RoutingOsc.applyChannelTargetsBatch(client, targets, liveByChannel)
                if (ok) lastVerifyFailure = null
                ok
            }
        }.getOrDefault(false)
    }
}
