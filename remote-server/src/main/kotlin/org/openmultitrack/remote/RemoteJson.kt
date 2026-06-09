package org.openmultitrack.remote

import org.json.JSONArray
import org.json.JSONObject
import org.openmultitrack.domain.remote.RemoteProtocol
import java.util.Base64

internal object RemoteJson {
    fun encodeSnapshot(snapshot: RemoteMirrorSnapshot): String =
        JSONObject().apply {
            put("type", "snapshot")
            put("protocolVersion", snapshot.protocolVersion)
            put("hostName", snapshot.hostName)
            put("activeMixerId", snapshot.activeMixerId)
            put("settings", encodeSettings(snapshot.settings))
            put("mixers", JSONArray().apply { snapshot.mixers.forEach { put(encodeMixerProfile(it)) } })
            put("sessions", encodeSessions(snapshot.sessions))
        }.toString()

    fun decodeSnapshot(json: String): RemoteMirrorSnapshot {
        val root = JSONObject(json)
        require(root.getString("type") == "snapshot")
        val sessionsObj = root.getJSONObject("sessions")
        val sessions = buildMap {
            sessionsObj.keys().forEach { key ->
                put(key, decodeMixerSnapshot(key, sessionsObj.getJSONObject(key)))
            }
        }
        return RemoteMirrorSnapshot(
            protocolVersion = root.getInt("protocolVersion"),
            hostName = root.getString("hostName"),
            activeMixerId = root.optString("activeMixerId").takeIf { it.isNotBlank() },
            settings = decodeSettings(root.getJSONObject("settings")),
            mixers = root.getJSONArray("mixers").let { arr ->
                (0 until arr.length()).map { decodeMixerProfile(arr.getJSONObject(it)) }
            },
            sessions = sessions,
        )
    }

    fun encodeDelta(delta: RemoteDeltaFrame): String =
        JSONObject().apply {
            put("type", "delta")
            delta.activeMixerId?.let { put("activeMixerId", it) }
            delta.settings?.let { put("settings", encodeSettings(it)) }
            if (delta.sessions.isNotEmpty()) {
                put("sessions", JSONObject().apply {
                    delta.sessions.forEach { (id, mixerDelta) ->
                        put(id, encodeMixerDelta(mixerDelta))
                    }
                })
            }
            if (delta.liveWaveforms.isNotEmpty()) {
                put("liveWaveforms", encodeLiveWaveforms(delta.liveWaveforms))
            }
        }.toString()

    fun decodeDelta(json: String): RemoteDeltaFrame {
        val root = JSONObject(json)
        require(root.getString("type") == "delta")
        val sessions = if (root.has("sessions")) {
            val obj = root.getJSONObject("sessions")
            buildMap {
                obj.keys().forEach { key ->
                    put(key, decodeMixerDelta(key, obj.getJSONObject(key)))
                }
            }
        } else {
            emptyMap()
        }
        val live = if (root.has("liveWaveforms")) {
            decodeLiveWaveforms(root.getJSONObject("liveWaveforms"))
        } else {
            emptyMap()
        }
        return RemoteDeltaFrame(
            activeMixerId = root.optString("activeMixerId").takeIf { it.isNotBlank() },
            settings = if (root.has("settings")) decodeSettings(root.getJSONObject("settings")) else null,
            sessions = sessions,
            liveWaveforms = live,
        )
    }

    fun encodeCommand(type: String, payload: JSONObject = JSONObject()): String =
        JSONObject().apply {
            put("type", "command")
            put("command", type)
            put("payload", payload)
        }.toString()

    fun decodeCommand(json: String): Pair<String, JSONObject> {
        val root = JSONObject(json)
        require(root.getString("type") == "command")
        return root.getString("command") to root.getJSONObject("payload")
    }

    fun encodeWaveformChunk(
        mixerId: String,
        sessionDir: String,
        channel: Int,
        startSec: Float,
        peaks: FloatArray,
    ): String =
        JSONObject().apply {
            put("type", "waveform_chunk")
            put("mixerId", mixerId)
            put("sessionDir", sessionDir)
            put("channel", channel)
            put("startSec", startSec.toDouble())
            put("peaks", JSONArray().apply { peaks.forEach { put(it.toDouble()) } })
        }.toString()

    fun decodeWaveformChunk(json: String): WaveformChunkMessage {
        val root = JSONObject(json)
        require(root.getString("type") == "waveform_chunk")
        val arr = root.getJSONArray("peaks")
        val peaks = FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        return WaveformChunkMessage(
            mixerId = root.optString("mixerId", ""),
            sessionDir = root.optString("sessionDir", ""),
            channel = root.getInt("channel"),
            startSec = root.getDouble("startSec").toFloat(),
            peaks = peaks,
        )
    }

    fun encodeWaveformRequest(
        mixerId: String,
        sessionDir: String,
        channel: Int,
        startSec: Float,
        windowSec: Float,
    ): String =
        encodeCommand(
            "waveform_request",
            JSONObject().apply {
                put("mixerId", mixerId)
                put("sessionDir", sessionDir)
                put("channel", channel)
                put("startSec", startSec.toDouble())
                put("windowSec", windowSec.toDouble())
                put("maxPoints", RemoteProtocol.MAX_WAVEFORM_POINTS)
            },
        )

    private fun encodeSettings(s: RemoteSettingsSnapshot): JSONObject =
        JSONObject().apply {
            put("hideArmButton", s.hideArmButton)
            put("hideMonitorButton", s.hideMonitorButton)
            put("hideSoloButton", s.hideSoloButton)
            put("showWaveforms", s.showWaveforms)
            put("showVuMeters", s.showVuMeters)
            put("recordWaveformWindowSec", s.recordWaveformWindowSec.toDouble())
            put("playbackWaveformWindowSec", s.playbackWaveformWindowSec.toDouble())
            put("stripNumberMode", s.stripNumberMode)
            put("stripIconMode", s.stripIconMode)
            put("monitorGainLinear", s.monitorGainLinear.toDouble())
        }

    private fun decodeSettings(obj: JSONObject): RemoteSettingsSnapshot =
        RemoteSettingsSnapshot(
            hideArmButton = obj.optBoolean("hideArmButton"),
            hideMonitorButton = obj.optBoolean("hideMonitorButton"),
            hideSoloButton = obj.optBoolean("hideSoloButton"),
            showWaveforms = obj.optBoolean("showWaveforms", true),
            showVuMeters = obj.optBoolean("showVuMeters", true),
            recordWaveformWindowSec = obj.optDouble("recordWaveformWindowSec", 15.0).toFloat(),
            playbackWaveformWindowSec = obj.optDouble("playbackWaveformWindowSec", 180.0).toFloat(),
            stripNumberMode = obj.optInt("stripNumberMode"),
            stripIconMode = obj.optInt("stripIconMode"),
            monitorGainLinear = obj.optDouble("monitorGainLinear", 2.5).toFloat(),
        )

    private fun encodeMixerProfile(p: RemoteMixerProfileSnapshot): JSONObject =
        JSONObject().apply {
            put("id", p.id)
            put("displayName", p.displayName)
        }

    private fun decodeMixerProfile(obj: JSONObject): RemoteMixerProfileSnapshot =
        RemoteMixerProfileSnapshot(
            id = obj.getString("id"),
            displayName = obj.optString("displayName", ""),
        )

    private fun encodeSessions(sessions: Map<String, RemoteMixerSnapshot>): JSONObject =
        JSONObject().apply {
            sessions.forEach { (id, session) -> put(id, encodeMixerSnapshot(session)) }
        }

    private fun encodeMixerSnapshot(s: RemoteMixerSnapshot): JSONObject =
        JSONObject().apply {
            put("mixerId", s.mixerId)
            put("displayName", s.displayName)
            put("appMode", s.appMode)
            put("isRecording", s.isRecording)
            put("isMonitoring", s.isMonitoring)
            put("isPlaying", s.isPlaying)
            put("isVuMetering", s.isVuMetering)
            put("transportState", s.transportState)
            put("recordElapsedSec", s.recordElapsedSec.toDouble())
            put("playbackPositionSec", s.playbackPositionSec.toDouble())
            put("playbackDurationSec", s.playbackDurationSec.toDouble())
            put("captureChannelCount", s.captureChannelCount)
            put("channelStrips", encodeStrips(s.channelStrips))
            put("captureMeterLevels", encodeMeterMap(s.captureMeterLevels))
            put("soundcheckMeterLevels", encodeMeterMap(s.soundcheckMeterLevels))
            put("soundcheckSessions", JSONArray().apply {
                s.soundcheckSessions.forEach { put(encodeSoundcheckSession(it)) }
            })
            put("selectedSoundcheckDir", s.selectedSoundcheckDir)
            s.soundcheckWaveformMeta?.let { put("soundcheckWaveformMeta", encodeWaveformMeta(it)) }
            put("soundcheckViewStartSec", s.soundcheckViewStartSec.toDouble())
            put("soundcheckViewWindowSec", s.soundcheckViewWindowSec.toDouble())
            s.soundcheckLoopStartSec?.let { put("soundcheckLoopStartSec", it.toDouble()) }
            s.soundcheckLoopEndSec?.let { put("soundcheckLoopEndSec", it.toDouble()) }
            put("soundcheckLoopEnabled", s.soundcheckLoopEnabled)
            s.statusMessage?.let { put("statusMessage", it) }
            s.warningMessage?.let { put("warningMessage", it) }
            s.lastRecordingPath?.let { put("lastRecordingPath", it) }
            put("hostMixerReady", s.hostMixerReady)
        }

    private fun decodeMixerSnapshot(mixerId: String, obj: JSONObject): RemoteMixerSnapshot {
        val stripsArr = obj.getJSONArray("channelStrips")
        val strips = (0 until stripsArr.length()).map { decodeStrip(stripsArr.getJSONObject(it)) }
        val sessionsArr = obj.getJSONArray("soundcheckSessions")
        val sessions = (0 until sessionsArr.length()).map {
            decodeSoundcheckSession(sessionsArr.getJSONObject(it))
        }
        return RemoteMixerSnapshot(
            mixerId = mixerId,
            displayName = obj.optString("displayName", ""),
            appMode = obj.optInt("appMode"),
            isRecording = obj.optBoolean("isRecording"),
            isMonitoring = obj.optBoolean("isMonitoring"),
            isPlaying = obj.optBoolean("isPlaying"),
            isVuMetering = obj.optBoolean("isVuMetering"),
            transportState = obj.optString("transportState", "IDLE"),
            recordElapsedSec = obj.optDouble("recordElapsedSec").toFloat(),
            playbackPositionSec = obj.optDouble("playbackPositionSec").toFloat(),
            playbackDurationSec = obj.optDouble("playbackDurationSec").toFloat(),
            captureChannelCount = obj.optInt("captureChannelCount"),
            channelStrips = strips,
            captureMeterLevels = decodeMeterMap(obj.optJSONObject("captureMeterLevels")),
            soundcheckMeterLevels = decodeMeterMap(obj.optJSONObject("soundcheckMeterLevels")),
            soundcheckSessions = sessions,
            selectedSoundcheckDir = obj.optString("selectedSoundcheckDir").takeIf { it.isNotBlank() },
            soundcheckWaveformMeta = obj.optJSONObject("soundcheckWaveformMeta")?.let(::decodeWaveformMeta),
            soundcheckViewStartSec = obj.optDouble("soundcheckViewStartSec").toFloat(),
            soundcheckViewWindowSec = obj.optDouble("soundcheckViewWindowSec", 180.0).toFloat(),
            soundcheckLoopStartSec = obj.optDouble("soundcheckLoopStartSec").takeIf { !it.isNaN() }?.toFloat(),
            soundcheckLoopEndSec = obj.optDouble("soundcheckLoopEndSec").takeIf { !it.isNaN() }?.toFloat(),
            soundcheckLoopEnabled = obj.optBoolean("soundcheckLoopEnabled"),
            statusMessage = obj.optString("statusMessage").takeIf { it.isNotBlank() },
            warningMessage = obj.optString("warningMessage").takeIf { it.isNotBlank() },
            lastRecordingPath = obj.optString("lastRecordingPath").takeIf { it.isNotBlank() },
            hostMixerReady = obj.optBoolean("hostMixerReady"),
        )
    }

    private fun encodeMixerDelta(d: RemoteMixerDelta): JSONObject =
        JSONObject().apply {
            d.appMode?.let { put("appMode", it) }
            d.transportState?.let { put("transportState", it) }
            d.isRecording?.let { put("isRecording", it) }
            d.isMonitoring?.let { put("isMonitoring", it) }
            d.isPlaying?.let { put("isPlaying", it) }
            d.isVuMetering?.let { put("isVuMetering", it) }
            d.recordElapsedSec?.let { put("recordElapsedSec", it.toDouble()) }
            d.playbackPositionSec?.let { put("playbackPositionSec", it.toDouble()) }
            d.playbackDurationSec?.let { put("playbackDurationSec", it.toDouble()) }
            d.captureMeterLevels?.let { put("captureMeterLevels", encodeMeterMap(it)) }
            d.soundcheckMeterLevels?.let { put("soundcheckMeterLevels", encodeMeterMap(it)) }
            d.channelStrips?.let { put("channelStrips", encodeStrips(it)) }
            d.soundcheckSessions?.let { sessions ->
                put("soundcheckSessions", JSONArray().apply { sessions.forEach { put(encodeSoundcheckSession(it)) } })
            }
            if (d.selectedSoundcheckDir != null) put("selectedSoundcheckDir", d.selectedSoundcheckDir)
            d.soundcheckWaveformMeta?.let { put("soundcheckWaveformMeta", encodeWaveformMeta(it)) }
            d.soundcheckViewStartSec?.let { put("soundcheckViewStartSec", it.toDouble()) }
            d.soundcheckViewWindowSec?.let { put("soundcheckViewWindowSec", it.toDouble()) }
            d.soundcheckLoopStartSec?.let { put("soundcheckLoopStartSec", it.toDouble()) }
            d.soundcheckLoopEndSec?.let { put("soundcheckLoopEndSec", it.toDouble()) }
            d.soundcheckLoopEnabled?.let { put("soundcheckLoopEnabled", it) }
            if (d.statusMessage != null) put("statusMessage", d.statusMessage)
            if (d.warningMessage != null) put("warningMessage", d.warningMessage)
            if (d.lastRecordingPath != null) put("lastRecordingPath", d.lastRecordingPath)
            d.hostMixerReady?.let { put("hostMixerReady", it) }
        }

    private fun decodeMixerDelta(mixerId: String, obj: JSONObject): RemoteMixerDelta {
        val strips = obj.optJSONArray("channelStrips")?.let { arr ->
            (0 until arr.length()).map { decodeStrip(arr.getJSONObject(it)) }
        }
        val sessions = obj.optJSONArray("soundcheckSessions")?.let { arr ->
            (0 until arr.length()).map { decodeSoundcheckSession(arr.getJSONObject(it)) }
        }
        return RemoteMixerDelta(
            mixerId = mixerId,
            appMode = if (obj.has("appMode")) obj.getInt("appMode") else null,
            transportState = obj.optString("transportState").takeIf { it.isNotBlank() },
            isRecording = if (obj.has("isRecording")) obj.getBoolean("isRecording") else null,
            isMonitoring = if (obj.has("isMonitoring")) obj.getBoolean("isMonitoring") else null,
            isPlaying = if (obj.has("isPlaying")) obj.getBoolean("isPlaying") else null,
            isVuMetering = if (obj.has("isVuMetering")) obj.getBoolean("isVuMetering") else null,
            recordElapsedSec = if (obj.has("recordElapsedSec")) obj.getDouble("recordElapsedSec").toFloat() else null,
            playbackPositionSec = if (obj.has("playbackPositionSec")) {
                obj.getDouble("playbackPositionSec").toFloat()
            } else {
                null
            },
            playbackDurationSec = if (obj.has("playbackDurationSec")) {
                obj.getDouble("playbackDurationSec").toFloat()
            } else {
                null
            },
            captureMeterLevels = obj.optJSONObject("captureMeterLevels")?.let(::decodeMeterMap),
            soundcheckMeterLevels = obj.optJSONObject("soundcheckMeterLevels")?.let(::decodeMeterMap),
            channelStrips = strips,
            soundcheckSessions = sessions,
            selectedSoundcheckDir = if (obj.has("selectedSoundcheckDir")) obj.optString("selectedSoundcheckDir") else null,
            soundcheckWaveformMeta = obj.optJSONObject("soundcheckWaveformMeta")?.let(::decodeWaveformMeta),
            soundcheckViewStartSec = if (obj.has("soundcheckViewStartSec")) {
                obj.getDouble("soundcheckViewStartSec").toFloat()
            } else {
                null
            },
            soundcheckViewWindowSec = if (obj.has("soundcheckViewWindowSec")) {
                obj.getDouble("soundcheckViewWindowSec").toFloat()
            } else {
                null
            },
            soundcheckLoopStartSec = if (obj.has("soundcheckLoopStartSec")) {
                obj.getDouble("soundcheckLoopStartSec").toFloat()
            } else {
                null
            },
            soundcheckLoopEndSec = if (obj.has("soundcheckLoopEndSec")) {
                obj.getDouble("soundcheckLoopEndSec").toFloat()
            } else {
                null
            },
            soundcheckLoopEnabled = if (obj.has("soundcheckLoopEnabled")) obj.getBoolean("soundcheckLoopEnabled") else null,
            statusMessage = if (obj.has("statusMessage")) obj.optString("statusMessage") else null,
            warningMessage = if (obj.has("warningMessage")) obj.optString("warningMessage") else null,
            lastRecordingPath = if (obj.has("lastRecordingPath")) obj.optString("lastRecordingPath") else null,
            hostMixerReady = if (obj.has("hostMixerReady")) obj.getBoolean("hostMixerReady") else null,
        )
    }

    private fun encodeStrips(strips: List<RemoteChannelStripSnapshot>): JSONArray =
        JSONArray().apply {
            strips.forEach { strip ->
                put(
                    JSONObject().apply {
                        put("index", strip.index)
                        put("displayName", strip.displayName)
                        put("label", strip.label)
                        put("colorArgb", strip.colorArgb)
                        strip.iconId?.let { put("iconId", it) }
                        put("armed", strip.armed)
                        put("monitoring", strip.monitoring)
                        put("solo", strip.solo)
                        put("muted", strip.muted)
                    },
                )
            }
        }

    private fun decodeStrip(obj: JSONObject): RemoteChannelStripSnapshot =
        RemoteChannelStripSnapshot(
            index = obj.getInt("index"),
            displayName = obj.optString("displayName", ""),
            label = obj.optString("label", ""),
            colorArgb = obj.optInt("colorArgb"),
            iconId = if (obj.has("iconId")) obj.getInt("iconId") else null,
            armed = obj.optBoolean("armed"),
            monitoring = obj.optBoolean("monitoring"),
            solo = obj.optBoolean("solo"),
            muted = obj.optBoolean("muted"),
        )

    private fun encodeSoundcheckSession(s: RemoteSoundcheckSessionSnapshot): JSONObject =
        JSONObject().apply {
            put("sessionDir", s.sessionDir)
            put("title", s.title)
            put("durationSec", s.durationSec.toDouble())
            put("channelCount", s.channelCount)
        }

    private fun decodeSoundcheckSession(obj: JSONObject): RemoteSoundcheckSessionSnapshot =
        RemoteSoundcheckSessionSnapshot(
            sessionDir = obj.getString("sessionDir"),
            title = obj.optString("title", ""),
            durationSec = obj.optDouble("durationSec").toFloat(),
            channelCount = obj.optInt("channelCount"),
        )

    private fun encodeWaveformMeta(m: RemoteSoundcheckWaveformMeta): JSONObject =
        JSONObject().apply {
            put("durationSec", m.durationSec.toDouble())
            put("peaksPerSec", m.peaksPerSec)
            put("channelCount", m.channelCount)
            put("loading", m.loading)
            put("progress", m.progress.toDouble())
        }

    private fun decodeWaveformMeta(obj: JSONObject): RemoteSoundcheckWaveformMeta =
        RemoteSoundcheckWaveformMeta(
            durationSec = obj.optDouble("durationSec").toFloat(),
            peaksPerSec = obj.optInt("peaksPerSec"),
            channelCount = obj.optInt("channelCount"),
            loading = obj.optBoolean("loading"),
            progress = obj.optDouble("progress").toFloat(),
        )

    private fun encodeMeterMap(levels: Map<Int, Float>): JSONObject =
        JSONObject().apply { levels.forEach { (ch, level) -> put(ch.toString(), level.toDouble()) } }

    private fun decodeMeterMap(obj: JSONObject?): Map<Int, Float> {
        if (obj == null) return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                put(key.toInt(), obj.getDouble(key).toFloat())
            }
        }
    }

    private fun encodeLiveWaveforms(
        live: Map<String, Map<Int, RemoteLiveWaveformTail>>,
    ): JSONObject =
        JSONObject().apply {
            live.forEach { (mixerId, channels) ->
                put(
                    mixerId,
                    JSONObject().apply {
                        channels.forEach { (ch, tail) ->
                            put(
                                ch.toString(),
                                JSONObject().apply {
                                    put("gen", tail.generation)
                                    put("tail", Base64.getEncoder().encodeToString(tail.peaksU8))
                                },
                            )
                        }
                    },
                )
            }
        }

    private fun decodeLiveWaveforms(obj: JSONObject): Map<String, Map<Int, RemoteLiveWaveformTail>> =
        buildMap {
            obj.keys().forEach { mixerId ->
                val channelsObj = obj.getJSONObject(mixerId)
                val channels = buildMap {
                    channelsObj.keys().forEach { chKey ->
                        val tailObj = channelsObj.getJSONObject(chKey)
                        put(
                            chKey.toInt(),
                            RemoteLiveWaveformTail(
                                generation = tailObj.getInt("gen"),
                                peaksU8 = Base64.getDecoder().decode(tailObj.getString("tail")),
                            ),
                        )
                    }
                }
                put(mixerId, channels)
            }
        }
}
