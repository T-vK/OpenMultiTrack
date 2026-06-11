package org.openmultitrack.app.remote

import org.json.JSONArray
import org.json.JSONObject
import org.openmultitrack.app.audio.LiveWaveformSnapshot
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.service.SoundcheckSessionItem
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.mixer.MixerRoutingConfig
import org.openmultitrack.domain.remote.RemoteProtocol
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.remote.RemoteChannelStripSnapshot
import org.openmultitrack.remote.RemoteDeltaFrame
import org.openmultitrack.remote.RemoteLiveWaveformTail
import org.openmultitrack.remote.RemoteMirrorSnapshot
import org.openmultitrack.remote.RemoteMixerDelta
import org.openmultitrack.remote.RemoteMixerProfileSnapshot
import org.openmultitrack.remote.RemoteMixerSnapshot
import org.openmultitrack.remote.RemoteRoutingSnapshot
import org.openmultitrack.remote.RemoteSettingsSnapshot
import org.openmultitrack.remote.RemoteSoundcheckSessionSnapshot
import org.openmultitrack.remote.RemoteSoundcheckWaveformMeta
import org.openmultitrack.remote.RemoteWaveformUtil
import org.openmultitrack.sessionio.wav.SessionWaveformOverview

object RemoteSnapshotMapper {
    fun buildSnapshot(
        hostName: String,
        settings: AppSettingsStore,
        mixers: List<MixerProfile>,
        activeMixerId: String?,
        sessions: Map<String, MixerSessionUiState>,
        routingByMixer: Map<String, MixerRoutingConfig> = emptyMap(),
    ): RemoteMirrorSnapshot =
        RemoteMirrorSnapshot(
            protocolVersion = RemoteProtocol.VERSION,
            hostName = hostName,
            activeMixerId = activeMixerId,
            settings = settingsToRemote(settings),
            mixers = mixers.map { RemoteMixerProfileSnapshot(it.id, it.displayName) },
            sessions = sessions.mapValues { (id, session) ->
                sessionToRemote(session, routingByMixer[id] ?: MixerRoutingConfig())
            },
        )

    fun routingToRemote(config: MixerRoutingConfig): RemoteRoutingSnapshot =
        RemoteRoutingSnapshot(
            inputMap = config.inputMap,
            outputMap = config.outputMap,
            hiddenRecord = config.hiddenRecord,
            hiddenSoundcheck = config.hiddenSoundcheck,
        )

    fun routingFromRemote(remote: RemoteRoutingSnapshot): MixerRoutingConfig =
        MixerRoutingConfig(
            inputMap = remote.inputMap,
            outputMap = remote.outputMap,
            hiddenRecord = remote.hiddenRecord,
            hiddenSoundcheck = remote.hiddenSoundcheck,
        )

    fun routingFromPayload(obj: JSONObject): MixerRoutingConfig =
        routingFromRemote(
            RemoteRoutingSnapshot(
                inputMap = jsonToIntMap(obj.optJSONObject("inputMap")),
                outputMap = jsonToIntMap(obj.optJSONObject("outputMap")),
                hiddenRecord = jsonToIntSet(obj.optJSONArray("hiddenRecord")),
                hiddenSoundcheck = jsonToIntSet(obj.optJSONArray("hiddenSoundcheck")),
            ),
        )

    fun routingToPayload(config: MixerRoutingConfig): JSONObject =
        JSONObject().apply {
            put("inputMap", intMapToJson(config.inputMap))
            put("outputMap", intMapToJson(config.outputMap))
            put("hiddenRecord", intSetToJson(config.hiddenRecord))
            put("hiddenSoundcheck", intSetToJson(config.hiddenSoundcheck))
        }

    private fun intMapToJson(map: Map<Int, Int>): JSONObject =
        JSONObject().apply { map.forEach { (k, v) -> put(k.toString(), v) } }

    private fun jsonToIntMap(obj: JSONObject?): Map<Int, Int> {
        if (obj == null) return emptyMap()
        return buildMap {
            obj.keys().forEach { key -> put(key.toInt(), obj.getInt(key)) }
        }
    }

    private fun intSetToJson(set: Set<Int>): JSONArray =
        JSONArray().apply { set.sorted().forEach { put(it) } }

    private fun jsonToIntSet(arr: JSONArray?): Set<Int> {
        if (arr == null) return emptySet()
        return buildSet {
            for (i in 0 until arr.length()) add(arr.getInt(i))
        }
    }

    fun buildDelta(
        previous: RemoteMirrorSnapshot?,
        current: RemoteMirrorSnapshot,
        liveWaveforms: Map<String, Map<Int, RemoteLiveWaveformTail>>,
    ): RemoteDeltaFrame? {
        val sessions = buildMap {
            current.sessions.forEach { (id, session) ->
                val prev = previous?.sessions?.get(id)
                mixerDelta(prev, session)?.let { put(id, it) }
            }
        }
        val settings = if (previous == null || previous.settings != current.settings) {
            current.settings
        } else {
            null
        }
        val active = if (previous?.activeMixerId != current.activeMixerId) current.activeMixerId else null
        if (sessions.isEmpty() && settings == null && active == null && liveWaveforms.isEmpty()) {
            return null
        }
        return RemoteDeltaFrame(
            activeMixerId = active,
            settings = settings,
            sessions = sessions,
            liveWaveforms = liveWaveforms,
        )
    }

    fun applyDelta(
        snapshot: RemoteMirrorSnapshot,
        delta: RemoteDeltaFrame,
        livePeaks: MutableMap<String, MutableMap<Int, LiveWaveformSnapshot>>,
    ): RemoteMirrorSnapshot {
        val settings = delta.settings ?: snapshot.settings
        val waveformCapacity = liveWaveformCapacity(settings.recordWaveformHistorySec)
        val sessions = snapshot.sessions.toMutableMap()
        delta.sessions.forEach { (id, mixerDelta) ->
            val base = sessions[id] ?: return@forEach
            sessions[id] = applyMixerDelta(base, mixerDelta)
        }
        delta.liveWaveforms.forEach { (mixerId, channels) ->
            val mixerPeaks = livePeaks.getOrPut(mixerId) { mutableMapOf() }
            val recordElapsedSec = sessions[mixerId]?.recordElapsedSec
            channels.forEach { (ch, tail) ->
                val decoded = RemoteWaveformUtil.decodeTail(tail.peaksU8)
                val merged = RemoteWaveformUtil.mergeLiveWaveformTail(
                    existingPeaks = mixerPeaks[ch]?.peaks,
                    newTail = decoded,
                    capacity = waveformCapacity,
                )
                mixerPeaks[ch] = LiveWaveformSnapshot(
                    peaks = merged,
                    peakCount = merged.size,
                    capacity = waveformCapacity,
                )
            }
            if (recordElapsedSec != null && recordElapsedSec > 0f) {
                alignLiveWaveformChannels(
                    mixerPeaks,
                    recordElapsedSec,
                    waveformCapacity,
                    settings.recordWaveformHistorySec,
                )
            }
        }
        return snapshot.copy(
            activeMixerId = delta.activeMixerId ?: snapshot.activeMixerId,
            settings = settings,
            sessions = sessions,
        )
    }

    fun remoteToMixerSession(remote: RemoteMixerSnapshot, livePeaks: Map<Int, LiveWaveformSnapshot>): MixerSessionUiState =
        MixerSessionUiState(
            mixerId = remote.mixerId,
            mixerProfile = MixerProfile(
                id = remote.mixerId,
                usbDeviceName = null,
                vendorId = 0,
                productId = 0,
                serialNumber = null,
                productName = null,
                displayName = remote.displayName,
            ),
            channelStrips = remote.channelStrips.map(::stripToDomain),
            appMode = AppMode.entries.getOrElse(remote.appMode) { AppMode.MULTITRACK_RECORD },
            isRecording = remote.isRecording,
            isMonitoring = remote.isMonitoring,
            isVuMetering = remote.isVuMetering,
            isPlaying = remote.isPlaying,
            transportState = runCatching { TransportState.valueOf(remote.transportState) }
                .getOrDefault(TransportState.IDLE),
            statusMessage = remote.statusMessage,
            warningMessage = remote.warningMessage,
            captureChannelCount = remote.captureChannelCount,
            recordElapsedSec = remote.recordElapsedSec,
            waveformPeaks = livePeaks,
            captureMeterLevels = remote.captureMeterLevels,
            soundcheckSessions = remote.soundcheckSessions.map {
                SoundcheckSessionItem(
                    sessionDir = it.sessionDir,
                    title = it.title,
                    startedAtEpochMs = 0L,
                    durationSec = it.durationSec,
                    channelCount = it.channelCount,
                )
            },
            selectedSoundcheckDir = remote.selectedSoundcheckDir,
            playbackPositionSec = remote.playbackPositionSec,
            playbackDurationSec = remote.playbackDurationSec,
            soundcheckWaveforms = remote.soundcheckWaveformMeta?.let { meta ->
                SessionWaveformOverview(
                    peaksByChannel = emptyMap(),
                    peaksPerSec = meta.peaksPerSec.toFloat(),
                    durationSec = meta.durationSec,
                )
            },
            soundcheckWaveformsLoading = remote.soundcheckWaveformMeta?.loading == true,
            soundcheckWaveformProgress = remote.soundcheckWaveformMeta?.progress ?: 0f,
            soundcheckWaveformChannelsTotal = remote.soundcheckWaveformMeta?.channelCount ?: 0,
            soundcheckViewStartSec = remote.soundcheckViewStartSec,
            soundcheckViewWindowSec = remote.soundcheckViewWindowSec,
            soundcheckLoopStartSec = remote.soundcheckLoopStartSec,
            soundcheckLoopEndSec = remote.soundcheckLoopEndSec,
            soundcheckLoopEnabled = remote.soundcheckLoopEnabled,
            soundcheckMeterLevels = remote.soundcheckMeterLevels,
            lastRecordingPath = remote.lastRecordingPath,
        )

    fun remoteSettingsToUi(settings: RemoteSettingsSnapshot): RemoteUiSettings =
        RemoteUiSettings(
            hideArmButton = settings.hideArmButton,
            hideMonitorButton = settings.hideMonitorButton,
            hideSoloButton = settings.hideSoloButton,
            showWaveforms = settings.showWaveforms,
            showVuMeters = settings.showVuMeters,
            recordWaveformWindowSec = settings.recordWaveformWindowSec,
            recordWaveformHistorySec = settings.recordWaveformHistorySec,
            playbackWaveformWindowSec = settings.playbackWaveformWindowSec,
            stripNumberMode = settings.stripNumberMode,
            stripIconMode = settings.stripIconMode,
            monitorGainLinear = settings.monitorGainLinear,
        )

    fun liveWaveformTails(
        sessions: Map<String, MixerSessionUiState>,
        previousGen: MutableMap<String, MutableMap<Int, Int>>,
        previousTails: MutableMap<String, MutableMap<Int, ByteArray>>,
    ): Map<String, Map<Int, RemoteLiveWaveformTail>> =
        buildMap {
            sessions.forEach { (mixerId, session) ->
                if (session.waveformPeaks.isEmpty()) return@forEach
                val mixerGen = previousGen.getOrPut(mixerId) { mutableMapOf() }
                val mixerPrev = previousTails.getOrPut(mixerId) { mutableMapOf() }
                val changedChannels = mutableSetOf<Int>()
                session.waveformPeaks.forEach { (ch, snap) ->
                    if (snap.peaks.isEmpty()) return@forEach
                    val tail = RemoteWaveformUtil.quantizeTail(snap.peaks)
                    val prev = mixerPrev[ch]
                    if (prev == null || !prev.contentEquals(tail)) {
                        mixerPrev[ch] = tail
                        changedChannels.add(ch)
                    }
                }
                if (changedChannels.isEmpty()) return@forEach
                val channelsToEmit = changedChannels
                val channels = buildMap<Int, RemoteLiveWaveformTail> {
                    channelsToEmit.forEach { ch ->
                        val tail = mixerPrev[ch] ?: return@forEach
                        val gen = (mixerGen[ch] ?: 0) + 1
                        mixerGen[ch] = gen
                        put(ch, RemoteLiveWaveformTail(gen, tail))
                    }
                }
                if (channels.isNotEmpty()) put(mixerId, channels)
            }
        }

    private fun settingsToRemote(settings: AppSettingsStore): RemoteSettingsSnapshot =
        RemoteSettingsSnapshot(
            hideArmButton = settings.hideArmButton,
            hideMonitorButton = settings.hideMonitorButton,
            hideSoloButton = settings.hideSoloButton,
            showWaveforms = settings.showWaveforms,
            showVuMeters = settings.showVuMeters,
            recordWaveformWindowSec = settings.recordWaveformWindowSec,
            recordWaveformHistorySec = settings.recordWaveformHistorySec,
            playbackWaveformWindowSec = settings.playbackWaveformWindowSec,
            stripNumberMode = settings.stripNumberMode.ordinal,
            stripIconMode = settings.stripIconMode.ordinal,
            monitorGainLinear = settings.monitorGainLinear,
        )

    private fun sessionToRemote(
        session: MixerSessionUiState,
        routing: MixerRoutingConfig,
    ): RemoteMixerSnapshot =
        RemoteMixerSnapshot(
            mixerId = session.mixerId,
            displayName = session.mixerProfile?.displayName ?: session.mixerId,
            appMode = session.appMode.ordinal,
            isRecording = session.isRecording,
            isMonitoring = session.isMonitoring,
            isPlaying = session.isPlaying,
            isVuMetering = session.isVuMetering,
            transportState = session.transportState.name,
            recordElapsedSec = session.recordElapsedSec,
            playbackPositionSec = session.playbackPositionSec,
            playbackDurationSec = session.playbackDurationSec,
            captureChannelCount = session.captureChannelCount,
            channelStrips = session.channelStrips.map(::stripToRemote),
            captureMeterLevels = session.captureMeterLevels,
            soundcheckMeterLevels = session.soundcheckMeterLevels,
            soundcheckSessions = session.soundcheckSessions.map {
                RemoteSoundcheckSessionSnapshot(
                    sessionDir = it.sessionDir,
                    title = it.title,
                    durationSec = it.durationSec,
                    channelCount = it.channelCount,
                )
            },
            selectedSoundcheckDir = session.selectedSoundcheckDir,
            soundcheckWaveformMeta = buildSoundcheckWaveformMeta(session),
            soundcheckViewStartSec = session.soundcheckViewStartSec,
            soundcheckViewWindowSec = session.soundcheckViewWindowSec,
            soundcheckLoopStartSec = session.soundcheckLoopStartSec,
            soundcheckLoopEndSec = session.soundcheckLoopEndSec,
            soundcheckLoopEnabled = session.soundcheckLoopEnabled,
            statusMessage = session.statusMessage,
            warningMessage = session.warningMessage,
            lastRecordingPath = session.lastRecordingPath,
            hostMixerReady = session.probe != null,
            routing = routingToRemote(routing),
        )

    private fun mixerDelta(prev: RemoteMixerSnapshot?, current: RemoteMixerSnapshot): RemoteMixerDelta? {
        val delta = RemoteMixerDelta(
            mixerId = current.mixerId,
            appMode = changed(prev?.appMode, current.appMode),
            transportState = changed(prev?.transportState, current.transportState),
            isRecording = changed(prev?.isRecording, current.isRecording),
            isMonitoring = changed(prev?.isMonitoring, current.isMonitoring),
            isPlaying = changed(prev?.isPlaying, current.isPlaying),
            isVuMetering = changed(prev?.isVuMetering, current.isVuMetering),
            recordElapsedSec = changed(prev?.recordElapsedSec, current.recordElapsedSec),
            playbackPositionSec = changed(prev?.playbackPositionSec, current.playbackPositionSec),
            playbackDurationSec = changed(prev?.playbackDurationSec, current.playbackDurationSec),
            captureMeterLevels = if (prev?.captureMeterLevels != current.captureMeterLevels) {
                current.captureMeterLevels
            } else {
                null
            },
            soundcheckMeterLevels = if (prev?.soundcheckMeterLevels != current.soundcheckMeterLevels) {
                current.soundcheckMeterLevels
            } else {
                null
            },
            channelStrips = if (prev?.channelStrips != current.channelStrips) current.channelStrips else null,
            soundcheckSessions = if (prev?.soundcheckSessions != current.soundcheckSessions) {
                current.soundcheckSessions
            } else {
                null
            },
            selectedSoundcheckDir = optionalStringChanged(prev?.selectedSoundcheckDir, current.selectedSoundcheckDir),
            soundcheckWaveformMeta = if (prev?.soundcheckWaveformMeta != current.soundcheckWaveformMeta) {
                current.soundcheckWaveformMeta
            } else {
                null
            },
            soundcheckViewStartSec = changed(prev?.soundcheckViewStartSec, current.soundcheckViewStartSec),
            soundcheckViewWindowSec = changed(prev?.soundcheckViewWindowSec, current.soundcheckViewWindowSec),
            soundcheckLoopStartSec = changed(prev?.soundcheckLoopStartSec, current.soundcheckLoopStartSec),
            soundcheckLoopEndSec = changed(prev?.soundcheckLoopEndSec, current.soundcheckLoopEndSec),
            soundcheckLoopEnabled = changed(prev?.soundcheckLoopEnabled, current.soundcheckLoopEnabled),
            statusMessage = optionalStringChanged(prev?.statusMessage, current.statusMessage),
            warningMessage = optionalStringChanged(prev?.warningMessage, current.warningMessage),
            lastRecordingPath = optionalStringChanged(prev?.lastRecordingPath, current.lastRecordingPath),
            hostMixerReady = changed(prev?.hostMixerReady, current.hostMixerReady),
            routing = if (prev?.routing != current.routing) current.routing else null,
        )
        return if (delta == RemoteMixerDelta(mixerId = current.mixerId)) null else delta
    }

    private fun applyMixerDelta(base: RemoteMixerSnapshot, delta: RemoteMixerDelta): RemoteMixerSnapshot =
        base.copy(
            appMode = delta.appMode ?: base.appMode,
            transportState = delta.transportState ?: base.transportState,
            isRecording = delta.isRecording ?: base.isRecording,
            isMonitoring = delta.isMonitoring ?: base.isMonitoring,
            isPlaying = delta.isPlaying ?: base.isPlaying,
            isVuMetering = delta.isVuMetering ?: base.isVuMetering,
            recordElapsedSec = delta.recordElapsedSec ?: base.recordElapsedSec,
            playbackPositionSec = delta.playbackPositionSec ?: base.playbackPositionSec,
            playbackDurationSec = delta.playbackDurationSec ?: base.playbackDurationSec,
            captureMeterLevels = delta.captureMeterLevels ?: base.captureMeterLevels,
            soundcheckMeterLevels = delta.soundcheckMeterLevels ?: base.soundcheckMeterLevels,
            channelStrips = delta.channelStrips ?: base.channelStrips,
            soundcheckSessions = delta.soundcheckSessions ?: base.soundcheckSessions,
            selectedSoundcheckDir = applyOptionalString(delta.selectedSoundcheckDir, base.selectedSoundcheckDir),
            soundcheckWaveformMeta = delta.soundcheckWaveformMeta ?: base.soundcheckWaveformMeta,
            soundcheckViewStartSec = delta.soundcheckViewStartSec ?: base.soundcheckViewStartSec,
            soundcheckViewWindowSec = delta.soundcheckViewWindowSec ?: base.soundcheckViewWindowSec,
            soundcheckLoopStartSec = delta.soundcheckLoopStartSec ?: base.soundcheckLoopStartSec,
            soundcheckLoopEndSec = delta.soundcheckLoopEndSec ?: base.soundcheckLoopEndSec,
            soundcheckLoopEnabled = delta.soundcheckLoopEnabled ?: base.soundcheckLoopEnabled,
            statusMessage = applyOptionalString(delta.statusMessage, base.statusMessage),
            warningMessage = applyOptionalString(delta.warningMessage, base.warningMessage),
            lastRecordingPath = applyOptionalString(delta.lastRecordingPath, base.lastRecordingPath),
            hostMixerReady = delta.hostMixerReady ?: base.hostMixerReady,
            routing = delta.routing ?: base.routing,
        )

    private fun stripToRemote(strip: ChannelStripState): RemoteChannelStripSnapshot =
        RemoteChannelStripSnapshot(
            index = strip.index,
            displayName = strip.displayName,
            label = strip.label,
            colorArgb = strip.colorArgb,
            iconId = strip.iconId,
            armed = strip.armed,
            monitoring = strip.monitoring,
            solo = strip.solo,
            muted = strip.muted,
        )

    private fun stripToDomain(strip: RemoteChannelStripSnapshot): ChannelStripState =
        ChannelStripState(
            index = strip.index,
            displayName = strip.displayName,
            label = strip.label,
            colorArgb = strip.colorArgb,
            iconId = strip.iconId,
            armed = strip.armed,
            monitoring = strip.monitoring,
            solo = strip.solo,
            muted = strip.muted,
        )

    private fun liveWaveformCapacity(recordWindowSec: Float): Int =
        kotlin.math.max(10, (recordWindowSec * RemoteProtocol.LIVE_WAVEFORM_PEAKS_PER_SEC).toInt())

    private fun alignLiveWaveformChannels(
        mixerPeaks: MutableMap<Int, LiveWaveformSnapshot>,
        recordElapsedSec: Float,
        capacity: Int,
        windowSec: Float,
    ) {
        if (capacity <= 0) return
        val rolling = recordElapsedSec > windowSec
        mixerPeaks.keys.toList().forEach { ch ->
            val snap = mixerPeaks[ch] ?: return@forEach
            val peaks = snap.peaks
            if (peaks.size <= capacity) {
                if (snap.capacity != capacity) {
                    mixerPeaks[ch] = snap.copy(capacity = capacity)
                }
                return@forEach
            }
            val aligned = if (rolling) {
                peaks.copyOfRange(peaks.size - capacity, peaks.size)
            } else {
                peaks.copyOfRange(0, capacity)
            }
            mixerPeaks[ch] = snap.copy(peaks = aligned, capacity = capacity)
        }
    }

    private fun buildSoundcheckWaveformMeta(session: MixerSessionUiState): RemoteSoundcheckWaveformMeta? {
        val overview = session.soundcheckWaveforms
        if (overview != null) {
            val availableChannels = overview.peaksByChannel.keys.maxOrNull()?.plus(1)
                ?: overview.peaksByChannel.size
            return RemoteSoundcheckWaveformMeta(
                durationSec = overview.durationSec,
                peaksPerSec = overview.peaksPerSec.toInt(),
                channelCount = availableChannels.coerceAtLeast(1),
                loading = session.soundcheckWaveformsLoading,
                progress = session.soundcheckWaveformProgress,
            )
        }
        if (session.selectedSoundcheckDir == null) return null
        val channelCount = session.soundcheckWaveformChannelsTotal
            .coerceAtLeast(session.captureChannelCount)
            .coerceAtLeast(session.channelStrips.size)
        if (channelCount <= 0 && !session.soundcheckWaveformsLoading) return null
        return RemoteSoundcheckWaveformMeta(
            durationSec = session.playbackDurationSec,
            peaksPerSec = 30,
            channelCount = channelCount.coerceAtLeast(1),
            loading = session.soundcheckWaveformsLoading,
            progress = session.soundcheckWaveformProgress,
        )
    }

    /** Empty string means "cleared"; null means "unchanged". */
    private fun optionalStringChanged(prev: String?, current: String?): String? =
        when {
            prev == current -> null
            current == null -> ""
            else -> current
        }

    private fun applyOptionalString(delta: String?, base: String?): String? =
        when (delta) {
            null -> base
            "" -> null
            else -> delta
        }

    private fun <T> changed(prev: T?, current: T): T? = if (prev == current) null else current
}

data class RemoteUiSettings(
    val hideArmButton: Boolean,
    val hideMonitorButton: Boolean,
    val hideSoloButton: Boolean,
    val showWaveforms: Boolean,
    val showVuMeters: Boolean,
    val recordWaveformWindowSec: Float,
    val recordWaveformHistorySec: Float,
    val playbackWaveformWindowSec: Float,
    val stripNumberMode: Int,
    val stripIconMode: Int,
    val monitorGainLinear: Float,
)
