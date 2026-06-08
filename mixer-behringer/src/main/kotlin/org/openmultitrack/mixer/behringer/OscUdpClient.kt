package org.openmultitrack.mixer.behringer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets
/** Minimal OSC-over-UDP client (no external deps). */
class OscUdpClient(
    private val host: String,
    private val port: Int,
) : AutoCloseable {
    private val socket = DatagramSocket()
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
        fun discoverMixer(timeoutMs: Long = 3000, port: Int = Xr18Mixer.DEFAULT_PORT): String? {
            val socket = DatagramSocket()
            socket.soTimeout = 500
            socket.broadcast = true
            socket.reuseAddress = true
            return try {
                val payload = encodeOscMessage(OscPath.xinfo(), emptyList())
                val broadcast = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(payload, payload.size, broadcast, port))
                val deadline = System.nanoTime() + timeoutMs * 1_000_000
                while (System.nanoTime() < deadline) {
                    try {
                        val buf = ByteArray(4096)
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = OscMessageDecoder.decode(buf.copyOf(packet.length)) ?: continue
                        if (msg.path == "/xinfo" && msg.args.isNotEmpty()) {
                            return packet.address.hostAddress
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    }
                }
                null
            } finally {
                socket.close()
            }
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
