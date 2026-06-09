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
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.wav.SessionWaveformExtractor
import kotlin.math.abs

/** Isolated host test for waveform zoom + seek (requires a longer recording). */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class HostZoomE2eTest {
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
    fun waveformZoomThenSeekKeepsAccuratePlayhead() = runBlocking {
        val h = E2eMixerHarness(appRule).also { harness = it }
        val ctrl = h.bindAndRegisterXr18()
        val sessionDir = h.recordShortSession(ctrl, E2eConfig.ZOOM_RECORD_SECONDS)
        val meta = SessionMetadata.read(sessionDir)!!
        val fileDuration = SessionWaveformExtractor.durationSec(sessionDir, meta)
        assertThat(fileDuration).isGreaterThan(30f)
        h.prepareSoundcheck(ctrl, sessionDir)

        val duration = ctrl.state.value.playbackDurationSec.coerceAtLeast(fileDuration)
        assertThat(duration).isGreaterThan(30f)
        val seekTarget = duration * 0.35f
        val windowBefore = ctrl.state.value.soundcheckViewWindowSec

        ctrl.zoomSoundcheckView(scale = 2f, focalSec = seekTarget)
        delay(500)
        assertThat(ctrl.state.value.soundcheckViewWindowSec).isLessThan(windowBefore)

        assertThat(ctrl.state.value.soundcheckSampleRate).isGreaterThan(0)
        ctrl.seekSoundcheck(seekTarget)
        E2eWait.untilMixerState(ctrl, 45_000) {
            abs(it.playbackPositionSec - seekTarget) <= 2.5f
        }

        val startFrame = (seekTarget * ctrl.state.value.soundcheckSampleRate).toLong()
        ctrl.playSoundcheck(startFrame)
        E2eWait.untilPlaying(ctrl, timeoutMs = 60_000)
        E2eWait.untilMixerState(ctrl, 30_000) {
            it.isPlaying && it.playbackPositionSec >= seekTarget - 1f
        }
        assertThat(abs(ctrl.state.value.playbackPositionSec - seekTarget)).isAtMost(3f)

        ctrl.stopSoundcheck()
        E2eWait.untilNotPlaying(ctrl)
    }
}
