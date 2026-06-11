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
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.domain.remote.RemoteProtocol
import org.openmultitrack.domain.session.AppMode

/**
 * USB host e2e: record from XR18 and verify live waveform pixels on screen do not shift
 * horizontally as recording grows (with normalization enabled for quiet noise).
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class HostLiveWaveformStabilityE2eTest {
    @get:Rule(order = 0)
    val usbDeviceRule = UsbDeviceRule()

    @get:Rule(order = 1)
    val appRule = E2eAppRule(enableWaveformsAndVu = true)

    private var harness: E2eMixerHarness? = null
    private val capacitySlots =
        (15f * RemoteProtocol.LIVE_WAVEFORM_PEAKS_PER_SEC).toInt()

    @After
    fun tearDown() {
        runCatching { harness?.clientUnbindOnly() }
        harness = null
    }

    @Test
    fun liveRecording_existingWaveformBarsStayHorizontallyStable() {
        runBlocking {
            check(AppSettingsStore(appRule.appContext).recordWaveformNormalized) {
                "recordWaveformNormalized must be enabled for this test"
            }

            val h = E2eMixerHarness(appRule).also { harness = it }
            val ctrl = h.bindAndRegisterXr18()
            try {
                ctrl.setAppMode(AppMode.MULTITRACK_RECORD)
                ctrl.startRecording()
                E2eWait.untilRecording(ctrl, timeoutMs = 60_000)

                val probeSlot = 15
                var previousStrip = awaitWaveformStripWithBars()
                var previousCentroid = checkNotNull(
                    waveformStripSlotCentroid(previousStrip, probeSlot, capacitySlots),
                ) {
                    "slot $probeSlot bar not visible in baseline screenshot"
                }
                var previousRight = waveformStripRightEdge(previousStrip)
                check(previousRight >= 0) { "baseline waveform has no visible bars" }

                val snap = ctrl.state.value.waveformPeaks.values.firstOrNull()
                val maxPeak = snap?.peaks?.maxOrNull() ?: 0f
                Log.i(
                    E2eConfig.TAG,
                    "waveform baseline centroid=$previousCentroid right=$previousRight maxPeak=$maxPeak",
                )

                for (targetPeaks in listOf(60, 90, 120)) {
                    awaitLiveWaveformPeakCount(ctrl, minPeaks = targetPeaks)
                    delay(350)
                    appRule.runOnActivity { }

                    val strip = awaitWaveformStripWithBars()
                    val centroid = checkNotNull(
                        waveformStripSlotCentroid(strip, probeSlot, capacitySlots),
                    ) {
                        "slot $probeSlot bar not visible at peaks>=$targetPeaks"
                    }
                    val shiftPx = abs(centroid - previousCentroid)
                    check(shiftPx < 4.5f) {
                        "slot $probeSlot centroid shifted ${shiftPx}px (was $previousCentroid, now $centroid)"
                    }
                    val right = waveformStripRightEdge(strip)
                    assertThat(right).isAtLeast(previousRight)

                    val peakAtProbe = ctrl.state.value.waveformPeaks.values.firstOrNull()
                        ?.peaks?.getOrNull(probeSlot)
                    Log.i(
                        E2eConfig.TAG,
                        "pixel stable peaks>=$targetPeaks centroid=$centroid right=$right " +
                            "shift=${shiftPx}px peak@$probeSlot=$peakAtProbe",
                    )

                    previousCentroid = centroid
                    previousRight = right
                    previousStrip = strip
                }
            } finally {
                runCatching {
                    ctrl.stopRecording()
                    E2eWait.untilNotRecording(ctrl, timeoutMs = 30_000)
                }
            }
            Log.i(E2eConfig.TAG, "live waveform screenshot stability e2e passed")
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
        delay(150)
    }
}
