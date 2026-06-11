package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OscIntTest {
    @Test
    fun oscInt_coercesFloatAndInt() {
        assertThat(oscInt(3)).isEqualTo(3)
        assertThat(oscInt(1.0f)).isEqualTo(1)
        assertThat(oscInt(0.0f)).isEqualTo(0)
        assertThat(oscInt("x")).isNull()
    }

    @Test
    fun readChannel_acceptsFloatOscValues() {
        val replies = mapOf(
            OscPath.channelPreampRtnSw(1) to listOf(1.0f),
            OscPath.channelConfigInsrc(1) to listOf(2.0f),
            OscPath.channelConfigRtnsrc(1) to listOf(0.0f),
        )
        val state = Xr18RoutingOsc.readChannel(replies, 0)
        assertThat(state?.usesUsbReturn).isTrue()
        assertThat(state?.rtnsrc).isEqualTo(0)
    }
}
