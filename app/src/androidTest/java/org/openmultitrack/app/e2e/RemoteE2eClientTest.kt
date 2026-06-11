package org.openmultitrack.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.domain.remote.RemoteConnectionState

/**
 * Client (remote) side of dual-device e2e: connects to host, mirrors state, and exercises
 * strip controls, monitor mode, transport, view/loop/session/routing commands, settings sync,
 * live recording waveforms, and disconnect recovery.
 */
@RunWith(AndroidJUnit4::class)
class RemoteE2eClientTest {
    @get:Rule(order = 0)
    val appRule = E2eAppRule()

    @get:Rule(order = 1)
    val waveformDisplayRule = E2eWaveformDisplayRule { appRule.appContext }

    private var harness: E2eRemoteHarness? = null

    @After
    fun tearDown() {
        runCatching { harness?.shutdown() }
        harness = null
    }

    @Test(timeout = 600_000)
    fun remoteFullSyncTransportWaveformsAndReconnect() = runBlocking {
        val hostIp = E2eConfig.hostIp ?: error("host_ip instrumentation arg required")
        E2eWait.awaitHostRemoteReady(hostIp)
        val remote = E2eRemoteHarness(appRule).also { harness = it }
        remote.prepareClientBeforeBind()
        remote.bindSession()
        remote.connectClient(hostIp)

        val mixerId = E2eWait.untilRemoteState(remote.state(), 120_000) { state ->
            state.sessionByMixer.isNotEmpty()
        }.let { state ->
            state.activeMixerId ?: state.sessionByMixer.keys.first()
        }
        val mirror = remote.state().value.sessionByMixer[mixerId]
        assertThat(mirror).isNotNull()
        val primarySessionDir = mirror!!.selectedSoundcheckDir
        assertThat(primarySessionDir).isNotNull()
        awaitSoundcheckReady(remote, mixerId)

        RemoteE2eAssertions.assertSettingsMirrored(remote.state())
        RemoteE2eAssertions.assertRoutingMirrored(remote, mixerId)
        RemoteE2eAssertions.assertSoundcheckWaveformsOnRemote(remote, mixerId)
        RemoteE2eAssertions.assertPlaybackTransport(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertSoundcheckViewPanAndSet(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertSettingsChangeFromRemote(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertZoomSeekPlayAccuracy(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertLoopRegionFromRemote(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertSessionLibraryFromRemote(
            remote,
            mixerId,
            hostIp,
            primarySessionDir!!,
        )
        RemoteE2eAssertions.assertRoutingChangeFromRemote(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertSimplePlayFromRemote(remote, mixerId, hostIp, primarySessionDir)
        RemoteE2eAssertions.assertBroaderSettingsSync(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertStripControlsFromRemote(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertMonitorModeFromRemote(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertLiveWaveformsDuringRecording(remote, mixerId, hostIp)

        if (remote.state().value.connectionState != RemoteConnectionState.CONNECTED) {
            remote.reconnectClient(hostIp)
        }
        testUnexpectedDisconnectRecovery(remote, hostIp, mixerId)
        E2eLanSync.signal(E2eLanSync.CLIENT_DONE, targetHost = hostIp)
    }

    private suspend fun awaitSoundcheckReady(remote: E2eRemoteHarness, mixerId: String) {
        RemoteE2eAssertions.assertSoundcheckWaveformsOnRemote(remote, mixerId)
    }

    private suspend fun testUnexpectedDisconnectRecovery(
        remote: E2eRemoteHarness,
        hostIp: String,
        mixerId: String,
    ) {
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId, timeoutMs = 180_000)
        remote.disconnectClient()
        delay(2_000)
        remote.connectClient(hostIp)
        E2eWait.untilRemoteConnected(remote.state(), timeoutMs = 120_000)
        awaitSoundcheckReady(remote, mixerId)
    }
}
