package org.openmultitrack.app.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.mixer.behringer.MixerRoutingPort
import org.openmultitrack.mixer.behringer.XAirChannelInputState
import org.openmultitrack.mixer.behringer.XAirInputSourceCatalog

/**
 * App-path XR18 routing e2e: taps Record/Stop and Play/Stop in the real UI and verifies
 * OSC input routing via the same hooks stack as production ([RoutingAutomationBridge]).
 *
 * Run: `./scripts/run-xr18-routing-e2e.sh --osc-host <mixer-ip>`
 *
 * Logcat: `adb logcat -s Xr18RoutingE2e:I RoutingHooks:W Xr18Routing:W`
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class Xr18RoutingAppE2eTest {
    @get:Rule(order = 0)
    val appRule = E2eAppRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    private var harness: E2eMixerHarness? = null
    private var oscPort: MixerRoutingPort? = null
    private var baselineByChannel: Map<Int, XAirChannelInputState> = emptyMap()

    @After
    fun tearDown() {
        runBlocking {
            val port = oscPort
            if (port != null && baselineByChannel.isNotEmpty()) {
                runCatching {
                    port.restoreChannels(baselineByChannel, baselineByChannel.keys)
                }
            }
        }
        runCatching { harness?.shutdown() }
        harness = null
        oscPort = null
    }

    @Test
    fun uiRecordAndPlay_changeOscRoutingAndRestore() = runBlocking {
        val h = E2eMixerHarness(appRule).also { harness = it }
        val ctrl = h.bindAndRegisterXr18()
        val (oscHost, port) = appRule.runOnActivity { activity ->
            runBlocking(Dispatchers.IO) {
                Xr18RoutingE2eHarness.configureProfileForRouting(activity, h.mixerId)
            }
        }
        h.applyOscHostOnActiveMixer(oscHost)
        oscPort = port

        ctrl.setAppMode(AppMode.MULTITRACK_RECORD)
        E2eWait.untilMixerState(ctrl, 30_000) { it.appMode == AppMode.MULTITRACK_RECORD }

        val routableArmed = Xr18RoutingE2eHarness.routableArmedChannels(ctrl)
        assertWithMessage("Need at least one routable armed channel")
            .that(routableArmed)
            .isNotEmpty()
        Log.i(TAG, "routable armed channels: $routableArmed (capture=${ctrl.state.value.captureChannelCount})")

        baselineByChannel = routableArmed.associateWith { ch ->
            port.readChannelInput(ch) ?: error("Could not read baseline for CH${ch + 1}")
        }
        Log.i(TAG, "baseline: ${baselineByChannel.map { (ch, st) -> "CH${ch + 1}=${Xr18RoutingE2eHarness.describe(st)}" }}")

        // --- Record via UI ---
        E2eUiTransport.clickContentDescription("Record")
        val recordStarted = E2eWait.pollUntil(timeoutMs = 60_000) {
            val s = ctrl.state.value
            s.isRecording || s.warningMessage?.contains("routing", ignoreCase = true) == true
        }
        assertThat(recordStarted).isTrue()
        val afterRecordTap = ctrl.state.value
        assertWithMessage("Record blocked: ${afterRecordTap.warningMessage}")
            .that(afterRecordTap.isRecording)
            .isTrue()
        assertThat(afterRecordTap.warningMessage).isNull()

        delay(2_000)
        Xr18RoutingE2eHarness.assertChannelsConfirmed(
            port,
            routableArmed,
            XAirInputSourceCatalog::recordTarget,
            "after UI Record",
        )

        // --- Stop record via UI ---
        E2eUiTransport.clickContentDescription("Stop recording")
        E2eWait.untilNotRecording(ctrl, timeoutMs = 60_000)
        delay(1_500)
        for ((ch, baseline) in baselineByChannel) {
            val live = port.readChannelInput(ch)
            Xr18RoutingE2eHarness.assertReadMatches(
                "after UI Stop record CH${ch + 1}",
                ch,
                baseline,
                live,
            )
        }

        val sessionDir = ctrl.state.value.lastRecordingPath?.let { java.io.File(it) }
        assertThat(sessionDir?.isDirectory).isTrue()

        // --- Soundcheck play via UI ---
        h.prepareSoundcheck(ctrl, sessionDir!!)
        val trackChannels = routableArmed.intersect(
            ctrl.state.value.channelStrips.map { it.index }.toSet(),
        ).ifEmpty { routableArmed }
        Log.i(TAG, "soundcheck track channels for OSC check: $trackChannels")

        E2eUiTransport.clickContentDescription("Play")
        val playStarted = E2eWait.pollUntil(timeoutMs = 90_000) {
            val s = ctrl.state.value
            s.isPlaying || s.warningMessage?.contains("routing", ignoreCase = true) == true
        }
        assertThat(playStarted).isTrue()
        val afterPlayTap = ctrl.state.value
        assertWithMessage("Play blocked: ${afterPlayTap.warningMessage}")
            .that(afterPlayTap.isPlaying)
            .isTrue()
        assertThat(afterPlayTap.warningMessage).isNull()

        delay(2_000)
        Xr18RoutingE2eHarness.assertChannelsConfirmed(
            port,
            trackChannels,
            XAirInputSourceCatalog::soundcheckTarget,
            "after UI Play",
        )

        // --- Stop playback via UI ---
        E2eUiTransport.clickContentDescription("Stop playback")
        E2eWait.untilNotPlaying(ctrl, timeoutMs = 60_000)
        delay(1_500)
        for ((ch, baseline) in baselineByChannel) {
            if (ch !in trackChannels) continue
            val live = port.readChannelInput(ch)
            Xr18RoutingE2eHarness.assertReadMatches(
                "after UI Stop playback CH${ch + 1}",
                ch,
                baseline,
                live,
            )
        }
    }

    private companion object {
        const val TAG = Xr18RoutingE2eHarness.TAG
    }
}
