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
import org.openmultitrack.app.routing.SoundcheckTrackChannels
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.mixer.behringer.MixerRoutingPort
import org.openmultitrack.mixer.behringer.XAirChannelInputState
import org.openmultitrack.mixer.behringer.XAirInputSourceCatalog
import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.File

/**
 * App-path XR18 routing e2e on the tablet with the USB mixer (wireless adb: 192.168.3.42:46003).
 *
 * Exercises [RoutingAutomationBridge] hooks with USB capture/playback active — the conditions
 * that caused OSC verify failures on hardware. UI transport taps are attempted when visible.
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
    fun recordAndPlay_routingHooksWithUsbCaptureActive() = runBlocking {
        val h = E2eMixerHarness(appRule).also { harness = it }
        val ctrl = h.bindAndRegisterXr18()
        val (oscHost, port) = appRule.runOnActivity { activity ->
            runBlocking(Dispatchers.IO) {
                Xr18RoutingE2eHarness.configureProfileForRouting(activity, h.mixerId)
            }
        }
        h.applyOscHostOnActiveMixer(oscHost)
        h.syncUiWithHarnessMixer(h.mixerId, AppMode.MULTITRACK_RECORD)
        oscPort = port

        ctrl.setAppMode(AppMode.MULTITRACK_RECORD)
        E2eWait.untilMixerState(ctrl, 30_000) { it.probe != null && !it.probing }

        // Stress: USB capture on before record — must quiesce before OSC routing.
        ctrl.startMonitoring()
        E2eWait.untilMixerState(ctrl, 30_000) { it.isMonitoring }

        val routableArmed = Xr18RoutingE2eHarness.routableArmedChannels(ctrl)
        assertWithMessage("Need routable armed channels").that(routableArmed).isNotEmpty()

        baselineByChannel = routableArmed.associateWith { ch ->
            port.readChannelInput(ch) ?: error("Could not read baseline CH${ch + 1}")
        }
        Log.i(TAG, "baseline: ${baselineByChannel.map { (ch, st) -> "CH${ch + 1}=${Xr18RoutingE2eHarness.describe(st)}" }}")

        startRecordViaUiOrController(ctrl)
        E2eWait.untilRecording(ctrl, timeoutMs = 90_000)
        assertWithMessage("Record blocked: ${ctrl.state.value.warningMessage}")
            .that(ctrl.state.value.warningMessage)
            .isNull()

        delay(2_000)
        Xr18RoutingE2eHarness.assertChannelsConfirmed(
            port,
            routableArmed,
            XAirInputSourceCatalog::recordTarget,
            "after Record",
        )

        stopRecordViaUiOrController(ctrl)
        E2eWait.untilNotRecording(ctrl, timeoutMs = 60_000)
        delay(2_000)
        assertBaselinesRestored(port, routableArmed, "after Stop record")

        val sessionDir = ctrl.state.value.lastRecordingPath?.let { File(it) }
        assertThat(sessionDir?.isDirectory).isTrue()
        val metadata = SessionMetadata.read(sessionDir!!)
        assertThat(metadata).isNotNull()
        val trackChannels = SoundcheckTrackChannels.indicesWithTracks(sessionDir, metadata!!)
            .let { XAirInputSourceCatalog.routableIndices(it) }
        assertWithMessage("Session must have routable tracks").that(trackChannels).isNotEmpty()

        h.prepareSoundcheck(ctrl, sessionDir)
        h.syncUiWithHarnessMixer(h.mixerId, AppMode.VIRTUAL_SOUNDCHECK)
        E2eWait.untilSoundcheckReady(ctrl, timeoutMs = 180_000)

        startPlayViaUiOrController(ctrl)
        val playReady = E2eWait.pollUntil(timeoutMs = 90_000) {
            val s = ctrl.state.value
            s.playbackPositionSec > 0.25f || !s.warningMessage.isNullOrBlank()
        }
        assertThat(playReady).isTrue()
        assertWithMessage("Play failed: ${ctrl.state.value.warningMessage}")
            .that(ctrl.state.value.warningMessage)
            .isNull()
        assertThat(ctrl.state.value.playbackPositionSec).isGreaterThan(0.2f)

        delay(1_500)
        Xr18RoutingE2eHarness.assertChannelsConfirmed(
            port,
            trackChannels,
            XAirInputSourceCatalog::soundcheckTarget,
            "after Play",
        )

        stopPlayViaUiOrController(ctrl)
        E2eWait.untilNotPlaying(ctrl, timeoutMs = 60_000)
        val restored = E2eWait.pollUntil(timeoutMs = 60_000) {
            trackChannels.all { ch ->
                val baseline = baselineByChannel[ch] ?: return@all true
                val live = port.readChannelInput(ch)
                live != null && live.matchesRouting(baseline)
            }
        }
        assertWithMessage("Soundcheck restore did not revert mixer routing").that(restored).isTrue()
        for (ch in trackChannels) {
            val baseline = baselineByChannel[ch] ?: continue
            val live = port.readChannelInput(ch)
            Xr18RoutingE2eHarness.assertReadMatches(
                "after Stop playback CH${ch + 1}",
                ch,
                baseline,
                live,
            )
        }
    }

    private fun startRecordViaUiOrController(ctrl: org.openmultitrack.app.service.MixerSessionController) {
        if (runCatching { E2eUiTransport.clickContentDescription("Record", timeoutMs = 5_000) }.isSuccess) {
            Log.i(TAG, "transport: UI Record")
        } else {
            Log.i(TAG, "transport: controller startRecording")
            ctrl.startRecording()
        }
    }

    private fun stopRecordViaUiOrController(ctrl: org.openmultitrack.app.service.MixerSessionController) {
        if (runCatching {
                E2eUiTransport.clickContentDescription("Stop recording", timeoutMs = 5_000)
            }.isSuccess
        ) {
            Log.i(TAG, "transport: UI Stop recording")
        } else {
            Log.i(TAG, "transport: controller stopRecording")
            ctrl.stopRecording()
        }
    }

    private fun startPlayViaUiOrController(ctrl: org.openmultitrack.app.service.MixerSessionController) {
        if (runCatching { E2eUiTransport.clickContentDescription("Play", timeoutMs = 5_000) }.isSuccess) {
            Log.i(TAG, "transport: UI Play")
        } else {
            Log.i(TAG, "transport: controller playSoundcheckPlayback")
            ctrl.playSoundcheckPlayback()
        }
    }

    private fun stopPlayViaUiOrController(ctrl: org.openmultitrack.app.service.MixerSessionController) {
        if (runCatching {
                E2eUiTransport.clickContentDescription("Stop playback", timeoutMs = 5_000)
            }.isSuccess
        ) {
            Log.i(TAG, "transport: UI Stop playback")
        } else {
            Log.i(TAG, "transport: controller stopSoundcheck")
            ctrl.stopSoundcheck()
        }
    }

    private suspend fun assertBaselinesRestored(
        port: MixerRoutingPort,
        channels: Set<Int>,
        step: String,
    ) {
        for (ch in channels.sorted()) {
            val baseline = baselineByChannel[ch] ?: continue
            val live = port.readChannelInput(ch)
            Xr18RoutingE2eHarness.assertReadMatches("$step CH${ch + 1}", ch, baseline, live)
        }
    }

    private companion object {
        const val TAG = Xr18RoutingE2eHarness.TAG
    }
}
