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
    private val clientFactory: (String, Int) -> OscUdpClient = { h, p -> OscUdpClient(h, p) },
) : MixerRoutingPort {
    override suspend fun probe(timeoutMs: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            clientFactory(host, port).use { client ->
                val replies = client.query(listOf(OscPath.info()), timeoutMs = timeoutMs, rounds = 2)
                replies.isNotEmpty()
            }
        }.getOrDefault(false)
    }

    override suspend fun readChannelInput(channelIndex: Int): XAirChannelInputState? =
        readAllChannelInputs()[channelIndex]

    override suspend fun readAllChannelInputs(): Map<Int, XAirChannelInputState> =
        withContext(Dispatchers.IO) {
            val paths = buildList {
                for (ch in 1..XAirInputSourceCatalog.CHANNEL_COUNT) {
                    add(OscPath.channelConfigInsrc(ch))
                    add(OscPath.channelConfigRtnsrc(ch))
                    add(OscPath.channelPreampRtnSw(ch))
                }
            }
            runCatching {
                clientFactory(host, port).use { client ->
                    val replies = client.query(paths, timeoutMs = 3000, rounds = 4)
                    buildMap {
                        for (ch in 1..XAirInputSourceCatalog.CHANNEL_COUNT) {
                            val idx = ch - 1
                            val insrc = (replies[OscPath.channelConfigInsrc(ch)]?.firstOrNull() as? Int) ?: continue
                            val rtnsrc = (replies[OscPath.channelConfigRtnsrc(ch)]?.firstOrNull() as? Int) ?: 0
                            val rtnSw = (replies[OscPath.channelPreampRtnSw(ch)]?.firstOrNull() as? Int) ?: 0
                            put(idx, XAirChannelInputState(insrc, rtnsrc, rtnSw))
                        }
                    }
                }
            }.getOrDefault(emptyMap())
        }

    override suspend fun writeChannelInput(channelIndex: Int, state: XAirChannelInputState): Boolean =
        withContext(Dispatchers.IO) {
            val ch = channelIndex + 1
            if (ch !in 1..XAirInputSourceCatalog.CHANNEL_COUNT) return@withContext false
            runCatching {
                clientFactory(host, port).use { client ->
                    client.send(OscPath.xremote())
                    if (state.usesUsbReturn) {
                        client.send(
                            OscPath.channelConfigRtnsrc(ch),
                            listOf(OscArgument.IntArg(state.rtnsrc)),
                        )
                        client.send(
                            OscPath.channelPreampRtnSw(ch),
                            listOf(OscArgument.IntArg(1)),
                        )
                    } else {
                        client.send(
                            OscPath.channelConfigInsrc(ch),
                            listOf(OscArgument.IntArg(state.insrc)),
                        )
                        client.send(
                            OscPath.channelPreampRtnSw(ch),
                            listOf(OscArgument.IntArg(0)),
                        )
                    }
                    true
                }
            }.getOrDefault(false)
        }

    override suspend fun loadSnapshot(slot: Int): Boolean = withContext(Dispatchers.IO) {
        if (slot !in 1..64) return@withContext false
        runCatching {
            clientFactory(host, port).use { client ->
                client.send(OscPath.xremote())
                client.send(OscPath.snapLoad(), listOf(OscArgument.IntArg(slot)))
                true
            }
        }.getOrDefault(false)
    }

    override suspend fun applyRecordRouting(channelIndices: Iterable<Int>): Boolean =
        channelIndices.all { writeChannelInput(it, XAirInputSourceCatalog.recordTarget(it)) }

    override suspend fun applySoundcheckRouting(channelIndices: Iterable<Int>): Boolean =
        channelIndices.all { writeChannelInput(it, XAirInputSourceCatalog.soundcheckTarget(it)) }

    override suspend fun restoreChannels(
        baseline: Map<Int, XAirChannelInputState>,
        channels: Set<Int>,
    ): Boolean = channels.all { ch ->
        val state = baseline[ch] ?: return@all true
        writeChannelInput(ch, state)
    }
}
