package org.openmultitrack.app.scribble

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
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
    private val OSC_PORTS = listOf(Xr18Mixer.DEFAULT_PORT, X32Mixer.DEFAULT_PORT)
    private val discoveryMutex = Mutex()

    private data class LanSubnet(
        val prefix: String,
        val broadcast: InetAddress,
    )

    suspend fun probeMixerAt(
        context: Context,
        host: String,
        timeoutMs: Long = 3000,
        port: Int = Xr18Mixer.DEFAULT_PORT,
    ): String? = withContext(Dispatchers.IO) {
        withMulticastLock(context) {
            val lan = collectLanSubnets(context)
            probeMixerOnPorts(host, timeoutMs, listOf(port))
        }
    }

    suspend fun discoverMixerIp(
        context: Context,
        preferHost: String? = null,
        timeoutMs: Long = 12_000,
    ): String? = withContext(Dispatchers.IO) {
        discoveryMutex.withLock {
            withMulticastLock(context) {
                val lan = collectLanSubnets(context)
                discoverMixerIpLocked(preferHost, timeoutMs, lan)
            }
        }
    }

    private fun discoverMixerIpLocked(
        preferHost: String?,
        timeoutMs: Long,
        lan: List<LanSubnet>,
    ): String? {
        preferHost?.let { saved ->
            probeMixerOnPorts(saved, timeoutMs = 2500, ports = OSC_PORTS)?.let {
                OmtLog.i("OscDiscovery", "found mixer at saved host $it")
                return it
            }
        }

        val prefixes = orderedSubnetPrefixes(lan, preferHost)
        val broadcastAddrs = lan.map { it.broadcast }.distinct()
        OmtLog.i(
            "OscDiscovery",
            "scanning prefixes=$prefixes broadcasts=${broadcastAddrs.map { it.hostAddress }} ports=$OSC_PORTS timeout=${timeoutMs}ms",
        )

        val perPortBudget = timeoutMs / OSC_PORTS.size
        for (port in OSC_PORTS) {
            val broadcastBudget = minOf(perPortBudget / 2, 3000L)
            OscUdpClient.discoverMixer(
                timeoutMs = broadcastBudget,
                port = port,
                subnetPrefixes = emptyList(),
                broadcastAddrs = broadcastAddrs,
            )?.let {
                OmtLog.i("OscDiscovery", "found mixer via broadcast at $it port=$port")
                return it
            }

            val scanBudget = perPortBudget - broadcastBudget
            if (scanBudget <= 0 || prefixes.isEmpty()) continue
            OscUdpClient.scanSubnetsForMixer(
                timeoutMs = scanBudget,
                port = port,
                subnetPrefixes = prefixes,
            )?.let {
                OmtLog.i("OscDiscovery", "found mixer via scan at $it port=$port")
                return it
            }
        }
        OmtLog.w("OscDiscovery", "mixer not found on LAN")
        return null
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
            for (link in props.linkAddresses) {
                val broadcast = ipv4Broadcast(link) ?: continue
                val prefix = ipv4Prefix(link.address) ?: continue
                subnets.add(LanSubnet(prefix, broadcast))
            }
        }
        if (subnets.isNotEmpty()) return subnets.toList()

        legacyWifiPrefix(context)?.let { prefix ->
            runCatching {
                val broadcast = InetAddress.getByName("$prefix.255")
                subnets.add(LanSubnet(prefix, broadcast))
            }
        }
        return subnets.toList()
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
