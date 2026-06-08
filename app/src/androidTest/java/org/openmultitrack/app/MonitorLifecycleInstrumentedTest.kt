package org.openmultitrack.app

import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.service.AudioSessionClient
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbAppProcessRule
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.app.test.UsbInstrumentedPermission
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService

/**
 * Validates monitor start → stop → start does not crash (regression for output invalidation).
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x00d4)
class MonitorLifecycleInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    @Test
    fun monitorStartStopStartSucceeds() = runBlocking {
        val client = AudioSessionClient(usbAppProcessRule.appContext)
        val managerReady = CompletableDeferred<org.openmultitrack.app.service.MultiMixerSessionManager>()
        client.whenReady { managerReady.complete(it) }
        client.bind()

        val manager = withTimeout(15_000) { managerReady.await() }
        try {
            val (mixerId, descriptor, probe) = usbAppProcessRule.runOnActivity { activity ->
                val enumerator = UsbAudioEnumerator(activity)
                val xr18 = enumerator.listUsbDevices().first {
                    it.vendorId == XR18_VENDOR_ID && it.productId == XR18_PRODUCT_ID
                }
                val usbManager = activity.getSystemService(UsbManager::class.java)
                val device = usbManager.deviceList[xr18.deviceName]
                    ?: error("XR18 not in UsbManager device list")
                UsbInstrumentedPermission.ensure(activity, usbManager, device)
                val profile = MixerDeviceStore(activity).addMixer(xr18)
                val probeResult = UsbAudioProbeService(enumerator).probe(xr18)
                Triple(profile.id, xr18, probeResult)
            }

            val profile = MixerDeviceStore(usbAppProcessRule.appContext).listMixers()
                .first { it.id == mixerId }
            manager.registerMixer(profile)
            manager.onProbeComplete(mixerId, descriptor, probe)

            val ctrl = manager.getOrCreate(mixerId)
            ctrl.startMonitoring()
            delay(2_000)
            assertThat(ctrl.state.value.isMonitoring).isTrue()

            ctrl.stopMonitoring()
            delay(500)
            assertThat(ctrl.state.value.isMonitoring).isFalse()

            ctrl.startMonitoring()
            withTimeout(10_000) {
                assertThat(ctrl.state.first { it.isMonitoring }.isMonitoring).isTrue()
            }
            delay(1_000)

            ctrl.stopMonitoring()
            delay(500)
            assertThat(ctrl.state.value.isMonitoring).isFalse()
        } finally {
            manager.shutdownAll()
            client.unbind()
        }
    }

    private companion object {
        const val XR18_VENDOR_ID = 0x1397
        const val XR18_PRODUCT_ID = 0x00d4
    }
}
