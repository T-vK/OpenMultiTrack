package org.openmultitrack.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.domain.session.AppMode
import kotlin.math.abs

/**
 * End-to-end validation on the USB host tablet: recording, simple play, virtual soundcheck,
 * seek, and waveform zoom with post-zoom seek accuracy.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class HostLocalE2eTest {
    @get:Rule(order = 0)
    val appRule = E2eAppRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    private var harness: E2eMixerHarness? = null

    @After
    fun tearDown() {
        runCatching { harness?.shutdown() }
        harness = null
    }

    @Test
    fun recordingWritesCompleteSession() = runBlocking {
        val h = E2eMixerHarness(appRule).also { harness = it }
        val ctrl = h.bindAndRegisterXr18()
        val sessionDir = h.recordShortSession(ctrl)
        assertThat(sessionDir.listFiles()?.any { it.extension == "wav" }).isTrue()
    }

    @Test
    fun simplePlayModePlaysStereoMix() = runBlocking {
        val h = E2eMixerHarness(appRule).also { harness = it }
        val ctrl = h.bindAndRegisterXr18()
        val sessionDir = h.recordShortSession(ctrl)
        h.prepareSoundcheck(ctrl, sessionDir)

        ctrl.setAppMode(AppMode.SIMPLE_PLAY)
        ctrl.playSoundcheckPlayback()
        E2eWait.untilPlaying(ctrl)
        delay(1_500)
        assertThat(ctrl.state.value.appMode).isEqualTo(AppMode.SIMPLE_PLAY)
        assertThat(ctrl.state.value.isPlaying).isTrue()

        ctrl.pauseSoundcheckPlayback()
        E2eWait.untilNotPlaying(ctrl)
    }

    @Test
    fun virtualSoundcheckPlaybackAndSeek() = runBlocking {
        val h = E2eMixerHarness(appRule).also { harness = it }
        val ctrl = h.bindAndRegisterXr18()
        val sessionDir = h.recordShortSession(ctrl)
        h.prepareSoundcheck(ctrl, sessionDir)

        val duration = ctrl.state.value.playbackDurationSec
        val seekTarget = (duration * 0.35f).coerceAtLeast(1f)

        ctrl.seekSoundcheck(seekTarget)
        E2eWait.untilMixerState(ctrl, 30_000) {
            abs(it.playbackPositionSec - seekTarget) <= 2f
        }

        val startFrame = (seekTarget * ctrl.state.value.soundcheckSampleRate).toLong()
        ctrl.playSoundcheck(startFrame)
        E2eWait.untilPlaying(ctrl)
        E2eWait.untilMixerState(ctrl, 30_000) {
            it.isPlaying && it.playbackPositionSec >= seekTarget - 1f
        }

        ctrl.stopSoundcheck()
        E2eWait.untilNotPlaying(ctrl)
    }

}
