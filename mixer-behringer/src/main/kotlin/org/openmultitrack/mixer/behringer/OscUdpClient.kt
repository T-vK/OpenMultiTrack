package org.openmultitrack.mixer.behringer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets

/** Optional hook (e.g. bind UDP socket to a specific Android Wi‑Fi network) before send/receive. */
fun interface OscSocketSetup {
    fun configure(socket: DatagramSocket)
}

/** Minimal OSC-over-UDP client (no external deps). */
class OscUdpClient(
    private val host: String,
    private val port: Int,
) : AutoCloseable {
    private val socket = openUdpSocket()
    private val address: InetAddress = InetAddress.getByName(host)

    suspend fun send(path: String, args: List<OscArgument> = emptyList()) = withContext(Dispatchers.IO) {
        val payload = encodeOscMessage(path, args)
        val packet = DatagramPacket(payload, payload.size, address, port)
        socket.send(packet)
    }

    /** Query OSC paths; returns path → argument list for replies received within [timeoutMs]. */
    suspend fun query(paths: List<String>, timeoutMs: Long = 2500, rounds: Int = 4): Map<String, List<Any>> =
        withContext(Dispatchers.IO) {
            val pending = paths.toMutableSet()
            val replies = LinkedHashMap<String, List<Any>>()
            socket.soTimeout = 250
            repeat(rounds) {
                if (pending.isEmpty()) return@repeat
                for (path in pending.toList()) {
                    send(path)
                }
                val deadline = System.nanoTime() + timeoutMs * 1_000_000
                while (pending.isNotEmpty() && System.nanoTime() < deadline) {
                    try {
                        val buf = ByteArray(4096)
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = OscMessageDecoder.decode(buf.copyOf(packet.length)) ?: continue
                        if (msg.path in pending && msg.args.isNotEmpty()) {
                            replies[msg.path] = msg.args
                            pending.remove(msg.path)
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        break
                    }
                }
            }
            replies
        }

    override fun close() {
        socket.close()
    }

    companion object {
        fun openUdpSocket(setup: OscSocketSetup? = null): DatagramSocket =
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
                setup?.configure(this)
            }

        fun localIpv4Addresses(): List<InetAddress> {
            val ips = LinkedHashSet<InetAddress>()
            runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nic ->
                    if (!nic.isUp || nic.isLoopback) return@forEach
                    nic.inetAddresses.toList().forEach { addr ->
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            ips.add(addr)
                        }
                    }
                }
            }
            return ips.toList()
        }

        internal fun broadcastTargets(): List<InetAddress> {
            val targets = LinkedHashSet<InetAddress>()
            runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
            runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nic ->
                    if (!nic.isUp || nic.isLoopback) return@forEach
                    nic.interfaceAddresses.forEach { addr ->
                        val ip = addr.address
                        if (ip is Inet4Address && !ip.isLoopbackAddress) {
                            addr.broadcast?.let { targets.add(it) }
                        }
                    }
                }
            }
            return targets.toList()
        }

        fun discoverMixer(
            timeoutMs: Long = 3000,
            port: Int = Xr18Mixer.DEFAULT_PORT,
            setup: OscSocketSetup? = null,
        ): String? {
            val broadcastBudget = minOf(timeoutMs / 3, 1500L)
            discoverViaBroadcast(broadcastBudget, port, setup)?.let { return it }
            return discoverViaSubnetUnicast(timeoutMs - broadcastBudget, port, setup)
        }

        private fun discoverViaBroadcast(timeoutMs: Long, port: Int, setup: OscSocketSetup?): String? {
            val socket = try {
                openUdpSocket(setup).apply {
                    soTimeout = 250
                    broadcast = true
                }
            } catch (_: Exception) {
                return null
            }
            return try {
                val payload = encodeOscMessage(OscPath.xinfo(), emptyList())
                for (target in broadcastTargets()) {
                    socket.send(DatagramPacket(payload, payload.size, target, port))
                }
                waitForXinfoReply(socket, timeoutMs)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { socket.close() }
            }
        }

        private fun discoverViaSubnetUnicast(timeoutMs: Long, port: Int, setup: OscSocketSetup?): String? {
            if (timeoutMs <= 0) return null
            val prefixes = localIpv4SubnetPrefixes()
            if (prefixes.isEmpty()) return null
            val perHostMs = 35L
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            for (prefix in prefixes) {
                for (last in 1..254) {
                    if (System.nanoTime() > deadline) return null
                    val host = "$prefix.$last"
                    probeMixerAt(host, perHostMs, port, setup)?.let { return it }
                }
            }
            return null
        }

        fun probeMixerAt(
            host: String,
            timeoutMs: Long,
            port: Int = Xr18Mixer.DEFAULT_PORT,
            setup: OscSocketSetup? = null,
        ): String? {
            val socket = try {
                openUdpSocket(setup).apply {
                    soTimeout = timeoutMs.toInt().coerceAtLeast(20)
                    connect(InetAddress.getByName(host), port)
                }
            } catch (_: Exception) {
                return null
            }
            return try {
                for (path in listOf(OscPath.xinfo(), OscPath.info())) {
                    val payload = encodeOscMessage(path, emptyList())
                    socket.send(DatagramPacket(payload, payload.size))
                    waitForMixerReply(socket, timeoutMs / 2, host)?.let { return it }
                }
                null
            } catch (_: Exception) {
                null
            } finally {
                runCatching { socket.close() }
            }
        }

        private fun waitForMixerReply(socket: DatagramSocket, timeoutMs: Long, expectedHost: String): String? {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            while (System.nanoTime() < deadline) {
                val remainingMs = ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceIn(20, 500)
                socket.soTimeout = remainingMs
                try {
                    val buf = ByteArray(4096)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val from = packet.address.hostAddress ?: continue
                    val msg = OscMessageDecoder.decode(buf.copyOf(packet.length)) ?: continue
                    if (msg.path == "/xinfo" || msg.path == "/info") {
                        return if (expectedHost.isNotEmpty()) expectedHost else from
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                }
            }
            return null
        }

        private fun waitForXinfoReply(socket: DatagramSocket, timeoutMs: Long): String? {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            while (System.nanoTime() < deadline) {
                val remainingMs = ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceIn(20, 500)
                socket.soTimeout = remainingMs
                try {
                    val buf = ByteArray(4096)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val msg = OscMessageDecoder.decode(buf.copyOf(packet.length)) ?: continue
                    if (msg.path == "/xinfo" || msg.path == "/info") {
                        return packet.address.hostAddress
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                }
            }
            return null
        }

        fun localIpv4SubnetPrefixes(): List<String> {
            val prefixes = LinkedHashSet<String>()
            runCatching {
                NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nic ->
                    if (!nic.isUp || nic.isLoopback) return@forEach
                    nic.interfaceAddresses.forEach { addr ->
                        val ip = addr.address
                        if (ip is Inet4Address && !ip.isLoopbackAddress) {
                            val parts = ip.hostAddress?.split(".") ?: return@forEach
                            if (parts.size == 4) {
                                prefixes.add("${parts[0]}.${parts[1]}.${parts[2]}")
                            }
                        }
                    }
                }
            }
            return prefixes.toList()
        }

        fun encodeOscMessage(path: String, args: List<OscArgument>): ByteArray {
            val out = ArrayList<Byte>()
            writePaddedString(out, path)
            if (args.isEmpty()) {
                return out.toByteArray()
            }
            var typeTags = ","
            args.forEach { arg ->
                typeTags += when (arg) {
                    is OscArgument.IntArg -> "i"
                    is OscArgument.FloatArg -> "f"
                    is OscArgument.StringArg -> "s"
                }
            }
            writePaddedString(out, typeTags)
            args.forEach { arg ->
                when (arg) {
                    is OscArgument.IntArg -> writeInt32(out, arg.value)
                    is OscArgument.FloatArg -> writeFloat32(out, arg.value)
                    is OscArgument.StringArg -> writePaddedString(out, arg.value)
                }
            }
            return out.toByteArray()
        }

        private fun writePaddedString(out: MutableList<Byte>, value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            out.addAll(bytes.toList())
            out.add(0)
            val pad = (4 - ((bytes.size + 1) % 4)) % 4
            repeat(pad) { out.add(0) }
        }

        private fun writeInt32(out: MutableList<Byte>, value: Int) {
            out.add(((value shr 24) and 0xFF).toByte())
            out.add(((value shr 16) and 0xFF).toByte())
            out.add(((value shr 8) and 0xFF).toByte())
            out.add((value and 0xFF).toByte())
        }

        private fun writeFloat32(out: MutableList<Byte>, value: Float) {
            writeInt32(out, java.lang.Float.floatToIntBits(value))
        }
    }
}

sealed interface OscArgument {
    data class IntArg(val value: Int) : OscArgument
    data class FloatArg(val value: Float) : OscArgument
    data class StringArg(val value: String) : OscArgument
}
