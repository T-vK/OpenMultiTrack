package org.openmultitrack.app.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
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
 * USB host e2e: record from XR18 and verify live waveform data (and pixels when signal is present)
 * do not shift horizontally as recording grows.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class HostLiveWaveformStabilityE2eTest {
    @get:Rule(order = 0)
    val usbDeviceRule = UsbDeviceRule()

    @get:Rule(order = 1)
    val appRule = E2eAppRule(enableWaveformsAndVu = true)

    private var harness: E2eMixerHarness? = null

    @After
    fun tearDown() {
        runCatching { harness?.clientUnbindOnly() }
        harness = null
    }

    @Test
    fun liveRecording_existingWaveformBarsStayHorizontallyStable() {
        runBlocking {
            val h = E2eMixerHarness(appRule).also { harness = it }
            val ctrl = h.bindAndRegisterXr18()
            try {
                ctrl.setAppMode(AppMode.MULTITRACK_RECORD)
                ctrl.startRecording()
                E2eWait.untilRecording(ctrl, timeoutMs = 60_000)

                val probeIndex = 15
                var referencePeak: Float? = null
                var previousCentroid: Float? = null
                var previousRight = -1

                for (targetPeaks in listOf(30, 60, 90, 120)) {
                    awaitLiveWaveformPeakCount(ctrl, minPeaks = targetPeaks)
                    delay(300)

                    val snap = ctrl.state.value.waveformPeaks.values.firstOrNull()
                    checkNotNull(snap) { "no waveform snapshot in state at peaks>=$targetPeaks" }
                    check(snap.peaks.size >= targetPeaks) {
                        "expected >=$targetPeaks peaks, got ${snap.peaks.size}"
                    }
                    check(probeIndex < snap.peaks.size) {
                        "probe index $probeIndex out of range (${snap.peaks.size})"
                    }

                    val peakAtProbe = snap.peaks[probeIndex]
                    if (referencePeak == null) {
                        referencePeak = peakAtProbe
                    } else {
                        assertThat(peakAtProbe).isWithin(0.001f).of(referencePeak!!)
                    }
                    Log.i(
                        E2eConfig.TAG,
                        "peak buffer stable at index $probeIndex peaks>=$targetPeaks value=$peakAtProbe",
                    )

                    val hasVisibleSignal = snap.peaks.any { it > 0.01f } ||
                        ctrl.state.value.captureMeterLevels.values.any { it > 0.01f }
                    if (hasVisibleSignal) {
                        val strip = cropFirstWaveformStrip(captureDeviceScreen())
                        val centroid = waveformStripSlotCentroid(
                            strip,
                            slot = probeIndex,
                            capacitySlots = (15f * 30f).toInt(),
                        )
                        if (centroid != null) {
                            if (previousCentroid != null) {
                                val shiftPx = abs(centroid - previousCentroid!!)
                                check(shiftPx < 4.5f) {
                                    "slot $probeIndex centroid shifted ${shiftPx}px"
                                }
                            }
                            previousCentroid = centroid
                            val right = waveformStripRightEdge(strip)
                            if (previousRight >= 0) {
                                assertThat(right).isAtLeast(previousRight)
                            }
                            previousRight = right
                            Log.i(
                                E2eConfig.TAG,
                                "pixel stable peaks>=$targetPeaks centroid=$centroid right=$right",
                            )
                        }
                    }
                }
            } finally {
                runCatching {
                    ctrl.stopRecording()
                    E2eWait.untilNotRecording(ctrl, timeoutMs = 30_000)
                }
            }
            Log.i(E2eConfig.TAG, "live waveform host e2e passed")
        }
    }

    private suspend fun awaitLiveWaveformPeakCount(
        ctrl: org.openmultitrack.app.service.MixerSessionController,
        minPeaks: Int,
    ) {
        E2eWait.untilMixerState(ctrl, timeoutMs = 120_000) { state ->
            state.isRecording &&
                state.recordElapsedSec >= (minPeaks - 2) / 30f &&
                state.waveformPeaks.values.any { it.peaks.size >= minPeaks }
        }
        appRule.runOnActivity { }
        delay(150)
    }
}
