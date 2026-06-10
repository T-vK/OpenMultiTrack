package org.openmultitrack.app.e2e

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.openmultitrack.app.remote.RemoteControlUiState
import org.openmultitrack.app.remote.RemoteSnapshotMapper
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerRoutingConfig
import org.openmultitrack.domain.session.AppMode
import kotlin.math.abs

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

    suspend fun assertRoutingMirrored(
        remote: E2eRemoteHarness,
        mixerId: String,
        timeoutMs: Long = 30_000,
    ) {
        E2eWait.untilRemoteState(remote.state(), timeoutMs) { state ->
            state.mixerRoutingById.containsKey(mixerId)
        }
    }

    suspend fun assertStripControlsFromRemote(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
        channelIndex: Int = firstStripIndex(remote, mixerId),
    ) {
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)

        fun strip(): ChannelStripState? =
            remote.state().value.sessionByMixer[mixerId]
                ?.channelStrips
                ?.firstOrNull { it.index == channelIndex }

        suspend fun toggleAndAssert(
            command: String,
            read: (ChannelStripState) -> Boolean,
        ) {
            val before = strip()?.let(read) ?: error("Strip $channelIndex missing")
            remote.sendRemote(
                command,
                JSONObject().put("mixerId", mixerId).put("index", channelIndex),
            )
            E2eWait.untilRemoteState(remote.state(), 60_000) {
                strip()?.let(read) == !before
            }
            remote.sendRemote(
                command,
                JSONObject().put("mixerId", mixerId).put("index", channelIndex),
            )
            E2eWait.untilRemoteState(remote.state(), 60_000) {
                strip()?.let(read) == before
            }
        }

        toggleAndAssert("toggle_arm") { it.armed }
        toggleAndAssert("toggle_monitor") { it.monitoring }
        toggleAndAssert("toggle_mute") { it.muted }

        val soloBefore = strip()?.solo == true
        remote.sendRemote(
            "toggle_solo",
            JSONObject().put("mixerId", mixerId).put("index", channelIndex),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            strip()?.solo == !soloBefore
        }
        val strips = remote.state().value.sessionByMixer[mixerId]!!.channelStrips
        if (strips.size > 1) {
            val otherSolo = strips.filter { it.index != channelIndex }.any { it.solo }
            assertThat(otherSolo).isFalse()
        }
        remote.sendRemote(
            "toggle_solo",
            JSONObject().put("mixerId", mixerId).put("index", channelIndex),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            strip()?.solo == soloBefore
        }
    }

    suspend fun assertMonitorModeFromRemote(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
    ) {
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote("stop_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            it.sessionByMixer[mixerId]?.isPlaying != true
        }

        remote.sendRemote("start_monitor", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.isMonitoring == true
        }
        E2eWait.untilRemoteState(remote.state(), 60_000) { state ->
            val levels = state.sessionByMixer[mixerId]?.captureMeterLevels.orEmpty()
            levels.values.any { it > 0.005f }
        }

        remote.sendRemote("stop_monitor", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            it.sessionByMixer[mixerId]?.isMonitoring != true
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

        val recordedDir = E2eWait.untilRemoteState(remote.state(), 60_000) { state ->
            state.sessionByMixer[mixerId]?.lastRecordingPath?.isNotBlank() == true
        }.sessionByMixer[mixerId]!!.lastRecordingPath!!
        remote.sendRemote(
            "load_into_soundcheck",
            JSONObject().put("mixerId", mixerId).put("sessionDir", recordedDir),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.appMode == AppMode.VIRTUAL_SOUNDCHECK
        }
        assertSoundcheckWaveformsOnRemote(remote, mixerId)
    }

    suspend fun assertPlaybackTransport(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
    ) {
        ensureVirtualSoundcheck(remote, mixerId, hostIp)

        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote("play_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.isPlaying == true
        }
        E2eWait.untilRemotePlaybackAdvances(remote, mixerId, minAdvanceSec = 1.0f, observeMs = 3_500)

        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote("pause_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 90_000) {
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

    suspend fun assertSoundcheckViewPanAndSet(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
    ) {
        ensureVirtualSoundcheck(remote, mixerId, hostIp)
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)

        val startBefore = remote.state().value.sessionByMixer[mixerId]!!.soundcheckViewStartSec
        remote.sendRemote(
            "pan_soundcheck_view",
            JSONObject().put("mixerId", mixerId).put("deltaSec", 2.0),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            val start = it.sessionByMixer[mixerId]?.soundcheckViewStartSec ?: return@untilRemoteState false
            start > startBefore + 0.5f
        }

        remote.sendRemote(
            "set_soundcheck_view",
            JSONObject()
                .put("mixerId", mixerId)
                .put("viewStartSec", 5.0)
                .put("viewWindowSec", 45.0),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) { state ->
            val session = state.sessionByMixer[mixerId] ?: return@untilRemoteState false
            abs(session.soundcheckViewStartSec - 5f) <= 1f &&
                abs(session.soundcheckViewWindowSec - 45f) <= 2f
        }
    }

    suspend fun assertZoomSeekPlayAccuracy(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
    ) {
        ensureVirtualSoundcheck(remote, mixerId, hostIp)
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)

        val duration = remote.state().value.sessionByMixer[mixerId]!!.playbackDurationSec
        assertThat(duration).isGreaterThan(30f)
        val seekTarget = duration * 0.35f
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

        remote.sendRemote(
            "seek",
            JSONObject().put("mixerId", mixerId).put("positionSec", seekTarget.toDouble()),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            val pos = it.sessionByMixer[mixerId]?.playbackPositionSec ?: 0f
            abs(pos - seekTarget) <= 2.5f
        }

        remote.sendRemote("play_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.isPlaying == true
        }
        E2eWait.untilRemoteState(remote.state(), 30_000) { state ->
            val pos = state.sessionByMixer[mixerId]?.playbackPositionSec ?: 0f
            pos >= seekTarget - 1f
        }
        assertThat(
            abs(remote.state().value.sessionByMixer[mixerId]!!.playbackPositionSec - seekTarget),
        ).isAtMost(3f)

        remote.sendRemote("stop_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            it.sessionByMixer[mixerId]?.isPlaying != true
        }
    }

    suspend fun assertLoopRegionFromRemote(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
    ) {
        ensureVirtualSoundcheck(remote, mixerId, hostIp)
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)

        val duration = remote.state().value.sessionByMixer[mixerId]!!.playbackDurationSec
        val loopStart = (duration * 0.2f).coerceAtLeast(1f)
        val loopEnd = (duration * 0.35f).coerceAtMost(duration - 0.5f)
        assertThat(loopEnd).isGreaterThan(loopStart + 0.2f)

        remote.sendRemote(
            "set_loop",
            JSONObject()
                .put("mixerId", mixerId)
                .put("action", "region")
                .put("startSec", loopStart.toDouble())
                .put("endSec", loopEnd.toDouble()),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) { state ->
            val session = state.sessionByMixer[mixerId] ?: return@untilRemoteState false
            session.soundcheckLoopStartSec != null &&
                session.soundcheckLoopEndSec != null &&
                abs(session.soundcheckLoopStartSec!! - loopStart) <= 0.5f &&
                abs(session.soundcheckLoopEndSec!! - loopEnd) <= 0.5f
        }

        remote.sendRemote(
            "set_loop",
            JSONObject().put("mixerId", mixerId).put("action", "toggle"),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            it.sessionByMixer[mixerId]?.soundcheckLoopEnabled == true
        }
        remote.sendRemote(
            "set_loop",
            JSONObject().put("mixerId", mixerId).put("action", "toggle"),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) {
            it.sessionByMixer[mixerId]?.soundcheckLoopEnabled != true
        }
    }

    suspend fun assertSessionLibraryFromRemote(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
        primarySessionDir: String,
    ) {
        ensureVirtualSoundcheck(remote, mixerId, hostIp)
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)

        E2eWait.untilRemoteState(remote.state(), 60_000) { state ->
            state.sessionByMixer[mixerId]?.soundcheckSessions.orEmpty().size >= 2
        }
        val sessions = remote.state().value.sessionByMixer[mixerId]!!.soundcheckSessions
        assertThat(sessions.map { it.sessionDir }).contains(primarySessionDir)
        val alternate = sessions.first { it.sessionDir != primarySessionDir }

        remote.sendRemote(
            "rename_soundcheck_session",
            JSONObject()
                .put("mixerId", mixerId)
                .put("sessionDir", primarySessionDir)
                .put("title", "E2E Remote Title"),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) { state ->
            state.sessionByMixer[mixerId]?.soundcheckSessions
                ?.any { it.sessionDir == primarySessionDir && it.title == "E2E Remote Title" } == true
        }

        remote.sendRemote(
            "select_soundcheck",
            JSONObject().put("mixerId", mixerId).put("sessionDir", alternate.sessionDir),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.selectedSoundcheckDir == alternate.sessionDir
        }
        assertSoundcheckWaveformsOnRemote(remote, mixerId)

        remote.sendRemote(
            "select_soundcheck",
            JSONObject().put("mixerId", mixerId).put("sessionDir", primarySessionDir),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.selectedSoundcheckDir == primarySessionDir
        }
        assertSoundcheckWaveformsOnRemote(remote, mixerId)

        remote.sendRemote(
            "delete_soundcheck_session",
            JSONObject().put("mixerId", mixerId).put("sessionDir", alternate.sessionDir),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) { state ->
            state.sessionByMixer[mixerId]?.soundcheckSessions
                ?.none { it.sessionDir == alternate.sessionDir } == true
        }
    }

    suspend fun assertRoutingChangeFromRemote(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
        channelIndex: Int = firstStripIndex(remote, mixerId),
    ) {
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        val before = remote.state().value.mixerRoutingById[mixerId] ?: MixerRoutingConfig()
        val hidden = if (channelIndex in before.hiddenSoundcheck) {
            before.hiddenSoundcheck - channelIndex
        } else {
            before.hiddenSoundcheck + channelIndex
        }
        val updated = before.copy(hiddenSoundcheck = hidden)
        remote.sendRemote(
            "set_mixer_routing",
            JSONObject()
                .put("mixerId", mixerId)
                .put("routing", RemoteSnapshotMapper.routingToPayload(updated)),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) { state ->
            state.mixerRoutingById[mixerId]?.hiddenSoundcheck == hidden
        }
        remote.sendRemote(
            "set_mixer_routing",
            JSONObject()
                .put("mixerId", mixerId)
                .put("routing", RemoteSnapshotMapper.routingToPayload(before)),
        )
        E2eWait.untilRemoteState(remote.state(), 30_000) { state ->
            state.mixerRoutingById[mixerId]?.hiddenSoundcheck == before.hiddenSoundcheck
        }
    }

    suspend fun assertSimplePlayFromRemote(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
        sessionDir: String,
    ) {
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote(
            "load_into_simple_play",
            JSONObject().put("mixerId", mixerId).put("sessionDir", sessionDir),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.appMode == AppMode.SIMPLE_PLAY
        }
        remote.sendRemote("play_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.isPlaying == true
        }
        E2eWait.untilRemotePlaybackAdvances(remote, mixerId, minAdvanceSec = 1.0f, observeMs = 3_000)
        assertThat(remote.state().value.sessionByMixer[mixerId]?.appMode).isEqualTo(AppMode.SIMPLE_PLAY)

        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        remote.sendRemote("pause_playback", JSONObject().put("mixerId", mixerId))
        E2eWait.untilRemoteState(remote.state(), 90_000) {
            it.sessionByMixer[mixerId]?.isPlaying != true
        }
        remote.sendRemote(
            "load_into_soundcheck",
            JSONObject().put("mixerId", mixerId).put("sessionDir", sessionDir),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) {
            it.sessionByMixer[mixerId]?.appMode == AppMode.VIRTUAL_SOUNDCHECK
        }
        assertSoundcheckWaveformsOnRemote(remote, mixerId)
    }

    suspend fun assertBroaderSettingsSync(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
    ) {
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
        val beforeRecordWindow = remote.state().value.uiSettings?.recordWaveformWindowSec ?: 15f
        val targetWindow = if (beforeRecordWindow >= 20f) 15f else 20f
        remote.sendRemote(
            "set_settings",
            JSONObject()
                .put("showWaveforms", false)
                .put("recordWaveformWindowSec", targetWindow.toDouble()),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) { state ->
            val ui = state.uiSettings ?: return@untilRemoteState false
            !ui.showWaveforms && abs(ui.recordWaveformWindowSec - targetWindow) < 0.5f
        }
        remote.sendRemote(
            "set_settings",
            JSONObject()
                .put("showWaveforms", true)
                .put("recordWaveformWindowSec", beforeRecordWindow.toDouble()),
        )
        E2eWait.untilRemoteState(remote.state(), 60_000) { state ->
            val ui = state.uiSettings ?: return@untilRemoteState false
            ui.showWaveforms && abs(ui.recordWaveformWindowSec - beforeRecordWindow) < 0.5f
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
            abs(gain - target) < 0.05f
        }
        remote.sendRemote(
            "set_settings",
            JSONObject().put("monitorGainLinear", before.toDouble()),
        )
    }

    private suspend fun ensureVirtualSoundcheck(
        remote: E2eRemoteHarness,
        mixerId: String,
        hostIp: String,
    ) {
        val session = remote.state().value.sessionByMixer[mixerId]
        if (session?.isMonitoring == true) {
            E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
            remote.sendRemote("stop_monitor", JSONObject().put("mixerId", mixerId))
            E2eWait.untilRemoteState(remote.state(), 60_000) {
                it.sessionByMixer[mixerId]?.isMonitoring != true
            }
        }
        val currentMode = session?.appMode
        if (currentMode != AppMode.VIRTUAL_SOUNDCHECK) {
            val sessionDir = remote.state().value.sessionByMixer[mixerId]?.selectedSoundcheckDir
            if (sessionDir != null) {
                remote.sendRemote(
                    "load_into_soundcheck",
                    JSONObject().put("mixerId", mixerId).put("sessionDir", sessionDir),
                )
            } else {
                remote.sendRemote(
                    "set_app_mode",
                    JSONObject().put("mixerId", mixerId).put("mode", AppMode.VIRTUAL_SOUNDCHECK.ordinal),
                )
            }
            E2eWait.untilRemoteState(remote.state(), 60_000) {
                it.sessionByMixer[mixerId]?.appMode == AppMode.VIRTUAL_SOUNDCHECK
            }
            assertSoundcheckWaveformsOnRemote(remote, mixerId)
        }
        E2eWait.awaitRemoteReady(remote, hostIp, mixerId)
    }

    private fun firstStripIndex(remote: E2eRemoteHarness, mixerId: String): Int {
        val strips = remote.state().value.sessionByMixer[mixerId]?.channelStrips.orEmpty()
        assertThat(strips).isNotEmpty()
        return strips.first().index
    }
}
