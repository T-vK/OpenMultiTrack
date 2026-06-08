package org.openmultitrack.app.scribble

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.mixer.behringer.OscUdpClient
import org.openmultitrack.mixer.behringer.X32Mixer
import org.openmultitrack.mixer.behringer.Xr18Mixer
import java.net.Inet4Address
import java.net.InetAddress

/** Android-aware OSC mixer discovery (multicast lock + directed subnet broadcast). */
object OscLanDiscovery {
    private val discoveryMutex = Mutex()

    private data class LanSubnet(
        val prefix: String,
        val broadcast: InetAddress,
        val gateway: String?,
    )

    suspend fun probeMixerAt(
        context: Context,
        host: String,
        timeoutMs: Long = 3000,
        port: Int = Xr18Mixer.DEFAULT_PORT,
    ): String? = withContext(Dispatchers.IO) {
        withMulticastLock(context) {
            OmtLog.i("OscDiscovery", "probe $host port=$port timeout=${timeoutMs}ms")
            probeMixerOnPorts(host, timeoutMs, listOf(port))
        }
    }

    suspend fun discoverMixerIp(
        context: Context,
        preferHost: String? = null,
        timeoutMs: Long = 20_000,
    ): String? = withContext(Dispatchers.IO) {
        discoveryMutex.withLock {
            withMulticastLock(context) {
                val lan = collectLanSubnets(context)
                if (lan.isEmpty()) {
                    OmtLog.w("OscDiscovery", "no LAN subnets detected — check Wi-Fi/Ethernet")
                    return@withMulticastLock null
                }
                val attempts = 3
                val perAttemptMs = timeoutMs / attempts
                repeat(attempts) { attempt ->
                    OmtLog.i("OscDiscovery", "discovery attempt ${attempt + 1}/$attempts")
                    discoverMixerIpOnce(preferHost, perAttemptMs, lan)?.let { found ->
                        OmtLog.i("OscDiscovery", "found mixer at $found")
                        return@withMulticastLock found
                    }
                    if (attempt < attempts - 1) {
                        delay(400)
                    }
                }
                OmtLog.w("OscDiscovery", "mixer not found on LAN after $attempts attempts")
                null
            }
        }
    }

    private fun discoverMixerIpOnce(
        preferHost: String?,
        timeoutMs: Long,
        lan: List<LanSubnet>,
    ): String? {
        preferHost?.let { saved ->
            probeMixerOnPorts(saved, timeoutMs = 2000, ports = listOf(Xr18Mixer.DEFAULT_PORT, X32Mixer.DEFAULT_PORT))?.let {
                OmtLog.i("OscDiscovery", "found mixer at saved host $it")
                return it
            }
            OmtLog.i("OscDiscovery", "saved host $saved did not respond")
        }

        val prefixes = orderedSubnetPrefixes(lan, preferHost)
        val broadcastAddrs = lan.map { it.broadcast }.distinct()
        val priorityHosts = buildPriorityHosts(lan, preferHost)
        OmtLog.i(
            "OscDiscovery",
            "scan prefixes=$prefixes broadcasts=${broadcastAddrs.map { it.hostAddress }} " +
                "priority=$priorityHosts timeout=${timeoutMs}ms",
        )

        val xr18Budget = (timeoutMs * 0.75).toLong()
        findOnPort(
            port = Xr18Mixer.DEFAULT_PORT,
            budgetMs = xr18Budget,
            prefixes = prefixes,
            broadcastAddrs = broadcastAddrs,
            priorityHosts = priorityHosts,
        )?.let { return it }

        val x32Budget = timeoutMs - xr18Budget
        return findOnPort(
            port = X32Mixer.DEFAULT_PORT,
            budgetMs = x32Budget,
            prefixes = prefixes,
            broadcastAddrs = broadcastAddrs,
            priorityHosts = priorityHosts,
        )
    }

    private fun findOnPort(
        port: Int,
        budgetMs: Long,
        prefixes: List<String>,
        broadcastAddrs: List<InetAddress>,
        priorityHosts: List<String>,
    ): String? {
        if (budgetMs <= 0) return null
        val broadcastBudget = minOf(budgetMs / 2, 5000L)
        OscUdpClient.discoverMixer(
            timeoutMs = broadcastBudget,
            port = port,
            subnetPrefixes = emptyList(),
            broadcastAddrs = broadcastAddrs,
        )?.let {
            OmtLog.i("OscDiscovery", "found mixer via broadcast at $it port=$port")
            return it
        }

        val scanBudget = budgetMs - broadcastBudget
        if (scanBudget <= 0 || prefixes.isEmpty()) return null
        OscUdpClient.scanSubnetsForMixer(
            timeoutMs = scanBudget,
            port = port,
            subnetPrefixes = prefixes,
            priorityHosts = priorityHosts,
        )?.let {
            OmtLog.i("OscDiscovery", "found mixer via unicast scan at $it port=$port")
            return it
        }
        return null
    }

    private fun buildPriorityHosts(lan: List<LanSubnet>, preferHost: String?): List<String> {
        val hosts = LinkedHashSet<String>()
        preferHost?.let { hosts.add(it) }
        lan.mapNotNull { it.gateway }.forEach { hosts.add(it) }
        return hosts.toList()
    }

    private fun probeMixerOnPorts(
        host: String,
        timeoutMs: Long,
        ports: List<Int>,
    ): String? {
        for (port in ports) {
            OscUdpClient.probeMixerAt(host, timeoutMs, port)?.let { return it }
        }
        return null
    }

    private suspend fun <T> withMulticastLock(
        context: Context,
        block: suspend () -> T,
    ): T {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("omt-osc")?.apply {
            setReferenceCounted(true)
            acquire()
        }
        OmtLog.d("OscDiscovery", "multicast lock acquired=${lock != null}")
        return try {
            block()
        } finally {
            runCatching { lock?.release() }
        }
    }

    private fun collectLanSubnets(context: Context): List<LanSubnet> {
        val subnets = LinkedHashSet<LanSubnet>()
        val cm = context.getSystemService(ConnectivityManager::class.java)
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                continue
            }
            val props = cm.getLinkProperties(network) ?: continue
            val gateway = props.routes.firstGatewayHost()
            for (link in props.linkAddresses) {
                val broadcast = ipv4Broadcast(link) ?: continue
                val prefix = ipv4Prefix(link.address) ?: continue
                subnets.add(LanSubnet(prefix, broadcast, gateway))
            }
        }
        if (subnets.isNotEmpty()) return subnets.toList()

        legacyWifiPrefix(context)?.let { prefix ->
            runCatching {
                val broadcast = InetAddress.getByName("$prefix.255")
                subnets.add(LanSubnet(prefix, broadcast, gateway = "$prefix.1"))
            }
        }
        return subnets.toList()
    }

    private fun List<RouteInfo>.firstGatewayHost(): String? {
        for (route in this) {
            val gateway = route.gateway as? Inet4Address ?: continue
            if (gateway.isLoopbackAddress) continue
            val dest = route.destination?.address as? Inet4Address ?: continue
            if (dest.hostAddress == "0.0.0.0") {
                return gateway.hostAddress
            }
        }
        return null
    }

    private fun orderedSubnetPrefixes(lan: List<LanSubnet>, preferHost: String?): List<String> {
        val prefixes = LinkedHashSet<String>()
        if (preferHost != null) {
            preferHost.split(".").takeIf { it.size == 4 }?.let { parts ->
                prefixes.add("${parts[0]}.${parts[1]}.${parts[2]}")
            }
        }
        lan.forEach { prefixes.add(it.prefix) }
        OscUdpClient.localIpv4SubnetPrefixes().forEach { prefixes.add(it) }
        return prefixes.toList()
    }

    private fun ipv4Prefix(address: InetAddress): String? {
        if (address !is Inet4Address || address.isLoopbackAddress) return null
        val parts = address.hostAddress?.split(".") ?: return null
        if (parts.size != 4) return null
        return "${parts[0]}.${parts[1]}.${parts[2]}"
    }

    private fun ipv4Broadcast(link: LinkAddress): Inet4Address? {
        val address = link.address as? Inet4Address ?: return null
        if (address.isLoopbackAddress) return null
        val prefixLength = link.prefixLength
        if (prefixLength <= 0 || prefixLength > 32) return null
        val bytes = address.address
        if (bytes.size != 4) return null
        val host = bytes.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
        val broadcast = host or mask.inv()
        val broadcastAddr = Inet4Address.getByAddress(
            byteArrayOf(
                ((broadcast shr 24) and 0xFF).toByte(),
                ((broadcast shr 16) and 0xFF).toByte(),
                ((broadcast shr 8) and 0xFF).toByte(),
                (broadcast and 0xFF).toByte(),
            ),
        )
        return broadcastAddr as? Inet4Address
    }

    @Suppress("DEPRECATION")
    private fun legacyWifiPrefix(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val ip = wifi?.connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        val a = ip and 0xFF
        val b = ip shr 8 and 0xFF
        val c = ip shr 16 and 0xFF
        return "$a.$b.$c"
    }
}
