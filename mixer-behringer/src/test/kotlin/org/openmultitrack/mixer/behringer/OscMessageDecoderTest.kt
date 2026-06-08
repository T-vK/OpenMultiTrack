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
