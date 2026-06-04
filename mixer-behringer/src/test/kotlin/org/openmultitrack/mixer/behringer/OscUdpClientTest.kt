package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OscUdpClientTest {
    @Test
    fun encodeOscMessage_infoPath() {
        val bytes = OscUdpClient.encodeOscMessage("/info", emptyList())
        assertThat(bytes.size).isAtLeast(8)
        assertThat(String(bytes, 0, 5)).isEqualTo("/info")
    }
}
