package org.openmultitrack.mixer.behringer

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Decodes OSC 1.0 messages (including [#bundle](https://opensoundcontrol.stanford.edu/spec-1_0-html)) from UDP payloads. */
object OscMessageDecoder {
    data class Message(val path: String, val args: List<Any>)

    /** Decode a single packet — one message or all messages from an OSC bundle. */
    fun decodeAll(data: ByteArray): List<Message> {
        if (data.isEmpty()) return emptyList()
        val pathEnd = indexOfNull(data, 0)
        if (pathEnd < 0) return emptyList()
        val path = String(data, 0, pathEnd, StandardCharsets.UTF_8)
        return if (path == "#bundle") {
            decodeBundle(data, align4(pathEnd + 1))
        } else {
            decodeMessage(data, 0)?.let { listOf(it) }.orEmpty()
        }
    }

    fun decode(data: ByteArray): Message? = decodeAll(data).firstOrNull()

    private fun decodeBundle(data: ByteArray, offset: Int): List<Message> {
        if (offset + 8 > data.size) return emptyList()
        var pos = offset + 8 // timetag
        val messages = mutableListOf<Message>()
        while (pos + 4 <= data.size) {
            val size = readInt32(data, pos)
            pos += 4
            if (size <= 0 || pos + size > data.size) break
            decodeMessage(data, pos)?.let { messages.add(it) }
            pos += size
        }
        return messages
    }

    private fun decodeMessage(data: ByteArray, start: Int): Message? {
        if (start >= data.size) return null
        val pathEnd = indexOfNull(data, start)
        if (pathEnd < 0) return null
        val path = String(data, start, pathEnd - start, StandardCharsets.UTF_8)
        var offset = align4(pathEnd + 1)
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
