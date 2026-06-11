package org.openmultitrack.app.e2e

import androidx.compose.ui.graphics.asImageBitmap
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
import org.openmultitrack.app.ui.daw.RecordingProbeFrame
import org.openmultitrack.app.ui.daw.assertRecordingStabilityProbe
import org.openmultitrack.app.ui.daw.waveformRightEdgeX
import org.openmultitrack.domain.remote.RemoteProtocol
import org.openmultitrack.domain.session.AppMode

/**
 * USB host e2e: while recording from XR18, the live waveform must grow left-to-right
 * without interior columns regressing to background (real hardware, variable audio levels).
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class HostLiveWaveformLeftToRightE2eTest {
    @get:Rule(order = 0)
    val usbDeviceRule = UsbDeviceRule()

    @get:Rule(order = 1)
    val appRule = E2eAppRule()

    @get:Rule(order = 2)
    val waveformDisplayRule = E2eWaveformDisplayRule { appRule.appContext }

    private var harness: E2eMixerHarness? = null
    private val peaksPerSec = RemoteProtocol.LIVE_WAVEFORM_PEAKS_PER_SEC
    private val windowSec = 15f
    private val probeSeconds = 9

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
                delay(400)
                appRule.runOnActivity { }

                ctrl.startRecording()
                E2eWait.untilRecording(ctrl, timeoutMs = 60_000)

                val frames = ArrayList<RecordingProbeFrame>(probeSeconds)
                for (second in 1..probeSeconds) {
                    awaitRecordingElapsed(ctrl, second.toFloat())
                    awaitWaveformPeakCount(ctrl, second * peaksPerSec)
                    val peaks = ctrl.state.value.waveformPeaks.values.firstOrNull()?.peaks?.size ?: 0
                    val strip = awaitWaveformStripAtSecond(
                        second = second,
                        elapsedSec = peaks / peaksPerSec.toFloat(),
                        capacitySlots = (windowSec * peaksPerSec).toInt(),
                        peaksPerSec = peaksPerSec,
                        timeoutMs = 20_000,
                    )
                    frames.add(
                        RecordingProbeFrame(
                            checkIndex = second - 1,
                            elapsedSec = second.toFloat(),
                            peakCount = peaks,
                            image = strip.asImageBitmap(),
                        ),
                    )
                }

                assertRecordingStabilityProbe(
                    frames = frames,
                    windowSec = windowSec,
                    checksPerSec = 1,
                    probeSec = probeSeconds,
                    requireDenseInterior = false,
                )

                var previousRight = -1
                frames.forEach { frame ->
                    val right = waveformRightEdgeX(frame.image)
                    if (previousRight >= 0) {
                        assertThat(right).isAtLeast(previousRight)
                    }
                    previousRight = right
                }
                assertThat(previousRight).isGreaterThan(24)
            } finally {
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

}
