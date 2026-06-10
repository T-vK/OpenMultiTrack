package org.openmultitrack.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.domain.session.AppMode

/**
 * Host side of dual-device remote e2e: prepares mixer/session, starts remote host mode,
 * and waits for the client test to finish.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = E2eConfig.XR18_VENDOR_ID, productId = E2eConfig.XR18_PRODUCT_ID)
class RemoteE2eHostTest {
    @get:Rule(order = 0)
    val appRule = E2eAppRule(enableWaveformsAndVu = true)

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    private var mixerHarness: E2eMixerHarness? = null
    private var remoteHarness: E2eRemoteHarness? = null

    @After
    fun tearDown() {
        runCatching { remoteHarness?.shutdown() }
        runCatching { mixerHarness?.shutdown() }
        remoteHarness = null
        mixerHarness = null
    }

    @Test(timeout = 600_000)
    fun hostPreparesSessionAndServesRemoteClient() = runBlocking {
        val mixer = E2eMixerHarness(appRule).also { mixerHarness = it }
        val ctrl = mixer.bindAndRegisterXr18()
        val sessionDir = mixer.recordShortSession(ctrl, seconds = E2eConfig.ZOOM_RECORD_SECONDS)
        mixer.prepareSoundcheck(ctrl, sessionDir)
        mixer.recordShortSession(ctrl, seconds = E2eConfig.RECORD_SECONDS)
        mixer.prepareSoundcheck(ctrl, sessionDir)

        val remote = E2eRemoteHarness(appRule).also { remoteHarness = it }
        remote.bindSession()
        remote.ensureHostDisplayDefaults()
        val hostIp = remote.startHost()
        val readyPayload =
            "$HOST_READY_PAYLOAD|ip=$hostIp|mixerId=${mixer.mixerId}|sessionDir=${sessionDir.absolutePath}"
        val announceJob = launch {
            while (isActive && remote.state().value.connectedClientCount < 1) {
                E2eLanSync.signal(readyPayload)
                delay(2_000)
            }
        }

        remote.waitForRemoteClient()
        announceJob.cancel()
        assertThat(ctrl.state.value.appMode).isEqualTo(AppMode.VIRTUAL_SOUNDCHECK)

        // Keep serving through the full client e2e budget (ignore brief reconnect blips).
        val minServeMs = 300_000L
        val maxServeMs = 540_000L
        val serveStart = System.currentTimeMillis()
        while (System.currentTimeMillis() - serveStart < maxServeMs) {
            if (remote.state().value.connectedClientCount == 0 &&
                System.currentTimeMillis() - serveStart >= minServeMs
            ) {
                break
            }
            delay(1_000)
        }
        delay(1_000)
        assertThat(remoteHarness).isNotNull()
    }

    private companion object {
        const val HOST_READY_PAYLOAD = E2eLanSync.HOST_READY
    }
}
