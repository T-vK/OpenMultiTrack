package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OscPathTest {
    @Test
    fun channelInputSource_zeroPadsChannel() {
        assertThat(OscPath.channelInputSource(1)).isEqualTo("/ch/01/in/src")
        assertThat(OscPath.channelInputSource(18)).isEqualTo("/ch/18/in/src")
    }

    @Test
    fun xAirChannelPaths_useConfigAndPreamp() {
        assertThat(OscPath.channelConfigInsrc(3)).isEqualTo("/ch/03/config/insrc")
        assertThat(OscPath.channelConfigRtnsrc(3)).isEqualTo("/ch/03/config/rtnsrc")
        assertThat(OscPath.channelPreampRtnSw(3)).isEqualTo("/ch/03/preamp/rtnsw")
        assertThat(OscPath.snapLoad()).isEqualTo("/-snap/load")
    }

    @Test
    fun x32_defaultPort_differsFromXr18() {
        assertThat(X32Mixer.DEFAULT_PORT).isEqualTo(10023)
        assertThat(Xr18Mixer.DEFAULT_PORT).isEqualTo(10024)
    }
}
