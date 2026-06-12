package org.openmultitrack.app.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.domain.session.AppMode

/**
 * FLOW 8 virtual soundcheck playback on the USB host tablet.
 *
 * Records a short session, plays for 6s (past the historical ~3.7s UAC2 stall), stops,
 * then plays again from 0s. Fails on stall warnings, early stop, or libusb IO errors in logcat.
 *
 * Run: ./scripts/run-flow8-soundcheck-e2e.sh --serial 192.168.3.62:45551
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.FLOW8_VENDOR_ID, productId = E2eConfig.FLOW8_PRODUCT_ID)
class Flow8SoundcheckPlaybackE2eTest {
    @get:Rule(order = 0)
    val appRule = Flow8E2eAppRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    private var harness: E2eMixerHarness? = null

    @After
    fun tearDown() {
        runCatching { harness?.shutdown() }
        harness = null
    }

    @Test
    fun soundcheckPlaybackSustainsAndSurvivesStopPlay() {
        runBlocking {
        val h = E2eMixerHarness(appRule).also { harness = it }
        val ctrl = h.bindAndRegisterFlow8()
        h.syncUiWithHarnessMixer(h.mixerId, AppMode.MULTITRACK_RECORD)

        // Need >6s of audio so the sustain window does not outlive the session.
        val sessionDir = h.recordShortSession(ctrl, seconds = 10)
        Log.i(TAG, "recorded session: ${sessionDir.absolutePath}")

        h.prepareSoundcheck(ctrl, sessionDir)
        h.syncUiWithHarnessMixer(h.mixerId, AppMode.VIRTUAL_SOUNDCHECK)
        E2eWait.untilSoundcheckReady(ctrl, timeoutMs = 180_000)
        assertThat(ctrl.state.value.playbackDurationSec).isGreaterThan(1f)

        clearAppLogcat()
        startPlayViaUiOrController(ctrl)
        assertPlayStarted(ctrl)

        Log.i(TAG, "first play: sustaining ${E2eConfig.FLOW8_PLAYBACK_OBSERVE_MS}ms")
        E2eWait.untilPlaybackSustains(
            ctrl,
            minAdvanceSec = E2eConfig.FLOW8_PLAYBACK_MIN_ADVANCE_SEC,
            observeMs = E2eConfig.FLOW8_PLAYBACK_OBSERVE_MS,
        )
        val firstPos = ctrl.state.value.playbackPositionSec
        Log.i(TAG, "first play sustained to ${firstPos}s")

        stopPlayViaUiOrController(ctrl)
        E2eWait.untilNotPlaying(ctrl, timeoutMs = 60_000)
        delay(1_000)

        val afterStopLog = E2eLogcat.dumpRecent(500, TAG, "MixerSession", "Player", "OmtE2e")
        E2eLogcat.assertNoPlaybackFaults(afterStopLog)

        clearAppLogcat()
        startPlayViaUiOrController(ctrl)
        assertPlayStarted(ctrl)

        Log.i(TAG, "second play: sustaining from near 0s")
        E2eWait.untilPlaybackSustains(
            ctrl,
            minAdvanceSec = E2eConfig.FLOW8_PLAYBACK_MIN_ADVANCE_SEC,
            observeMs = E2eConfig.FLOW8_PLAYBACK_OBSERVE_MS,
        )
        val secondPos = ctrl.state.value.playbackPositionSec
        Log.i(TAG, "second play sustained to ${secondPos}s")

        stopPlayViaUiOrController(ctrl)
        E2eWait.untilNotPlaying(ctrl, timeoutMs = 60_000)

        val finalLog = E2eLogcat.dumpRecent(600, TAG, "MixerSession", "Player", "OmtE2e")
        E2eLogcat.assertNoPlaybackFaults(finalLog)
        Log.i(TAG, "playback e2e passed (first=${firstPos}s second=${secondPos}s)")
        }
    }

    private fun clearAppLogcat() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("logcat -c")
    }

    private suspend fun assertPlayStarted(ctrl: org.openmultitrack.app.service.MixerSessionController) {
        val ready = E2eWait.pollUntil(timeoutMs = 90_000) {
            val s = ctrl.state.value
            s.isPlaying || !s.warningMessage.isNullOrBlank()
        }
        assertThat(ready).isTrue()
        assertWithMessage("Play failed: ${ctrl.state.value.warningMessage}")
            .that(ctrl.state.value.warningMessage)
            .isNull()
        assertThat(ctrl.state.value.isPlaying).isTrue()
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

    private companion object {
        const val TAG = "Flow8PlaybackE2e"
    }
}
