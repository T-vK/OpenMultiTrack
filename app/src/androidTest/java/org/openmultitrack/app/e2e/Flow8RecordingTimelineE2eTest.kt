package org.openmultitrack.app.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.service.MixerSessionController
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.session.SessionPlaybackDuration
import java.io.File

/**
 * Records via the on-screen transport, compares the visible timer to real elapsed time,
 * then verifies soundcheck duration and playback position against wall clock and disk.
 *
 * Run: ./scripts/run-flow8-recording-timeline-e2e.sh --serial 192.168.3.62:45551
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.FLOW8_VENDOR_ID, productId = E2eConfig.FLOW8_PRODUCT_ID)
class Flow8RecordingTimelineE2eTest {
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
    fun recordingUiTracksWallClock_andSoundcheckMatchesDisk() {
        runBlocking {
        val h = E2eMixerHarness(appRule).also { harness = it }
        val ctrl = h.bindAndRegisterFlow8()
        h.syncUiWithHarnessMixer(h.mixerId, AppMode.MULTITRACK_RECORD)
        runCatching { ctrl.syncVuMeterCapture() }
        val captureReady = E2eWait.pollUntil(timeoutMs = 60_000) {
            ctrl.debugRawMeterPeaksForTest().any { it > 0.0001f }
        }
        assertWithMessage("USB capture did not warm up before recording")
            .that(captureReady)
            .isTrue()

        val recordSeconds = 12
        val toleranceSec = E2eRecordingTimelineAssertions.RECORDING_TOLERANCE_SEC

        ctrl.startRecording()
        E2eWait.untilRecording(ctrl, timeoutMs = 60_000)
        E2eUiTransport.waitForContentDescription("Stop recording", timeoutMs = 10_000)

        val uiTimerStarted = E2eWait.pollUntil(timeoutMs = 20_000) {
            runCatching { E2eUiTransport.readRecordingElapsedSec(timeoutMs = 1_000) }
                .getOrNull()
                ?.let { it >= 1f } == true
        }
        assertWithMessage(
            "Recording UI timer stayed at 0:00 while controller elapsed=" +
                "${ctrl.state.value.recordElapsedSec}s",
        ).that(uiTimerStarted).isTrue()

        val recordingWallStartMs = System.currentTimeMillis()
        val pollDeadlineMs = recordingWallStartMs + recordSeconds * 1_000L
        var pollCount = 0
        while (System.currentTimeMillis() < pollDeadlineMs) {
            val wallSec = (System.currentTimeMillis() - recordingWallStartMs) / 1000f
            val ctrlElapsed = ctrl.state.value.recordElapsedSec
            val uiElapsed = E2eUiTransport.readRecordingElapsedSec(timeoutMs = 1_500)
            if (pollCount % 4 == 0) {
                Log.i(TAG, "poll wall=${"%.2f".format(wallSec)}s ui=$uiElapsed ctrl=$ctrlElapsed")
            }
            E2eRecordingTimelineAssertions.assertElapsedMatchesWallClock(
                uiElapsed,
                wallSec,
                toleranceSec,
                label = "UI recording",
            )
            E2eRecordingTimelineAssertions.assertElapsedMatchesWallClock(
                ctrlElapsed,
                wallSec,
                toleranceSec,
                label = "controller recording",
            )
            pollCount++
            delay(500)
        }
        assertThat(pollCount).isAtLeast(8)

        val totalWallSec = (System.currentTimeMillis() - recordingWallStartMs) / 1000f
        val uiElapsedAtStop = E2eUiTransport.readRecordingElapsedSec()
        val controllerElapsedAtStop = ctrl.state.value.recordElapsedSec
        Log.i(
            TAG,
            "stopping after wall=${totalWallSec}s ui=${uiElapsedAtStop}s ctrl=${controllerElapsedAtStop}s",
        )

        E2eUiTransport.clickContentDescription("Stop recording", timeoutMs = 30_000)
        E2eWait.untilNotRecording(ctrl, timeoutMs = 120_000)

        val sessionPath = ctrl.state.value.lastRecordingPath
        assertThat(sessionPath).isNotNull()
        val sessionDir = File(sessionPath!!)
        assertThat(sessionDir.isDirectory).isTrue()

        val metadata = SessionMetadata.read(sessionDir)
        assertThat(metadata).isNotNull()
        assertThat(metadata!!.incomplete).isFalse()

        val diskDurationSec = SessionPlaybackDuration.durationSec(sessionDir, metadata)
        E2eRecordingTimelineAssertions.assertDurationMatchesWallClock(
            uiElapsedAtStop,
            totalWallSec,
            toleranceSec,
            label = "UI at stop",
        )
        E2eRecordingTimelineAssertions.assertDurationMatchesWallClock(
            controllerElapsedAtStop,
            totalWallSec,
            toleranceSec,
            label = "controller at stop",
        )
        E2eRecordingTimelineAssertions.assertDurationMatchesWallClock(
            diskDurationSec,
            totalWallSec,
            toleranceSec,
            label = "disk",
        )

        h.prepareSoundcheck(ctrl, sessionDir)
        h.syncUiWithHarnessMixer(h.mixerId, AppMode.VIRTUAL_SOUNDCHECK)
        E2eWait.untilSoundcheckReady(ctrl, timeoutMs = 180_000)

        val controllerDuration = ctrl.state.value.playbackDurationSec
        E2eRecordingTimelineAssertions.assertUiSoundcheckDurationMatchesWallClock(totalWallSec, toleranceSec)
        E2eRecordingTimelineAssertions.assertDurationMatchesWallClock(
            controllerDuration,
            totalWallSec,
            toleranceSec,
            label = "controller soundcheck duration",
        )
        E2eRecordingTimelineAssertions.assertDurationsAgree(
            controllerDuration,
            diskDurationSec,
            toleranceSec,
            firstLabel = "controller soundcheck",
            secondLabel = "disk",
        )

        E2eUiTransport.clickContentDescription("Play", timeoutMs = 30_000)
        assertPlayStarted(ctrl)

        val playWallStartMs = System.currentTimeMillis()
        delay(5_000)
        val playWallSec = (System.currentTimeMillis() - playWallStartMs) / 1000f
        val (uiPlayPos, _) = E2eUiTransport.readSoundcheckTransport()
        val controllerPosition = ctrl.state.value.playbackPositionSec
        Log.i(TAG, "playback poll wall=${"%.2f".format(playWallSec)}s ui=$uiPlayPos ctrl=$controllerPosition")

        E2eRecordingTimelineAssertions.assertElapsedMatchesWallClock(
            uiPlayPos,
            playWallSec,
            E2eRecordingTimelineAssertions.PLAYBACK_TOLERANCE_SEC,
            label = "UI playback position",
        )
        E2eRecordingTimelineAssertions.assertElapsedMatchesWallClock(
            controllerPosition,
            playWallSec,
            E2eRecordingTimelineAssertions.PLAYBACK_TOLERANCE_SEC,
            label = "controller playback position",
        )

        E2eUiTransport.clickContentDescription("Stop playback", timeoutMs = 10_000)
        E2eWait.untilNotPlaying(ctrl, timeoutMs = 60_000)

        Log.i(
            TAG,
            "recording timeline e2e passed (wall=${totalWallSec}s disk=${diskDurationSec}s " +
                "playPos=${controllerPosition}s)",
        )
        }
    }

    private suspend fun assertPlayStarted(ctrl: MixerSessionController) {
        val ready = E2eWait.pollUntil(timeoutMs = 90_000) {
            val state = ctrl.state.value
            state.isPlaying || !state.warningMessage.isNullOrBlank()
        }
        assertThat(ready).isTrue()
        assertWithMessage("Play failed: ${ctrl.state.value.warningMessage}")
            .that(ctrl.state.value.warningMessage)
            .isNull()
        assertThat(ctrl.state.value.isPlaying).isTrue()
    }

    private companion object {
        const val TAG = "Flow8RecordTimelineE2e"
    }
}
