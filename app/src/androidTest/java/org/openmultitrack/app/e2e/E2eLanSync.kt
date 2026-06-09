package org.openmultitrack.app.e2e

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

/** Lightweight UDP coordination between host and client e2e test processes. */
object E2eLanSync {
    const val HOST_READY = "E2E_HOST_READY"
    const val CLIENT_DONE = "E2E_CLIENT_DONE"
    const val CLIENT_RECONNECT_DONE = "E2E_CLIENT_RECONNECT_DONE"

    fun signal(message: String, targetHost: String? = null) {
        val payload = message.toByteArray(StandardCharsets.UTF_8)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val targets = if (targetHost != null) {
                listOf(InetAddress.getByName(targetHost))
            } else {
                listOf(
                    InetAddress.getByName("255.255.255.255"),
                    InetAddress.getByName("224.0.0.1"),
                )
            }
            targets.forEach { addr ->
                runCatching {
                    socket.send(DatagramPacket(payload, payload.size, addr, E2eConfig.SYNC_PORT))
                }
            }
        }
        Log.i(E2eConfig.TAG, "sync sent: $message")
    }

    suspend fun await(message: String, timeoutMs: Long = 180_000): String = withContext(Dispatchers.IO) {
        withTimeout(timeoutMs) {
            DatagramSocket(E2eConfig.SYNC_PORT).use { socket ->
                socket.soTimeout = 500
                val buf = ByteArray(512)
                while (true) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                        if (text.startsWith(message)) {
                            Log.i(E2eConfig.TAG, "sync received: $text")
                            return@withTimeout text
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // keep listening
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
        }
    }
}
