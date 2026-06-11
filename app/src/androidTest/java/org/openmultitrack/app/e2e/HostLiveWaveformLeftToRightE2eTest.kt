package org.openmultitrack.app.e2e

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.app.ui.daw.assertLeftToRightWaveformGrowth
import org.openmultitrack.domain.remote.RemoteProtocol
import org.openmultitrack.domain.session.AppMode

/**
 * USB host e2e: while recording, waveform bars must appear left-to-right on screen.
 * Probes vertical-center pixels at second markers 1–9 on cropped screenshots.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class HostLiveWaveformLeftToRightE2eTest {
    @get:Rule(order = 0)
    val usbDeviceRule = UsbDeviceRule()

    @get:Rule(order = 1)
    val appRule = E2eAppRule(enableWaveformsAndVu = true)

    private var harness: E2eMixerHarness? = null
    private val peaksPerSec = RemoteProtocol.LIVE_WAVEFORM_PEAKS_PER_SEC
    private val capacitySlots = (15f * peaksPerSec).toInt()

    @After
    fun tearDown() {
        runCatching { harness?.clientUnbindOnly() }
        harness = null
    }

    @Test
    fun liveRecording_waveformBarsGrowLeftToRightOnScreen() {
        runBlocking {
            val h = E2eMixerHarness(appRule).also { harness = it }
            val ctrl = h.bindAndRegisterXr18()
            try {
                ctrl.setAppMode(AppMode.MULTITRACK_RECORD)

                val frames = linkedMapOf<Int, ImageBitmap>()
                val peakCounts = linkedMapOf<Int, Int>()
                delay(400)
                appRule.runOnActivity { }
                frames[0] = captureWaveformStrip().asImageBitmap()
                peakCounts[0] = 0

                ctrl.startRecording()
                E2eWait.untilRecording(ctrl, timeoutMs = 60_000)
                for (second in 1..9) {
                    awaitRecordingElapsed(ctrl, second.toFloat())
                    awaitWaveformPeakCount(ctrl, second * peaksPerSec)
                    val peaks = ctrl.state.value.waveformPeaks.values.firstOrNull()?.peaks?.size ?: 0
                    val strip = awaitWaveformStripAtSecond(
                        second = second,
                        elapsedSec = peaks / peaksPerSec.toFloat(),
                        capacitySlots = capacitySlots,
                        peaksPerSec = peaksPerSec,
                        timeoutMs = 20_000,
                    )
                    frames[second] = strip.asImageBitmap()
                    peakCounts[second] = peaks
                }

                assertLeftToRightWaveformGrowth(
                    framesByElapsedSec = frames,
                    capacitySlots = capacitySlots,
                    peaksPerSec = peaksPerSec,
                    windowSec = 15f,
                    maxSecond = 9,
                    requireLockedPixelValues = false,
                    peakCountByFrame = peakCounts,
                )
            } finally {
                // Avoid native libusb teardown crash; harness tearDown uses clientUnbindOnly().
                runCatching { ctrl.stopRecording() }
            }
        }
    }

    private suspend fun awaitRecordingElapsed(
        ctrl: org.openmultitrack.app.service.MixerSessionController,
        targetSec: Float,
    ) {
        E2eWait.untilMixerState(ctrl, timeoutMs = 120_000) { state ->
            state.isRecording && state.recordElapsedSec >= targetSec - 0.05f
        }
    }

    private suspend fun awaitWaveformPeakCount(
        ctrl: org.openmultitrack.app.service.MixerSessionController,
        minPeaks: Int,
    ) {
        E2eWait.untilMixerState(ctrl, timeoutMs = 30_000) { state ->
            state.isRecording &&
                state.waveformPeaks.values.any { it.peaks.size >= minPeaks - 2 }
        }
    }

    private fun captureWaveformStrip(): Bitmap = cropFirstWaveformStrip(captureDeviceScreen())
}
