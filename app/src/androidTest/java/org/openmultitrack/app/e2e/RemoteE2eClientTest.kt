package org.openmultitrack.app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.domain.session.AppMode
import kotlin.math.abs

/**
 * Client (remote) side of dual-device e2e: connects to host, mirrors state, and exercises
 * recording, playback, seek, zoom, and unexpected disconnect recovery.
 */
@RunWith(AndroidJUnit4::class)
class RemoteE2eClientTest {
    @get:Rule
    val appRule = E2eAppRule()

    private var harness: E2eRemoteHarness? = null
    private lateinit var hostIp: String
    private lateinit var mixerId: String
    private lateinit var sessionDir: String

    @Before
    fun resolveHostFromSync() {
        runBlocking {
            val sync = E2eLanSync.await(E2eLanSync.HOST_READY, timeoutMs = 120_000)
            hostIp = E2eConfig.hostIp
                ?: Regex("""ip=([^|]+)""").find(sync)?.groupValues?.get(1)
                ?: error("Could not resolve host IP")
            mixerId = Regex("""mixerId=([^|]+)""").find(sync)?.groupValues?.get(1)
                ?: error("mixerId missing from HOST_READY sync")
            sessionDir = Regex("""sessionDir=(.+)""").find(sync)?.groupValues?.get(1)?.trim()
                ?: error("sessionDir missing from HOST_READY sync")
        }
    }

    @After
    fun tearDown() {
        runCatching { harness?.shutdown() }
        harness = null
    }

    @Test
    fun remoteRecordingPlaybackSeekZoomAndReconnect() = runBlocking {
        val remote = E2eRemoteHarness(appRule).also { harness = it }
        remote.bindSession()
        remote.connectClient(hostIp)

        val mirror = remote.state().value.sessionByMixer[mixerId]
        assertThat(mirror).isNotNull()
        assertThat(mirror!!.selectedSoundcheckDir).isEqualTo(sessionDir)
        assertThat(mirror.playbackDurationSec).isGreaterThan(1f)

        remote.sendRemote(
            "set_app_mode",
            JSONObject().put("mixerId", mixerId).put("mode", AppMode.MULTITRACK_RECORD.ordinal),
        )
        delay(500)
        remote.sendRemote("start_record", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 45_000) {
            it.sessionByMixer[mixerId]?.isRecording == true
        }
        delay(2_000)
        remote.sendRemote("stop_record", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 45_000) {
            it.sessionByMixer[mixerId]?.isRecording != true
        }

        remote.sendRemote(
            "load_into_soundcheck",
            JSONObject().put("mixerId", mixerId).put("sessionDir", sessionDir),
        )
        delay(2_000)

        remote.sendRemote(
            "toggle_playback",
            JSONObject().put("mixerId", mixerId),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            it.sessionByMixer[mixerId]?.isPlaying == true
        }
        delay(1_000)

        val duration = remote.state().value.sessionByMixer[mixerId]!!.playbackDurationSec
        val seekTarget = (duration * 0.4f).coerceAtLeast(1f)
        remote.sendRemote(
            "seek",
            JSONObject().put("mixerId", mixerId).put("positionSec", seekTarget.toDouble()),
        )
        delay(500)
        E2eWait.untilRemoteState(remote.state(), 15_000) {
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

        val seekAfterZoom = (seekTarget + 0.5f).coerceAtMost(duration - 0.25f)
        remote.sendRemote(
            "seek",
            JSONObject().put("mixerId", mixerId).put("positionSec", seekAfterZoom.toDouble()),
        )
        delay(500)
        E2eWait.untilRemoteState(remote.state(), 15_000) {
            val pos = it.sessionByMixer[mixerId]?.playbackPositionSec ?: 0f
            abs(pos - seekAfterZoom) <= 1.5f
        }

        remote.sendRemote(
            "set_app_mode",
            JSONObject().put("mixerId", mixerId).put("mode", AppMode.SIMPLE_PLAY.ordinal),
        )
        delay(800)
        assertThat(remote.state().value.sessionByMixer[mixerId]?.appMode).isEqualTo(AppMode.SIMPLE_PLAY)

        remote.sendRemote("stop_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 20_000) {
            it.sessionByMixer[mixerId]?.isPlaying != true
        }

        testUnexpectedDisconnectRecovery(remote)
        E2eLanSync.signal(E2eLanSync.CLIENT_DONE, targetHost = hostIp)
    }

    private suspend fun testUnexpectedDisconnectRecovery(remote: E2eRemoteHarness) {
        assertThat(remote.state().value.connectionState.name).isEqualTo("CONNECTED")
        remote.disconnectClient()
        delay(2_000)
        remote.connectClient(hostIp)
        E2eWait.untilRemoteConnected(remote.state())
        val remirror = remote.state().value.sessionByMixer[mixerId]
        assertThat(remirror).isNotNull()
        assertThat(remirror!!.playbackDurationSec).isGreaterThan(1f)
    }
}
