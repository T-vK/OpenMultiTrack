package org.openmultitrack.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.scribble.OscLanDiscovery
import org.openmultitrack.mixer.behringer.OscUdpClient
import org.openmultitrack.mixer.behringer.Xr18Mixer
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class OscLanDiscoveryInstrumentedTest {
    @Test
    fun probeKnownXr18Ip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val probed = OscLanDiscovery.probeMixerAt(context, "192.168.3.63", timeoutMs = 5000)
        assertThat(probed).isEqualTo("192.168.3.63")
    }

    @Test
    fun broadcastFindsXr18() = runBlocking {
        val discovered = OscUdpClient.discoverMixer(
            timeoutMs = 4000,
            port = Xr18Mixer.DEFAULT_PORT,
            broadcastAddrs = listOf(InetAddress.getByName("192.168.3.255")),
        )
        assertThat(discovered).isEqualTo("192.168.3.63")
    }

    @Test
    fun discoverXr18OnLan() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val discovered = OscLanDiscovery.discoverMixerIp(context, timeoutMs = 12000)
        assertThat(discovered).isEqualTo("192.168.3.63")
    }
}
