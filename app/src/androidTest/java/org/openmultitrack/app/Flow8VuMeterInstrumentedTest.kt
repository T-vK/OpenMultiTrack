package org.openmultitrack.app

import android.hardware.usb.UsbManager
import android.util.Log
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
 * Verifies live VU meter levels on Flow 8 channel 1 while monitoring USB capture.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x050c)
class Flow8VuMeterInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    @Test
    fun channel1VuMeterShowsInputWhileMonitoring() = runBlocking {
        val client = AudioSessionClient(usbAppProcessRule.appContext)
        val managerReady = CompletableDeferred<org.openmultitrack.app.service.MultiMixerSessionManager>()
        client.whenReady { managerReady.complete(it) }
        client.bind()

        val manager = withTimeout(15_000) { managerReady.await() }
        try {
            val (mixerId, descriptor, probe) = usbAppProcessRule.runOnActivity { activity ->
                val enumerator = UsbAudioEnumerator(activity)
                val flow8 = enumerator.listUsbDevices().first {
                    it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
                }
                val usbManager = activity.getSystemService(UsbManager::class.java)
                val device = usbManager.deviceList[flow8.deviceName]
                    ?: error("Flow 8 not in UsbManager device list")
                UsbInstrumentedPermission.ensure(activity, usbManager, device)
                val profile = MixerDeviceStore(activity).addMixer(flow8)
                val probeResult = UsbAudioProbeService(enumerator).probe(flow8)
                Triple(profile.id, flow8, probeResult)
            }

            val profile = MixerDeviceStore(usbAppProcessRule.appContext).listMixers()
                .first { it.id == mixerId }
            manager.registerMixer(profile)
            manager.onProbeComplete(mixerId, descriptor, probe)

            val ctrl = manager.getOrCreate(mixerId)
            ctrl.startMonitoring()
            withTimeout(10_000) {
                assertThat(ctrl.state.first { it.isMonitoring }.isMonitoring).isTrue()
            }

            var maxCh1 = 0f
            var lastSnapshot = emptyMap<Int, Float>()
            repeat(60) {
                delay(100)
                val levels = ctrl.state.value.captureMeterLevels
                lastSnapshot = levels
                maxCh1 = maxOf(maxCh1, levels[0] ?: 0f)
            }

            Log.i(TAG, "Flow8 VU ch1 max=$maxCh1 snapshot=$lastSnapshot")
            assertThat(maxCh1).isGreaterThan(0.005f)

            ctrl.stopMonitoring()
            delay(500)
        } finally {
            manager.shutdownAll()
            client.unbind()
        }
    }

    private companion object {
        const val TAG = "Flow8VuMeterTest"
        const val FLOW8_VENDOR_ID = 0x1397
        const val FLOW8_PRODUCT_ID = 0x050c
    }
}
