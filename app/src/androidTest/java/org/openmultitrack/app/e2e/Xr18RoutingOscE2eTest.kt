package org.openmultitrack.app.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbAppProcessRule
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.mixer.behringer.MixerRoutingPort
import org.openmultitrack.mixer.behringer.RoutingConfirmResult
import org.openmultitrack.mixer.behringer.XAirChannelInputState

/**
 * End-to-end XR18 routing over LAN OSC.
 *
 * Each step **writes** routing, then **separately queries** live values and compares.
 * Logs use tag [Xr18RoutingE2eHarness.TAG] — filter logcat while iterating:
 *
 *     adb logcat -s Xr18RoutingE2e:I
 *
 * Run: `./scripts/run-xr18-routing-e2e.sh`
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class Xr18RoutingOscE2eTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    private var oscHost: String = ""
    private lateinit var port: MixerRoutingPort
    private val testChannel = 0
    private var baseline: XAirChannelInputState? = null

    @Before
    fun setUp() {
        runBlocking {
            usbAppProcessRule.runOnActivity { activity ->
                runBlocking(Dispatchers.IO) {
                    oscHost = Xr18RoutingE2eHarness.discoverOscHost(activity)
                    port = Xr18RoutingE2eHarness.routingPort(activity, oscHost)
                    Log.i(TAG, "OSC host=$oscHost")
                    assertThat(port.probe(timeoutMs = 3000)).isTrue()
                    baseline = port.readChannelInput(testChannel)
                    Log.i(
                        TAG,
                        "baseline CH${testChannel + 1}: " +
                            (baseline?.let(Xr18RoutingE2eHarness::describe) ?: "?"),
                    )
                    assertWithMessage("Could not read baseline routing for CH${testChannel + 1}")
                        .that(baseline)
                        .isNotNull()
                }
            }
        }
    }

    @After
    fun restoreBaseline() {
        val base = baseline ?: return
        runBlocking {
            usbAppProcessRule.runOnActivity {
                runBlocking(Dispatchers.IO) {
                    val ok = port.restoreChannels(mapOf(testChannel to base), setOf(testChannel))
                    val live = port.readChannelInput(testChannel)
                    Log.i(TAG, "restore baseline ok=$ok live=${live?.let(Xr18RoutingE2eHarness::describe)}")
                }
            }
        }
    }

    @Test
    fun separateWriteThenQuery_usbThenAdThenRestore() = runBlocking {
        usbAppProcessRule.runOnActivity { activity ->
            runBlocking(Dispatchers.IO) {
                val usb = Xr18RoutingE2eHarness.usbTarget(testChannel)
                val ad = Xr18RoutingE2eHarness.adTarget(testChannel)

                // 1) Write USB routing only (no built-in confirm loop)
                port.writeChannelInputOnly(testChannel, usb)
                delay(250)
                val afterUsb = port.confirmChannelRouting(testChannel, usb)
                Xr18RoutingE2eHarness.assertConfirmed("write USB → query", afterUsb)

                // 2) Write A/D routing only, then separate query
                port.writeChannelInputOnly(testChannel, ad)
                delay(250)
                val afterAd = port.confirmChannelRouting(testChannel, ad)
                Xr18RoutingE2eHarness.assertConfirmed("write A/D → query", afterAd)

                // 3) High-level apply helpers (write + confirm loop inside)
                val usbApplyOk = port.applySoundcheckRouting(setOf(testChannel))
                val afterUsbApply = port.confirmChannelRouting(testChannel, usb)
                Log.i(TAG, "applySoundcheckRouting ok=$usbApplyOk ${afterUsbApply.report()}")
                Xr18RoutingE2eHarness.assertConfirmed("applySoundcheckRouting", afterUsbApply)

                val adApplyOk = port.applyRecordRouting(setOf(testChannel))
                val afterAdApply = port.confirmChannelRouting(testChannel, ad)
                Log.i(TAG, "applyRecordRouting ok=$adApplyOk ${afterAdApply.report()}")
                Xr18RoutingE2eHarness.assertConfirmed("applyRecordRouting", afterAdApply)
            }
        }
    }

    @Test
    fun readChannelInput_matchesConfirmQuery() = runBlocking {
        usbAppProcessRule.runOnActivity {
            runBlocking(Dispatchers.IO) {
                val ad = Xr18RoutingE2eHarness.adTarget(testChannel)
                port.writeChannelInputOnly(testChannel, ad)
                delay(300)
                val fromRead = port.readChannelInput(testChannel)
                val fromConfirm = port.confirmChannelRouting(testChannel, ad)
                Log.i(TAG, "readChannelInput=${fromRead?.let(Xr18RoutingE2eHarness::describe)}")
                Log.i(TAG, "confirmChannelRouting=${fromConfirm.report()}")
                assertThat(fromRead).isEqualTo(fromConfirm.live)
                Xr18RoutingE2eHarness.assertConfirmed("read vs confirm", fromConfirm)
            }
        }
    }

    private companion object {
        const val TAG = Xr18RoutingE2eHarness.TAG
    }
}
