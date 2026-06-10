package org.openmultitrack.app.e2e

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.openmultitrack.app.remote.RemoteControlUiState
import org.openmultitrack.domain.session.AppMode

/** Shared assertions for dual-device remote control e2e. */
object RemoteE2eAssertions {
    suspend fun assertSettingsMirrored(
        state: StateFlow<RemoteControlUiState>,
        timeoutMs: Long = 30_000,
    ) {
        val ui = E2eWait.untilRemoteState(state, timeoutMs) { remote ->
            remote.uiSettings != null
        }.uiSettings!!
        assertThat(ui.showWaveforms).isTrue()
        assertThat(ui.showVuMeters).isTrue()
        assertThat(ui.recordWaveformWindowSec).isGreaterThan(0f)
        assertThat(ui.playbackWaveformWindowSec).isGreaterThan(0f)
    }

    suspend fun assertSoundcheckWaveformsOnRemote(
        remote: E2eRemoteHarness,
        mixerId: String,
        timeoutMs: Long = 120_000,
    ) {
        E2eWait.untilRemoteState(remote.state(), timeoutMs) { state ->
            val session = state.sessionByMixer[mixerId] ?: return@untilRemoteState false
            !session.soundcheckWaveformsLoading &&
                session.soundcheckWaveforms != null &&
                session.playbackDurationSec > 1f &&
                session.soundcheckWaveforms!!.peaksByChannel.values.any { it.isNotEmpty() }
        }
    }

    suspend fun assertLiveWaveformsDuringRecording(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
        recordSeconds: Int = 3,
    ) {
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote(
            "set_app_mode",
            JSONObject().put("mixerId", mixerId).put("mode", AppMode.MULTITRACK_RECORD.ordinal),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            it.sessionByMixer[mixerId]?.appMode == AppMode.MULTITRACK_RECORD
        }
        delay(1_000)
        remote.sendRemote("start_record", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 90_000) {
            it.sessionByMixer[mixerId]?.isRecording == true
        }
        E2eWait.untilRemoteState(remote.state(), 90_000) { state ->
            val session = state.sessionByMixer[mixerId] ?: return@untilRemoteState false
            session.recordElapsedSec > 0.5f &&
                session.waveformPeaks.values.any { snap -> snap.peaks.any { it > 0.01f } }
        }
        delay(recordSeconds * 1_000L)
        remote.sendRemote("stop_record", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.isRecording != true
        }
    }

    suspend fun assertPlaybackTransport(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
    ) {
        val currentMode = remote.state().value.sessionByMixer[mixerId]?.appMode
        if (currentMode != AppMode.VIRTUAL_SOUNDCHECK) {
            remote.sendRemote(
                "set_app_mode",
                JSONObject().put("mixerId", mixerId).put("mode", AppMode.VIRTUAL_SOUNDCHECK.ordinal),
            )
            E2eWait.untilRemoteState(remote.state(), 60_000) {
                it.sessionByMixer[mixerId]?.appMode == AppMode.VIRTUAL_SOUNDCHECK
            }
            assertSoundcheckWaveformsOnRemote(remote, mixerId)
        }

        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote("play_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.isPlaying == true
        }
        val posWhilePlaying = E2eWait.untilRemoteState(remote.state(), 30_000) { state ->
            val session = state.sessionByMixer[mixerId] ?: return@untilRemoteState false
            session.playbackPositionSec > 0.05f
        }.sessionByMixer[mixerId]!!.playbackPositionSec
        assertThat(posWhilePlaying).isGreaterThan(0.05f)

        remote.sendRemote("pause_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            it.sessionByMixer[mixerId]?.isPlaying != true
        }

        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote("toggle_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 90_000) {
            it.sessionByMixer[mixerId]?.isPlaying == true
        }

        remote.sendRemote("stop_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            it.sessionByMixer[mixerId]?.isPlaying != true
        }
    }

    suspend fun assertSettingsChangeFromRemote(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
    ) {
        val before = remote.state().value.uiSettings?.monitorGainLinear ?: 2.5f
        val target = (before + 0.5f).coerceAtMost(4f)
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote(
            "set_settings",
            JSONObject().put("monitorGainLinear", target.toDouble()),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) { state ->
            val gain = state.uiSettings?.monitorGainLinear ?: return@untilRemoteState false
            kotlin.math.abs(gain - target) < 0.05f
        }
        remote.sendRemote(
            "set_settings",
            JSONObject().put("monitorGainLinear", before.toDouble()),
        )
    }
}
