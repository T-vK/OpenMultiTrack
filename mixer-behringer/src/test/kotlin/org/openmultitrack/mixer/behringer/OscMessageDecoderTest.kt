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
}
