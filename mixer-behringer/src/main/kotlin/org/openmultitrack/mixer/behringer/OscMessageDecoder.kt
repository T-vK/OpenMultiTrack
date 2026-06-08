package org.openmultitrack.mixer.behringer

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Decodes OSC 1.0 messages from UDP payloads. */
object OscMessageDecoder {
    data class Message(val path: String, val args: List<Any>)

    fun decode(data: ByteArray): Message? {
        if (data.isEmpty()) return null
        var offset = 0
        val pathEnd = indexOfNull(data, offset)
        if (pathEnd < 0) return null
        val path = String(data, offset, pathEnd - offset, StandardCharsets.UTF_8)
        offset = align4(pathEnd + 1)
        if (offset >= data.size) return Message(path, emptyList())
        val tagsEnd = indexOfNull(data, offset)
        if (tagsEnd < 0) return Message(path, emptyList())
        val tags = String(data, offset, tagsEnd - offset, StandardCharsets.UTF_8)
        offset = align4(tagsEnd + 1)
        if (!tags.startsWith(",")) return Message(path, emptyList())
        val args = mutableListOf<Any>()
        for (tag in tags.drop(1)) {
            if (offset >= data.size) break
            when (tag) {
                'i' -> {
                    args.add(readInt32(data, offset))
                    offset += 4
                }
                'f' -> {
                    args.add(readFloat32(data, offset))
                    offset += 4
                }
                's' -> {
                    val sEnd = indexOfNull(data, offset)
                    if (sEnd < 0) break
                    val s = String(data, offset, sEnd - offset, StandardCharsets.UTF_8)
                    args.add(s)
                    offset = align4(sEnd + 1)
                }
                else -> break
            }
        }
        return Message(path, args)
    }

    private fun align4(n: Int): Int = (n + 3) and 0xFFFFFFFC.toInt()

    private fun indexOfNull(data: ByteArray, start: Int): Int {
        for (i in start until data.size) {
            if (data[i] == 0.toByte()) return i
        }
        return -1
    }

    private fun readInt32(data: ByteArray, offset: Int): Int {
        val buf = ByteBuffer.wrap(data, offset, 4)
        return buf.int
    }

    private fun readFloat32(data: ByteArray, offset: Int): Float {
        val bits = readInt32(data, offset)
        return java.lang.Float.intBitsToFloat(bits)
    }
}
