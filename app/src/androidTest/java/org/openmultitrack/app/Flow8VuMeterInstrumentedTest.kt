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
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.service.AudioSessionClient
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbAppProcessRule
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.app.test.UsbInstrumentedPermission
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService

/**
 * Verifies per-channel VU discrimination on Flow 8.
 *
 * Physical setup: channel 1 has a medium input signal; channel 2 is silent.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x050c)
class Flow8VuMeterInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    @Test
    fun vuMetersWorkWithoutMonitorEnabled() = runBlocking {
        withFlow8Controller { ctrl ->
            ctrl.syncVuMeterCapture()
            withTimeout(10_000) {
                assertThat(ctrl.state.first { it.isVuMetering }.isVuMetering).isTrue()
            }
            assertThat(ctrl.state.value.isMonitoring).isFalse()

            val sample = sampleVuLevels(ctrl, millis = 4_000)
            Log.i(TAG, "vu-only levels=$sample")
            assertThat(ctrl.state.value.isMonitoring).isFalse()
            assertThat(sample.maxCh1).isGreaterThan(0.005f)
        }
    }

    @Test
    fun channel1ReadsHigherThanSilentChannel2() = runBlocking {
        withFlow8Controller { ctrl ->
            ctrl.syncVuMeterCapture()
            withTimeout(10_000) {
                assertThat(ctrl.state.first { it.isVuMetering }.isVuMetering).isTrue()
            }

            val sample = sampleVuLevels(ctrl, millis = 6_000)
            val raw = ctrl.debugRawMeterPeaksForTest()
            Log.i(
                TAG,
                "ch1 vu=${sample.maxCh1} ch2 vu=${sample.maxCh2} " +
                    "rawCh1=${raw.getOrElse(0) { 0f }} rawCh2=${raw.getOrElse(1) { 0f }}",
            )

            assumeTrue(
                "Setup: feed medium signal to Flow 8 channel 1 and leave channel 2 silent",
                raw.getOrElse(0) { 0f } > 0.005f,
            )

            assertThat(sample.maxCh1).isGreaterThan(sample.maxCh2 + 0.02f)
            assertThat(raw.getOrElse(0) { 0f }).isGreaterThan(raw.getOrElse(1) { 0f } * 5f)
        }
    }

    private data class VuSample(
        val maxCh1: Float,
        val maxCh2: Float,
        val lastLevels: Map<Int, Float>,
    )

    private suspend fun sampleVuLevels(
        ctrl: org.openmultitrack.app.service.MixerSessionController,
        millis: Long,
    ): VuSample {
        var maxCh1 = 0f
        var maxCh2 = 0f
        var last = emptyMap<Int, Float>()
        repeat((millis / 100).toInt()) {
            delay(100)
            last = ctrl.state.value.captureMeterLevels
            maxCh1 = maxOf(maxCh1, last[0] ?: 0f)
            maxCh2 = maxOf(maxCh2, last[1] ?: 0f)
        }
        return VuSample(maxCh1, maxCh2, last)
    }

    private suspend fun withFlow8Controller(
        block: suspend (org.openmultitrack.app.service.MixerSessionController) -> Unit,
    ) {
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

            val settings = AppSettingsStore(usbAppProcessRule.appContext)
            settings.showVuMeters = true

            val ctrl = manager.getOrCreate(mixerId)
            block(ctrl)
            ctrl.syncVuMeterCapture()
            delay(300)
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
