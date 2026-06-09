package org.openmultitrack.domain.remote

import java.net.URI
import java.util.UUID

data class RemotePairedHost(
    val hostId: String,
    val displayName: String,
    val pin: String,
)

object RemotePairing {
    const val QR_SCHEME = "omt"

    fun ensureHostDeviceId(existing: String?): String =
        existing?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

    fun generatePin(): String =
        (100_000..999_999).random().toString()

    fun buildPairingUri(hostId: String, pin: String, name: String, port: Int = RemoteProtocol.HTTP_PORT): String {
        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
        return "omt://remote?hostId=$hostId&pin=$pin&name=$encodedName&port=$port"
    }

    fun parsePairingUri(uri: String): RemotePairingPayload? = runCatching {
        val parsed = URI(uri.replace(" ", ""))
        if (parsed.scheme != QR_SCHEME) return null
        val query = parsed.rawQuery ?: return null
        val params = query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
        val hostId = params["hostId"] ?: return null
        val pin = params["pin"] ?: return null
        RemotePairingPayload(
            hostId = hostId,
            pin = pin,
            name = params["name"]?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: "OpenMultiTrack",
            port = params["port"]?.toIntOrNull() ?: RemoteProtocol.HTTP_PORT,
            host = params["host"],
        )
    }.getOrNull()
}

data class RemotePairingPayload(
    val hostId: String,
    val pin: String,
    val name: String,
    val port: Int,
    val host: String? = null,
)
