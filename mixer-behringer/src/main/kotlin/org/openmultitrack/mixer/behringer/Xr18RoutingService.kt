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
    }
    override suspend fun probe(timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            openClient().use { client ->
                val replies = client.query(listOf(OscPath.info()), timeoutMs = timeoutMs, rounds = 2)
                replies.isNotEmpty()
            }
        }.getOrDefault(false)
    }

    override suspend fun readChannelInput(channelIndex: Int): XAirChannelInputState? =
        withContext(Dispatchers.IO) {
            runCatching {
                openClient().use { client ->
                    val replies = client.query(
                        Xr18RoutingOsc.channelQueryPaths(channelIndex),
                        timeoutMs = 1500,
                        rounds = 3,
                    )
                    Xr18RoutingOsc.readChannel(replies, channelIndex)
                }
            }.getOrNull()
        }

    override suspend fun readAllChannelInputs(): Map<Int, XAirChannelInputState> =
        withContext(Dispatchers.IO) {
            runCatching {
                openClient().use { client ->
                    val replies = client.query(Xr18RoutingOsc.queryPaths(), timeoutMs = 4000, rounds = 5)
                    Xr18RoutingOsc.parseAllChannels(replies)
                }
            }.getOrDefault(emptyMap())
        }

    override suspend fun writeChannelInput(channelIndex: Int, state: XAirChannelInputState): Boolean =
        writeChannelInputVerified(channelIndex, state)

    override suspend fun applyRecordRouting(channelIndices: Iterable<Int>): Boolean =
        applyChannelsVerified(channelIndices) { XAirInputSourceCatalog.recordTarget(it) }

    override suspend fun applySoundcheckRouting(channelIndices: Iterable<Int>): Boolean =
        applyChannelsVerified(channelIndices) { XAirInputSourceCatalog.soundcheckTarget(it) }

    override suspend fun restoreChannels(
        baseline: Map<Int, XAirChannelInputState>,
        channels: Set<Int>,
    ): Boolean = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext true
        runCatching {
            openClient().use { client ->
                Xr18RoutingOsc.onVerifyFailure = { ch, target, live, replies ->
                    onVerifyFailure?.invoke(ch, target, live, replies.keys)
                }
                for (ch in channels.sorted()) {
                    val target = baseline[ch] ?: continue
                    if (!Xr18RoutingOsc.writeAndVerify(client, ch, target)) {
                        return@withContext false
                    }
                }
                true
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
        targetFor: (Int) -> XAirChannelInputState,
    ): Boolean = withContext(Dispatchers.IO) {
        val indices = channelIndices.toList()
        if (indices.isEmpty()) return@withContext true
        runCatching {
            openClient().use { client ->
                Xr18RoutingOsc.onVerifyFailure = { ch, target, live, replies ->
                    onVerifyFailure?.invoke(ch, target, live, replies.keys)
                }
                client.send(OscPath.xremote())
                for (ch in indices.sorted()) {
                    val target = targetFor(ch)
                    if (!Xr18RoutingOsc.writeAndVerify(client, ch, target)) {
                        return@withContext false
                    }
                }
                true
            }
        }.getOrDefault(false)
    }
}
