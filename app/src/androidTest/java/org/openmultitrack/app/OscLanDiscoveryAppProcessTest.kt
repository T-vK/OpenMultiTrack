package org.openmultitrack.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.scribble.OscLanDiscovery
import org.openmultitrack.app.test.UsbAppProcessRule

/** Discovery in org.openmultitrack UID (same process as the real app). */
@RunWith(AndroidJUnit4::class)
class OscLanDiscoveryAppProcessTest {
    @get:Rule
    val appProcess = UsbAppProcessRule()

    @Test
    fun discoverXr18InAppProcess() {
        appProcess.runOnActivity { activity ->
            runBlocking {
                val discovered = OscLanDiscovery.discoverMixerIp(activity, timeoutMs = 12000)
                assertThat(discovered).isEqualTo("192.168.3.63")
            }
        }
    }

    @Test
    fun probeKnownIpInAppProcess() {
        appProcess.runOnActivity { activity ->
            runBlocking {
                val probed = OscLanDiscovery.probeMixerAt(activity, "192.168.3.63", timeoutMs = 5000)
                assertThat(probed).isEqualTo("192.168.3.63")
            }
        }
    }
}
