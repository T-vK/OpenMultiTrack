package org.openmultitrack.app.scribble

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.mixer.behringer.OscSocketSetup
import org.openmultitrack.mixer.behringer.OscUdpClient
import org.openmultitrack.mixer.behringer.X32Mixer
import org.openmultitrack.mixer.behringer.Xr18Mixer
import java.net.DatagramSocket
import java.net.Inet4Address

/** Android-aware OSC mixer discovery (Wi‑Fi/Ethernet bind + multicast lock + subnet scan). */
object OscLanDiscovery {
    private val OSC_PORTS = listOf(Xr18Mixer.DEFAULT_PORT, X32Mixer.DEFAULT_PORT)

    suspend fun probeMixerAt(
        context: Context,
        host: String,
        timeoutMs: Long = 3000,
        port: Int = Xr18Mixer.DEFAULT_PORT,
    ): String? = withContext(Dispatchers.IO) {
        withNetwork(context) { setup ->
            probeMixerOnPorts(host, timeoutMs, listOf(port), setup)
        }
    }

    suspend fun discoverMixerIp(
        context: Context,
        preferHost: String? = null,
        timeoutMs: Long = 12_000,
    ): String? = withContext(Dispatchers.IO) {
        withNetwork(context) { setup ->
            discoverMixerIpLocked(context, preferHost, timeoutMs, setup)
        }
    }

    private fun discoverMixerIpLocked(
        context: Context,
        preferHost: String?,
        timeoutMs: Long,
        setup: OscSocketSetup,
    ): String? {
        preferHost?.let { saved ->
            probeMixerOnPorts(saved, timeoutMs = 2500, ports = OSC_PORTS, setup)?.let {
                OmtLog.i("OscDiscovery", "found mixer at saved host $it")
                return it
            }
        }

        val prefixes = orderedSubnetPrefixes(context, preferHost)
        OmtLog.i("OscDiscovery", "scanning subnets=$prefixes ports=$OSC_PORTS timeout=${timeoutMs}ms")

        val perPortBudget = timeoutMs / OSC_PORTS.size
        for (port in OSC_PORTS) {
            val broadcastBudget = minOf(perPortBudget / 3, 2000L)
            OscUdpClient.discoverMixer(broadcastBudget, port, setup)?.let {
                OmtLog.i("OscDiscovery", "found mixer via broadcast at $it port=$port")
                return it
            }

            val scanBudget = perPortBudget - broadcastBudget
            if (scanBudget <= 0 || prefixes.isEmpty()) continue
            val perHostMs = 80L
            val deadline = System.nanoTime() + scanBudget * 1_000_000
            for (prefix in prefixes) {
                for (last in 1..254) {
                    if (System.nanoTime() > deadline) break
                    val host = "$prefix.$last"
                    if (host == preferHost) continue
                    probeMixerOnPorts(host, perHostMs, listOf(port), setup)?.let {
                        OmtLog.i("OscDiscovery", "found mixer via scan at $it port=$port")
                        return it
                    }
                }
            }
        }
        OmtLog.w("OscDiscovery", "mixer not found on LAN")
        return null
    }

    private fun probeMixerOnPorts(
        host: String,
        timeoutMs: Long,
        ports: List<Int>,
        setup: OscSocketSetup,
    ): String? {
        for (port in ports) {
            OscUdpClient.probeMixerAt(host, timeoutMs, port, setup)?.let { return it }
        }
        return null
    }

    private suspend fun <T> withNetwork(
        context: Context,
        block: suspend (OscSocketSetup) -> T,
    ): T {
        val appContext = context.applicationContext
        val wifi = appContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("omt-osc")?.apply {
            setReferenceCounted(true)
            acquire()
        }
        val setup = socketSetup(appContext)
        return try {
            block(setup)
        } finally {
            runCatching { lock?.release() }
        }
    }

    private fun socketSetup(context: Context): OscSocketSetup {
        val network = preferredLanNetwork(context)
        return OscSocketSetup { socket: DatagramSocket ->
            network?.let { runCatching { it.bindSocket(socket) } }
        }
    }

    private fun preferredLanNetwork(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val networks = cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 0
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 1
                else -> return@mapNotNull null
            }
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return@mapNotNull null
            }
            transport to network
        }
        return networks.minByOrNull { it.first }?.second
    }

    private fun orderedSubnetPrefixes(context: Context, preferHost: String?): List<String> {
        val prefixes = LinkedHashSet<String>()
        if (preferHost != null) {
            preferHost.split(".").takeIf { it.size == 4 }?.let { parts ->
                prefixes.add("${parts[0]}.${parts[1]}.${parts[2]}")
            }
        }
        prefixes.addAll(subnetPrefixes(context))
        return prefixes.toList()
    }

    private fun subnetPrefixes(context: Context): List<String> {
        val prefixes = LinkedHashSet<String>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in cm.allNetworks) {
            val props = cm.getLinkProperties(network) ?: continue
            for (addr in props.linkAddresses) {
                val ip = addr.address
                if (ip is Inet4Address && !ip.isLoopbackAddress) {
                    val parts = ip.hostAddress?.split(".") ?: continue
                    if (parts.size == 4) {
                        prefixes.add("${parts[0]}.${parts[1]}.${parts[2]}")
                    }
                }
            }
        }
        OscUdpClient.localIpv4SubnetPrefixes().forEach { prefixes.add(it) }
        legacyWifiPrefix(context)?.let { prefixes.add(it) }
        return prefixes.toList()
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
