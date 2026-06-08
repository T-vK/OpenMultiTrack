package org.openmultitrack.app.scribble

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openmultitrack.mixer.behringer.OscSocketSetup
import org.openmultitrack.mixer.behringer.OscUdpClient
import org.openmultitrack.mixer.behringer.Xr18Mixer
import java.net.DatagramSocket
import java.net.Inet4Address

/** Android-aware OSC mixer discovery (Wi‑Fi network bind + multicast lock + subnet scan). */
object OscLanDiscovery {
    suspend fun probeMixerAt(
        context: Context,
        host: String,
        timeoutMs: Long = 3000,
        port: Int = Xr18Mixer.DEFAULT_PORT,
    ): String? = withContext(Dispatchers.IO) {
        withNetwork(context) { setup ->
            OscUdpClient.probeMixerAt(host, timeoutMs, port, setup)
        }
    }

    suspend fun discoverMixerIp(context: Context, timeoutMs: Long = 5000): String? =
        withContext(Dispatchers.IO) {
            withNetwork(context) { setup ->
                discoverMixerIpLocked(context, timeoutMs, setup)
            }
        }

    private fun discoverMixerIpLocked(
        context: Context,
        timeoutMs: Long,
        setup: OscSocketSetup,
    ): String? {
        val broadcastBudget = minOf(timeoutMs / 4, 1200L)
        OscUdpClient.discoverMixer(broadcastBudget, Xr18Mixer.DEFAULT_PORT, setup)?.let { return it }

        val prefix = subnetPrefix(context)
            ?: OscUdpClient.localIpv4SubnetPrefixes().firstOrNull()
            ?: return null

        val scanBudget = timeoutMs - broadcastBudget
        val perHostMs = 40L
        val deadline = System.nanoTime() + scanBudget * 1_000_000
        for (last in 1..254) {
            if (System.nanoTime() > deadline) break
            val host = "$prefix.$last"
            OscUdpClient.probeMixerAt(host, perHostMs, Xr18Mixer.DEFAULT_PORT, setup)?.let { return it }
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
        val network = wifiNetwork(context)
        return OscSocketSetup { socket: DatagramSocket ->
            network?.let { runCatching { it.bindSocket(socket) } }
        }
    }

    private fun wifiNetwork(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private fun subnetPrefix(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        for (network in cm.allNetworks) {
            val props = cm.getLinkProperties(network) ?: continue
            val ipv4 = props.linkAddresses.firstOrNull { addr ->
                val ip = addr.address
                ip is Inet4Address && !ip.isLoopbackAddress
            }?.address?.hostAddress ?: continue
            val parts = ipv4.split(".")
            if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}"
        }
        OscUdpClient.localIpv4SubnetPrefixes().firstOrNull()?.let { return it }
        return legacyWifiPrefix(context)
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
