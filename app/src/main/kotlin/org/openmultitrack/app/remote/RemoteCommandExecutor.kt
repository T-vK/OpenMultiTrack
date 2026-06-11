package org.openmultitrack.app.remote

import org.json.JSONArray
import org.json.JSONObject
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerRoutingStore
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.app.service.MultiMixerSessionManager
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode
import org.openmultitrack.remote.RemoteWaveformUtil
import org.openmultitrack.sessionio.wav.SessionWaveformOverview

class RemoteCommandExecutor(
    private val manager: MultiMixerSessionManager,
    private val settings: AppSettingsStore,
    private val routingStore: MixerRoutingStore,
    private val promoteForeground: (String) -> Boolean,
) {
    fun execute(command: String, payload: JSONObject): Result<WaveformChunkReply?> = runCatching {
        when (command) {
            "set_active_mixer" -> {
                manager.setActiveMixer(payload.getString("mixerId"))
                null
            }
            "set_app_mode" -> {
                val mixerId = payload.getString("mixerId")
                val mode = AppMode.entries[payload.getInt("mode")]
                val ctrl = manager.getOrCreate(mixerId)
                if (ctrl.state.value.appMode == mode) return@runCatching null
                settings.setAppModeForMixer(mixerId, mode)
                ctrl.setAppMode(mode)
                null
            }
            "toggle_arm" -> {
                val mixerId = payload.getString("mixerId")
                val index = payload.getInt("index")
                manager.getOrCreate(mixerId).updateChannelStrip(index) { it.copy(armed = !it.armed) }
                null
            }
            "toggle_monitor" -> {
                val mixerId = payload.getString("mixerId")
                val index = payload.getInt("index")
                manager.getOrCreate(mixerId).updateChannelStrip(index) {
                    it.copy(monitoring = !it.monitoring)
                }
                null
            }
            "toggle_solo" -> {
                val mixerId = payload.getString("mixerId")
                val index = payload.getInt("index")
                val ctrl = manager.getOrCreate(mixerId)
                val wasSolo = ctrl.state.value.channelStrips.firstOrNull { it.index == index }?.solo == true
                ctrl.state.value.channelStrips.forEach { strip ->
                    ctrl.updateChannelStrip(strip.index) {
                        it.copy(solo = if (strip.index == index) !wasSolo else false)
                    }
                }
                null
            }
            "toggle_mute" -> {
                val mixerId = payload.getString("mixerId")
                val index = payload.getInt("index")
                manager.getOrCreate(mixerId).updateChannelStrip(index) { it.copy(muted = !it.muted) }
                null
            }
            "start_record" -> {
                val mixerId = payload.getString("mixerId")
                val ctrl = manager.getOrCreate(mixerId)
                check(ctrl.canStartRecording()) {
                    "Mixer not ready on host — check USB connection"
                }
                check(promoteForeground("Recording")) { "Could not start foreground service" }
                ctrl.startRecording()
                null
            }
            "stop_record" -> {
                manager.getOrCreate(payload.getString("mixerId")).stopRecording()
                null
            }
            "start_monitor" -> {
                val mixerId = payload.getString("mixerId")
                check(promoteForeground("Monitor")) { "Could not start foreground service" }
                manager.getOrCreate(mixerId).startMonitoring()
                null
            }
            "stop_monitor" -> {
                manager.getOrCreate(payload.getString("mixerId")).stopMonitoring()
                null
            }
            "toggle_playback" -> {
                manager.getOrCreate(payload.getString("mixerId")).toggleSoundcheckPlayback()
                null
            }
            "play_playback" -> {
                manager.getOrCreate(payload.getString("mixerId")).playSoundcheckPlayback()
                null
            }
            "pause_playback" -> {
                manager.getOrCreate(payload.getString("mixerId")).pauseSoundcheckPlayback()
                null
            }
            "stop_playback" -> {
                manager.getOrCreate(payload.getString("mixerId")).stopSoundcheck()
                null
            }
            "load_into_soundcheck" -> {
                val mixerId = payload.getString("mixerId")
                val sessionDir = payload.getString("sessionDir")
                val ctrl = manager.getOrCreate(mixerId)
                settings.setAppModeForMixer(mixerId, AppMode.VIRTUAL_SOUNDCHECK)
                ctrl.setAppMode(AppMode.VIRTUAL_SOUNDCHECK)
                settings.setLastSelectedSoundcheckSession(mixerId, sessionDir)
                ctrl.selectSoundcheckSession(sessionDir)
                null
            }
            "load_into_simple_play" -> {
                val mixerId = payload.getString("mixerId")
                val sessionDir = payload.getString("sessionDir")
                val ctrl = manager.getOrCreate(mixerId)
                settings.setAppModeForMixer(mixerId, AppMode.SIMPLE_PLAY)
                ctrl.setAppMode(AppMode.SIMPLE_PLAY)
                settings.setLastSelectedSoundcheckSession(mixerId, sessionDir)
                ctrl.selectSoundcheckSession(sessionDir)
                null
            }
            "rename_soundcheck_session" -> {
                manager.getOrCreate(payload.getString("mixerId"))
                    .renameSoundcheckSession(
                        payload.getString("sessionDir"),
                        payload.getString("title"),
                    )
                null
            }
            "delete_soundcheck_session" -> {
                manager.getOrCreate(payload.getString("mixerId"))
                    .deleteSoundcheckSession(payload.getString("sessionDir"))
                null
            }
            "seek" -> {
                val mixerId = payload.getString("mixerId")
                val positionSec = payload.getDouble("positionSec").toFloat()
                manager.getOrCreate(mixerId).seekSoundcheck(positionSec)
                null
            }
            "select_soundcheck" -> {
                val mixerId = payload.getString("mixerId")
                val sessionDir = payload.getString("sessionDir")
                settings.setLastSelectedSoundcheckSession(mixerId, sessionDir)
                manager.getOrCreate(mixerId).selectSoundcheckSession(sessionDir)
                null
            }
            "set_soundcheck_view" -> {
                val mixerId = payload.getString("mixerId")
                manager.getOrCreate(mixerId).setSoundcheckView(
                    payload.getDouble("viewStartSec").toFloat(),
                    payload.getDouble("viewWindowSec").toFloat(),
                )
                null
            }
            "pan_soundcheck_view" -> {
                val mixerId = payload.getString("mixerId")
                manager.getOrCreate(mixerId).panSoundcheckView(payload.getDouble("deltaSec").toFloat())
                null
            }
            "zoom_soundcheck_view" -> {
                val mixerId = payload.getString("mixerId")
                manager.getOrCreate(mixerId).zoomSoundcheckView(
                    payload.getDouble("scale").toFloat(),
                    payload.getDouble("focalSec").toFloat(),
                )
                null
            }
            "set_loop" -> {
                val mixerId = payload.getString("mixerId")
                val ctrl = manager.getOrCreate(mixerId)
                when (payload.optString("action")) {
                    "toggle" -> ctrl.toggleSoundcheckLoopButton()
                    "in" -> ctrl.setSoundcheckLoopIn()
                    "out" -> ctrl.setSoundcheckLoopOut()
                    "region" -> ctrl.setSoundcheckLoopRegion(
                        payload.getDouble("startSec").toFloat(),
                        payload.getDouble("endSec").toFloat(),
                    )
                }
                null
            }
            "set_settings" -> {
                applySettings(payload)
                null
            }
            "set_mixer_routing" -> {
                val mixerId = payload.getString("mixerId")
                val config = RemoteSnapshotMapper.routingFromPayload(payload.getJSONObject("routing"))
                routingStore.save(mixerId, config)
                manager.getOrCreate(mixerId).setRouting(config)
                null
            }
            "waveform_request" -> buildWaveformChunk(payload)
            else -> error("Unknown command: $command")
        }
    }

    private fun applySettings(payload: JSONObject) {
        if (payload.has("hideArmButton")) settings.hideArmButton = payload.getBoolean("hideArmButton")
        if (payload.has("hideMonitorButton")) settings.hideMonitorButton = payload.getBoolean("hideMonitorButton")
        if (payload.has("hideSoloButton")) settings.hideSoloButton = payload.getBoolean("hideSoloButton")
        if (payload.has("showWaveforms")) settings.showWaveforms = payload.getBoolean("showWaveforms")
        if (payload.has("showVuMeters")) settings.showVuMeters = payload.getBoolean("showVuMeters")
        if (payload.has("recordWaveformHistorySec")) {
            settings.recordWaveformHistorySec = payload.getDouble("recordWaveformHistorySec").toFloat()
            manager.mixerIds().forEach { manager.getOrCreate(it).updateWaveformConfig() }
        }
        if (payload.has("recordWaveformWindowSec")) {
            settings.recordWaveformWindowSec = payload.getDouble("recordWaveformWindowSec").toFloat()
            manager.mixerIds().forEach { manager.getOrCreate(it).updateWaveformConfig() }
        }
        if (payload.has("playbackWaveformWindowSec")) {
            settings.playbackWaveformWindowSec = payload.getDouble("playbackWaveformWindowSec").toFloat()
            manager.mixerIds().forEach { manager.getOrCreate(it).updateSoundcheckViewConfig() }
        }
        if (payload.has("stripNumberMode")) {
            settings.stripNumberMode = StripNumberMode.entries[payload.getInt("stripNumberMode")]
        }
        if (payload.has("stripIconMode")) {
            settings.stripIconMode = StripIconMode.entries[payload.getInt("stripIconMode")]
        }
        if (payload.has("monitorGainLinear")) {
            val gain = payload.getDouble("monitorGainLinear").toFloat()
            settings.monitorGainLinear = gain
            manager.mixerIds().forEach { manager.getOrCreate(it).setMonitorGain(gain) }
        }
    }

    private fun buildWaveformChunk(payload: JSONObject): WaveformChunkReply {
        val mixerId = payload.getString("mixerId")
        val sessionDir = payload.getString("sessionDir")
        val channel = payload.getInt("channel")
        val startSec = payload.getDouble("startSec").toFloat()
        val windowSec = payload.getDouble("windowSec").toFloat()
        val maxPoints = payload.optInt("maxPoints", 400)
        val session = manager.getOrCreate(mixerId).state.value
        check(session.selectedSoundcheckDir == sessionDir) { "Soundcheck session not selected" }
        val overview: SessionWaveformOverview = session.soundcheckWaveforms
            ?: error("Waveforms not loaded")
        val peaks = overview.peaksByChannel[channel]
            ?: error("Channel $channel not available")
        val sliced = RemoteWaveformUtil.slicePeaks(
            peaks = peaks,
            peaksPerSec = overview.peaksPerSec,
            startSec = startSec,
            windowSec = windowSec,
            maxPoints = maxPoints,
        )
        return WaveformChunkReply(mixerId, sessionDir, channel, startSec, sliced)
    }
}

data class WaveformChunkReply(
    val mixerId: String,
    val sessionDir: String,
    val channel: Int,
    val startSec: Float,
    val peaks: FloatArray,
)
