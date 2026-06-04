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

    override fun close() {
        socket.close()
    }

    companion object {
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
