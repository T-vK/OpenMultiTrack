package org.openmultitrack.app.e2e

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.scribble.IncompleteRecordingStore
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.domain.session.AppMode

/**
 * Leaves an active incomplete recording on disk for [InterruptedRecordingResumeE2eTest].
 * The orchestrator script force-stops the app immediately after this test.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class InterruptedRecordingPrepE2eTest {
    @get:Rule(order = 0)
    val appRule = E2eAppRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    @Test
    fun leaveActiveRecordingForKill() = runBlocking {
        clearStaleInterruptedSessions()
        val harness = E2eMixerHarness(appRule)
        val ctrl = harness.bindAndRegisterXr18()
        ctrl.setAppMode(AppMode.MULTITRACK_RECORD)
        ctrl.startRecording()
        E2eWait.untilRecording(ctrl)
        val sessionDir = ctrl.state.value.lastRecordingPath
        assertThat(sessionDir).isNotNull()
        val framesAtStart = SessionMetadata.read(java.io.File(sessionDir!!))?.timelineFramesWritten ?: 0L
        delay(15_000)
        val framesAfterRecord = SessionMetadata.read(java.io.File(sessionDir))?.timelineFramesWritten ?: 0L
        assertThat(framesAfterRecord).isGreaterThan(framesAtStart)
        val settings = AppSettingsStore(appRule.appContext)
        assertThat(IncompleteRecordingStore.recoverableSession(appRule.appContext, settings, harness.mixerId))
            .isNotNull()

        Log.i(
            E2eConfig.TAG,
            "INTERRUPT_READY mixerId=${harness.mixerId} sessionDir=$sessionDir",
        )
        harness.clientUnbindOnly()
    }

    private fun clearStaleInterruptedSessions() {
        val settings = AppSettingsStore(appRule.appContext)
        settings.clearActiveRecording()
        MixerDeviceStore(appRule.appContext).listMixers().forEach { profile ->
            IncompleteRecordingStore.findIncompleteSessions(appRule.appContext, settings, profile.id)
                .forEach { dir ->
                    runCatching {
                        SessionMetadata.read(dir)?.markComplete(dir)
                    }
                }
        }
    }
}
