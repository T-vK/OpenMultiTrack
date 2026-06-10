package org.openmultitrack.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Client (remote) side of dual-device e2e: connects to host, mirrors state, and exercises
 * recording, playback, seek, zoom, and unexpected disconnect recovery.
 */
@RunWith(AndroidJUnit4::class)
class RemoteE2eClientTest {
    @get:Rule
    val appRule = E2eAppRule(enableWaveformsAndVu = true)

    private var harness: E2eRemoteHarness? = null

    @After
    fun tearDown() {
        runCatching { harness?.shutdown() }
        harness = null
    }

    @Test
    fun remoteFullSyncTransportWaveformsAndReconnect() = runBlocking {
        val hostIp = E2eConfig.hostIp ?: error("host_ip instrumentation arg required")
        E2eWait.awaitHostRemoteReady(hostIp)
        val remote = E2eRemoteHarness(appRule).also { harness = it }
        remote.bindSession()
        remote.connectClient(hostIp)

        val mixerId = E2eWait.untilRemoteState(remote.state(), 120_000) { state ->
            state.sessionByMixer.isNotEmpty()
        }.let { state ->
            state.activeMixerId ?: state.sessionByMixer.keys.first()
        }
        val mirror = remote.state().value.sessionByMixer[mixerId]
        assertThat(mirror).isNotNull()
        assertThat(mirror!!.selectedSoundcheckDir).isNotNull()
        awaitSoundcheckReady(remote, mixerId)

        RemoteE2eAssertions.assertSettingsMirrored(remote.state())
        RemoteE2eAssertions.assertSoundcheckWaveformsOnRemote(remote, mixerId)
        RemoteE2eAssertions.assertPlaybackTransport(remote, mixerId, hostIp)
        RemoteE2eAssertions.assertSettingsChangeFromRemote(remote, mixerId, hostIp)

        val duration = remote.state().value.sessionByMixer[mixerId]!!.playbackDurationSec
        val seekTarget = (duration * 0.4f).coerceAtLeast(1f)
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote(
            "seek",
            JSONObject().put("mixerId", mixerId).put("positionSec", seekTarget.toDouble()),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            val pos = it.sessionByMixer[mixerId]?.playbackPositionSec ?: 0f
            abs(pos - seekTarget) <= 1.5f
        }

        val windowBefore = remote.state().value.sessionByMixer[mixerId]!!.soundcheckViewWindowSec
        remote.sendRemote(
            "zoom_soundcheck_view",
            JSONObject()
                .put("mixerId", mixerId)
                .put("scale", 2.0)
                .put("focalSec", seekTarget.toDouble()),
        )
        delay(500)
        val zoomedWindow = remote.state().value.sessionByMixer[mixerId]!!.soundcheckViewWindowSec
        assertThat(zoomedWindow).isLessThan(windowBefore)

        RemoteE2eAssertions.assertLiveWaveformsDuringRecording(remote, mixerId)

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
        assertThat(remote.state().value.connectionState.name).isEqualTo("CONNECTED")
        remote.disconnectClient()
        delay(2_000)
        remote.connectClient(hostIp)
        E2eWait.untilRemoteConnected(remote.state(), timeoutMs = 120_000)
        awaitSoundcheckReady(remote, mixerId)
    }
}
