package org.openmultitrack.app.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkConnectivity {
    fun snapshot(context: Context): NetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val active = cm?.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val hasLan = caps?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } == true
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val transport = when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            else -> "Other"
        }
        return NetworkSnapshot(
            transport = transport,
            hasLanTransport = hasLan,
            hasValidatedInternet = hasInternet,
        )
    }
}

data class NetworkSnapshot(
    val transport: String,
    val hasLanTransport: Boolean,
    val hasValidatedInternet: Boolean,
)
