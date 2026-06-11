package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OscMessageDecoderTest {
    @Test
    fun decode_stringAndIntReply() {
        val payload = OscUdpClient.encodeOscMessage(
            "/ch/01/config/name",
            listOf(OscArgument.StringArg("Kick")),
        )
        val msg = OscMessageDecoder.decode(payload)
        assertThat(msg?.path).isEqualTo("/ch/01/config/name")
        assertThat(msg?.args).containsExactly("Kick")
    }

    @Test
    fun decode_xinfoReplyFromXr18() {
        val hex = "2f78696e666f00002c737373730000003139322e3136382e332e363300000000" +
            "585231382d36332d35342d36390000005852313800000000312e313800000000"
        val data = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val msg = OscMessageDecoder.decode(data)
        assertThat(msg?.path).isEqualTo("/xinfo")
        assertThat(msg?.args).hasSize(4)
        assertThat(msg?.args?.first()).isEqualTo("192.168.3.63")
    }

    @Test
    fun decode_intReply() {
        val payload = OscUdpClient.encodeOscMessage(
            "/ch/01/config/color",
            listOf(OscArgument.IntArg(3)),
        )
        val msg = OscMessageDecoder.decode(payload)
        assertThat(msg?.path).isEqualTo("/ch/01/config/color")
        assertThat(msg?.args?.first()).isEqualTo(3)
    }

    @Test
    fun decodeAll_bundleWithMultipleRoutingReplies() {
        val msg1 = OscUdpClient.encodeOscMessage(
            OscPath.channelPreampRtnSw(1),
            listOf(OscArgument.IntArg(0)),
        )
        val msg2 = OscUdpClient.encodeOscMessage(
            OscPath.channelConfigInsrc(1),
            listOf(OscArgument.IntArg(1)),
        )
        val msg3 = OscUdpClient.encodeOscMessage(
            OscPath.channelConfigRtnsrc(1),
            listOf(OscArgument.IntArg(0)),
        )
        val bundle = encodeBundle(listOf(msg1, msg2, msg3))
        val messages = OscMessageDecoder.decodeAll(bundle)
        assertThat(messages.map { it.path }).containsExactly(
            OscPath.channelPreampRtnSw(1),
            OscPath.channelConfigInsrc(1),
            OscPath.channelConfigRtnsrc(1),
        )
        assertThat(messages[0].args.first()).isEqualTo(0)
        assertThat(messages[1].args.first()).isEqualTo(1)
    }

    private fun encodeBundle(messages: List<ByteArray>): ByteArray {
        val out = ArrayList<Byte>()
        writePaddedString(out, "#bundle")
        repeat(8) { out.add(0xFF.toByte()) }
        for (msg in messages) {
            writeInt32(out, msg.size)
            out.addAll(msg.toList())
        }
        return out.toByteArray()
    }

    private fun writePaddedString(out: MutableList<Byte>, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
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
}
