package org.openmultitrack.app.routing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.mixer.behringer.OscSocketSetup

/** Android LAN helpers for OSC UDP (multicast lock + Wi‑Fi socket binding). */
object OscLanSession {
    suspend fun <T> withMulticastLock(context: Context, block: suspend () -> T): T {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("omt-osc-routing")?.apply {
            setReferenceCounted(true)
            acquire()
        }
        return try {
            block()
        } finally {
            runCatching { lock?.release() }
        }
    }

    fun wifiSocketSetup(context: Context): OscSocketSetup = OscSocketSetup { socket ->
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return@OscSocketSetup
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val onLan = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!onLan) continue
            runCatching { network.bindSocket(socket) }
                .onSuccess {
                    OmtLog.d("OscLan", "OSC UDP socket bound to LAN network")
                }
                .onFailure { e ->
                    OmtLog.w("OscLan", "OSC UDP socket bind failed: ${e.message}")
                }
            return@OscSocketSetup
        }
        OmtLog.w("OscLan", "No Wi‑Fi/Ethernet network found for OSC UDP bind")
    }
}
