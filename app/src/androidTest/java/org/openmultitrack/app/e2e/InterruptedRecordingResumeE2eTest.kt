package org.openmultitrack.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.scribble.IncompleteRecordingStore
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.sessionio.session.SessionMetadata

/**
 * Verifies auto-resume after the orchestrator script force-stopped the app mid-recording.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class InterruptedRecordingResumeE2eTest {
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
    fun recordingResumesAfterForceStop() = runBlocking {
        val settings = AppSettingsStore(appRule.appContext)
        val mixerId = settings.activeRecordingMixerId
        val sessionPath = settings.activeRecordingSessionDir
        assertThat(mixerId).isNotNull()
        assertThat(sessionPath).isNotNull()
        val sessionDir = java.io.File(sessionPath!!)
        assertThat(IncompleteRecordingStore.recoverableSession(appContext, settings, mixerId!!))
            .isEqualTo(sessionDir)

        val metaBeforeResume = SessionMetadata.read(sessionDir)
        assertThat(metaBeforeResume).isNotNull()
        assertThat(metaBeforeResume!!.incomplete).isTrue()
        val wavBytesBefore = sessionDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
            ?.sumOf { it.length() }
            ?: 0L

        val h = E2eMixerHarness(appRule).also { harness = it }
        val ctrl = h.bindAndRegisterXr18(preserveActiveRecording = true)
        assertThat(h.mixerId).isEqualTo(mixerId)

        val autoResumed = E2eWait.pollUntil(timeoutMs = 90_000) {
            ctrl.state.value.isRecording
        }
        if (!autoResumed) {
            assertThat(SessionMetadata.read(sessionDir)?.incomplete).isTrue()
            ctrl.resumeRecording(sessionDir)
            E2eWait.untilRecording(ctrl, timeoutMs = 60_000)
        }
        assertThat(ctrl.state.value.lastRecordingPath).isEqualTo(sessionDir.absolutePath)
        val elapsedWhenResumed = ctrl.state.value.recordElapsedSec
        E2eWait.untilMixerState(ctrl, 30_000) {
            it.isRecording && it.recordElapsedSec >= elapsedWhenResumed + 1f
        }
        val wavGrew = E2eWait.pollUntil(timeoutMs = 30_000) {
            val wavBytes = sessionDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) }
                ?.sumOf { it.length() }
                ?: 0L
            wavBytes > wavBytesBefore
        }
        assertThat(wavGrew).isTrue()

        ctrl.stopRecording()
        E2eWait.untilNotRecording(ctrl)
        assertThat(SessionMetadata.read(sessionDir)?.incomplete).isFalse()
    }

    private val appContext get() = appRule.appContext
}
