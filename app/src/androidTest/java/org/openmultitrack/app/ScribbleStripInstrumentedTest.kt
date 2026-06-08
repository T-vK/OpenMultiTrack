package org.openmultitrack.app

import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbAppProcessRule
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.app.test.UsbInstrumentedPermission
import org.openmultitrack.app.scribble.OscLanDiscovery
import org.openmultitrack.mixer.behringer.Xr18ScribbleImporter
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService

/**
 * Validates XR18 scribble strip import over LAN OSC while USB is attached.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x00d4)
class ScribbleStripInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    @Test
    fun importUsbChannelLabelsFromOsc() {
        runBlocking {
        val labels = usbAppProcessRule.runOnActivity { activity ->
            runBlocking(Dispatchers.IO) {
                val discovered = OscLanDiscovery.probeMixerAt(activity, "192.168.3.63", timeoutMs = 5000)
                    ?: OscLanDiscovery.discoverMixerIp(activity, timeoutMs = 12000)
                    ?: error("XR18 not found on LAN — tablet and mixer must share a network")
                Xr18ScribbleImporter().fetchUsbLabels(discovered).getOrThrow()
            }
        }
        assertThat(labels).hasSize(18)
        assertThat(labels.count { !it.name.isNullOrBlank() }).isAtLeast(16)

        val ch1 = labels.first { it.usbChannel == 1 }
        assertThat(ch1.name).isNotNull()
        assertThat(ch1.name).isNotEmpty()
        assertThat(ch1.colorIndex).isNotNull()
        assertThat(ch1.colorArgb).isNotNull()
        assertThat(ch1.name).contains("Bass")

        usbAppProcessRule.runOnActivity { activity ->
            val enumerator = UsbAudioEnumerator(activity)
            val xr18 = enumerator.listUsbDevices().first {
                it.vendorId == XR18_VENDOR_ID && it.productId == XR18_PRODUCT_ID
            }
            val usbManager = activity.getSystemService(UsbManager::class.java)
            val device = usbManager.deviceList[xr18.deviceName]
                ?: error("XR18 not in UsbManager device list")
            UsbInstrumentedPermission.ensure(activity, usbManager, device)
            MixerDeviceStore(activity).addMixer(xr18)
            UsbAudioProbeService(enumerator).probe(xr18)
        }
        }
    }

    private companion object {
        const val XR18_VENDOR_ID = 0x1397
        const val XR18_PRODUCT_ID = 0x00d4
    }
}
