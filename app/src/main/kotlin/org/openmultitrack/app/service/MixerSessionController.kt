package org.openmultitrack.app.service

import android.content.Context
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openmultitrack.app.audio.CaptureSessionEngine
import org.openmultitrack.app.audio.VirtualDevicePlayback
import org.openmultitrack.app.audio.LiveWaveformSnapshot
import org.openmultitrack.app.audio.MonitorMixConfig
import org.openmultitrack.app.audio.PlaybackMixContext
import org.openmultitrack.app.audio.SessionPlayer
import org.openmultitrack.app.audio.TransportTrace
import org.openmultitrack.app.audio.TransportTraceHub
import org.openmultitrack.app.routing.RoutingAutomationBridge
import org.openmultitrack.app.routing.RoutingHookResult
import org.openmultitrack.app.routing.SoundcheckTrackChannels
import org.openmultitrack.app.audio.SyntheticCaptureGenerator
import org.openmultitrack.app.audio.VirtualMixerProbe
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.RecordingStorageResolver
import org.openmultitrack.app.root.LoopbackSetup
import org.openmultitrack.app.root.RootShell
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.DemoBandChannels
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.mixer.MixerRoutingConfig
import org.openmultitrack.domain.mixer.VirtualMixer
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.sessionio.session.SessionLibrary
import org.openmultitrack.sessionio.session.SessionCueFile
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.session.SessionTrackmark
import org.openmultitrack.sessionio.session.SessionPlaybackDuration
import org.openmultitrack.sessionio.wav.SessionWaveformCache
import org.openmultitrack.sessionio.wav.SessionWaveformExtractor
import org.openmultitrack.sessionio.wav.SessionWaveformOverview
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.sessionio.wav.WavWriter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.Flow8UsbPlaybackProfile
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.MixerUsbChannelCounts
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.mixer.behringer.ScribbleStripLabel
import org.openmultitrack.mixer.behringer.UsbChannelScribble
import org.openmultitrack.usb.UsbAudioStreamHandle
import java.io.File

data class SoundcheckSessionItem(
    val sessionDir: String,
    val title: String,
    val startedAtEpochMs: Long,
    val durationSec: Float,
    val channelCount: Int,
)

data class MixerSessionUiState(
    val mixerId: String,
    val mixerProfile: MixerProfile? = null,
    val usbDescriptor: UsbAudioDeviceDescriptor? = null,
    val probe: FullUsbProbeResult? = null,
    val probing: Boolean = false,
    val channelStrips: List<ChannelStripState> = emptyList(),
    val appMode: AppMode = AppMode.MULTITRACK_RECORD,
    val isRecording: Boolean = false,
    val isMonitoring: Boolean = false,
    val isVuMetering: Boolean = false,
    val isUsbDegraded: Boolean = false,
    val isVirtualMicActive: Boolean = false,
    val isPlaying: Boolean = false,
    val transportState: TransportState = TransportState.IDLE,
    val statusMessage: String? = null,
    val warningMessage: String? = null,
    val lastRecordingPath: String? = null,
    val monitorOutputDeviceId: Int = -1,
    val captureChannelCount: Int = 0,
    val playbackChannelCount: Int = 0,
    val recordElapsedSec: Float = 0f,
    val recordStartedAtEpochMs: Long = 0L,
    val recordViewStartSec: Float = 0f,
    val recordViewWindowSec: Float = 0f,
    val recordViewFollowPlayhead: Boolean = true,
    val storageFreeBytes: Long = 0L,
    val storageRecordEstimateSec: Float = 0f,
    val waveformPeaks: Map<Int, LiveWaveformSnapshot> = emptyMap(),
    val captureMeterLevels: Map<Int, Float> = emptyMap(),
    val soundcheckSessions: List<SoundcheckSessionItem> = emptyList(),
    val selectedSoundcheckDir: String? = null,
    val playbackPositionSec: Float = 0f,
    val playbackDurationSec: Float = 0f,
    val soundcheckSampleRate: Int = 48_000,
    val soundcheckWaveforms: SessionWaveformOverview? = null,
    val soundcheckWaveformsLoading: Boolean = false,
    val soundcheckWaveformProgress: Float = 0f,
    val soundcheckWaveformChannelsLoaded: Int = 0,
    val soundcheckWaveformChannelsTotal: Int = 0,
    val soundcheckViewStartSec: Float = 0f,
    val soundcheckViewWindowSec: Float = 180f,
    val soundcheckLoopStartSec: Float? = null,
    val soundcheckLoopEndSec: Float? = null,
    val soundcheckLoopEnabled: Boolean = false,
    val soundcheckLoopSelecting: Boolean = false,
    val soundcheckMeterLevels: Map<Int, Float> = emptyMap(),
    val trackmarks: List<SessionTrackmark> = emptyList(),
)

class MixerSessionController(
    val mixerId: String,
    private val appContext: Context,
    private val enumerator: UsbAudioEnumerator,
    private val settings: AppSettingsStore,
    private val isActiveMixer: () -> Boolean = { true },
    private val requestVuMeterSync: () -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureEngine = CaptureSessionEngine(mixerId)
    private val player = SessionPlayer()
    private val captureMutex = Mutex()
    private var usbStream: UsbAudioStreamHandle? = null
    private var activeProbe: FullUsbProbeResult? = null
    private var activeUsbDevice: UsbDevice? = null
    private var activeDescriptor: UsbAudioDeviceDescriptor? = null
    private var loopbackPlaybackId: Int? = null
    private var usbDetachJob: Job? = null
    private var waveformJob: Job? = null
    private var soundcheckWaveformJob: Job? = null
    private var playbackStatusJob: Job? = null
    private var profile: MixerProfile? = null
    private var lastVuLogNs = 0L
    private var lastMediaProgressSec = -1
    private var recordingWakeLock: PowerManager.WakeLock? = null
    private var routingConfig: MixerRoutingConfig = MixerRoutingConfig()
    private var cachedSoundcheckDir: String? = null
    private var cachedSoundcheckMetadata: SessionMetadata? = null

    private val storageResolver = RecordingStorageResolver(appContext, settings)

    private val storageRoot: File
        get() = storageResolver.defaultRecordingRoot()

    private val _state = MutableStateFlow(MixerSessionUiState(mixerId = mixerId))
    val state: StateFlow<MixerSessionUiState> = _state.asStateFlow()

    fun setProfile(mixer: MixerProfile) {
        profile = mixer
        _state.update { it.copy(mixerProfile = mixer) }
    }

    fun setRouting(config: MixerRoutingConfig) {
        routingConfig = config
        val s = _state.value
        if (s.isPlaying && s.appMode.isPlaybackMode) {
            scope.launch {
                captureMutex.withLock {
                    if (_state.value.isPlaying) restartSoundcheckPlaybackLocked()
                }
            }
        }
    }

    fun setProbeResult(descriptor: UsbAudioDeviceDescriptor, probe: FullUsbProbeResult) {
        activeDescriptor = descriptor
        activeProbe = probe
        val playbackCh = maxPlaybackChannelsFromProbe(probe)
        if (_state.value.isRecording) {
            _state.update {
                it.copy(
                    usbDescriptor = descriptor,
                    probe = probe,
                    probing = false,
                    playbackChannelCount = playbackCh,
                )
            }
            return
        }
        val chCount = probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
            ?: probe.input?.takeIf { it.isSuccess }?.channelCount
            ?: 0
        val strips = if (_state.value.channelStrips.isEmpty() && chCount > 0) {
            (0 until chCount).map { ChannelStripState(index = it) }
        } else {
            _state.value.channelStrips
        }
        _state.update {
            it.copy(
                usbDescriptor = descriptor,
                probe = probe,
                probing = false,
                captureChannelCount = chCount,
                playbackChannelCount = playbackCh,
                channelStrips = strips,
                statusMessage = "Ready — $chCount channels",
            )
        }
        syncVuMeterCapture()
        ensureLiveCaptureUiUpdates()
        refreshStorageEstimate()
        if (_state.value.appMode.isPlaybackMode) {
            scope.launch {
                captureMutex.withLock {
                    if (_state.value.appMode.isPlaybackMode) {
                        warmPlaybackRouteLocked()
                    }
                }
            }
        }
    }

    fun setProbing(probing: Boolean) {
        _state.update { it.copy(probing = probing) }
    }

    fun setAppMode(mode: AppMode) {
        if (_state.value.appMode == mode) return
        TransportTraceHub.mark(mixerId, "setAppMode $mode")
        if (mode.isPlaybackMode && _state.value.isRecording) {
            scope.launch { stopRecording(restoreRouting = false) }
        }
        if (mode == AppMode.MULTITRACK_RECORD) {
            scope.launch {
                captureMutex.withLock {
                    if (_state.value.appMode.isPlaybackMode) {
                        stopPlaybackStatusUpdates()
                        _state.update {
                            it.copy(
                                isPlaying = false,
                                transportState = TransportState.IDLE,
                                playbackPositionSec = 0f,
                                soundcheckMeterLevels = emptyMap(),
                                waveformPeaks = emptyMap(),
                                recordElapsedSec = 0f,
                            )
                        }
                        withContext(Dispatchers.IO) {
                            player.stopAndAwait()
                            if (isFlow8Active()) {
                                prepareFlow8UsbForCaptureLocked()
                            } else {
                                AudioEngineRouter.stopPlayback()
                                if (captureEngine.isCaptureActive && !_state.value.isRecording) {
                                    captureEngine.stopCapture()
                                }
                                AudioEngineRouter.stopAllRecording()
                                usbStream?.close()
                                usbStream = null
                            }
                            captureEngine.resetLiveWaveformBuffers()
                        }
                    }
                    _state.update { it.copy(appMode = mode) }
                    refreshStorageEstimate()
                    syncVuMeterCaptureWhileLocked()
                }
            }
            return
        }
        _state.update { it.copy(appMode = mode) }
        if (mode.isPlaybackMode) {
            captureEngine.updateVuMetering(false)
            _state.update {
                it.copy(
                    isVuMetering = false,
                    waveformPeaks = emptyMap(),
                    recordElapsedSec = 0f,
                )
            }
            refreshSoundcheckLibrary()
            scope.launch {
                captureMutex.withLock {
                    withContext(Dispatchers.IO) {
                        if (isFlow8Active()) {
                            prepareFlow8UsbForPlaybackLocked()
                        } else {
                            prepareUsbForPlaybackLocked()
                        }
                    }
                    warmPlaybackRouteLocked()
                }
            }
            _state.update {
                it.copy(
                    soundcheckViewWindowSec = org.openmultitrack.app.ui.daw.SoundcheckViewLayout.clampWindow(
                        settings.playbackWaveformWindowSec,
                        it.playbackDurationSec,
                    ),
                )
            }
        } else {
            syncVuMeterCapture()
        }
    }

    fun refreshSoundcheckLibrary() {
        val prof = profile ?: return
        TransportTraceHub.mark(mixerId, "refreshSoundcheckLibrary")
        scope.launch {
            val sessions = withContext(Dispatchers.IO) {
                SessionLibrary.listCompletedSessionsFromRoots(
                    storageResolver.allLibraryRoots(),
                    prof.storageFolderName(),
                    prof.id,
                )
            }.map { summary ->
                SoundcheckSessionItem(
                    sessionDir = summary.dir.absolutePath,
                    title = summary.displayTitle,
                    startedAtEpochMs = summary.metadata.startedAtEpochMs,
                    durationSec = summary.durationSec,
                    channelCount = summary.channelCount,
                )
            }
            TransportTraceHub.mark(mixerId, "refreshSoundcheckLibrary listed ${sessions.size} sessions")
            _state.update { s ->
                val selected = s.selectedSoundcheckDir?.takeIf { dir ->
                    sessions.any { it.sessionDir == dir }
                } ?: sessions.firstOrNull()?.sessionDir
                s.copy(
                    soundcheckSessions = sessions,
                    selectedSoundcheckDir = selected,
                    playbackDurationSec = sessions.firstOrNull { it.sessionDir == selected }?.durationSec ?: 0f,
                )
            }
            _state.value.selectedSoundcheckDir?.let { loadSoundcheckWaveforms(it) }
        }
    }

    fun selectSoundcheckSession(sessionDir: String) {
        val item = _state.value.soundcheckSessions.firstOrNull { it.sessionDir == sessionDir }
        TransportTraceHub.mark(mixerId, "selectSoundcheckSession ${File(sessionDir).name}")
        scope.launch {
            captureMutex.withLock {
                TransportTraceHub.mark(mixerId, "selectSoundcheck stop prior playback")
                stopSoundcheckLocked()
                soundcheckWaveformJob?.cancel()
            }
            val dir = File(sessionDir)
            TransportTraceHub.mark(mixerId, "selectSoundcheck read metadata")
            val metadata = withContext(Dispatchers.IO) {
                SessionMetadata.read(dir)?.withResolvedChannels(dir)
            } ?: run {
                TransportTraceHub.finish(mixerId, "selectSoundcheck failed: no metadata")
                return@launch
            }
            val durationSec = item?.durationSec?.takeIf { it > 0f }
                ?: withContext(Dispatchers.IO) { SessionPlaybackDuration.durationSec(dir, metadata) }
            TransportTraceHub.mark(mixerId, "selectSoundcheck prepare UI duration=${durationSec}s ch=${metadata.channels.size}")
            prepareSoundcheckSessionUi(sessionDir, metadata, durationSec)
            TransportTraceHub.mark(mixerId, "selectSoundcheck UI ready (waveforms loading async)")
            loadSoundcheckWaveformsBackground(dir, metadata)
            if (_state.value.appMode.isPlaybackMode) {
                TransportTraceHub.mark(mixerId, "selectSoundcheck warmPlaybackRoute")
                captureMutex.withLock { warmPlaybackRouteLocked() }
            }
            TransportTraceHub.finish(mixerId, "soundcheck session loaded")
        }
    }

    private suspend fun warmPlaybackRouteLocked() {
        if (!_state.value.appMode.isPlaybackMode) return
        val trace = TransportTrace("warmPlaybackRoute")
        val descriptor = activeDescriptor ?: return
        val probe = activeProbe ?: return
        val usbOutputs = maxPlaybackChannelsFromProbe(probe).coerceAtLeast(1)
        val route = runCatching { ensurePlaybackLocked(descriptor, probe, usbOutputs).getOrThrow() }
            .onFailure { e ->
                trace.mark("ensurePlayback failed: ${e.message}")
                OmtLog.d("MixerSession", "playback route warmup: ${e.message}")
            }
            .getOrNull() ?: return
        trace.mark("usbStream open fd=${usbStream?.fd} backend=${route.backend}")
        if (route.backend == org.openmultitrack.usb.AudioBackend.UAC2) {
            AudioEngineRouter.preclaimPlaybackRoute(route, activeUsbDevice)
            trace.mark("UAC2 playback interface preclaimed")
        }
        trace.mark("done")
    }

    fun renameSoundcheckSession(sessionDir: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val dir = File(sessionDir)
            val updated = withContext(Dispatchers.IO) {
                val metadata = SessionMetadata.read(dir) ?: return@withContext false
                metadata.copy(customTitle = trimmed).writeTo(dir)
                true
            }
            if (!updated) return@launch
            refreshSoundcheckLibrary()
            if (_state.value.selectedSoundcheckDir == sessionDir) {
                selectSoundcheckSession(sessionDir)
            }
        }
    }

    fun deleteSoundcheckSession(sessionDir: String) {
        scope.launch {
            captureMutex.withLock {
                if (_state.value.selectedSoundcheckDir == sessionDir) {
                    stopSoundcheckLocked()
                    soundcheckWaveformJob?.cancel()
                }
            }
            withContext(Dispatchers.IO) {
                File(sessionDir).deleteRecursively()
            }
            refreshSoundcheckLibrary()
        }
    }

    fun setSoundcheckView(viewStartSec: Float, viewWindowSec: Float) {
        val duration = _state.value.playbackDurationSec
        val window = org.openmultitrack.app.ui.daw.SoundcheckViewLayout.clampWindow(viewWindowSec, duration)
        val maxStart = max(0f, duration - window)
        _state.update {
            it.copy(
                soundcheckViewStartSec = viewStartSec.coerceIn(0f, maxStart),
                soundcheckViewWindowSec = window,
            )
        }
    }

    fun panSoundcheckView(deltaSec: Float) {
        val s = _state.value
        setSoundcheckView(s.soundcheckViewStartSec + deltaSec, s.soundcheckViewWindowSec)
    }

    fun zoomSoundcheckView(scale: Float, focalSec: Float) {
        val s = _state.value
        val newWindow = org.openmultitrack.app.ui.daw.SoundcheckViewLayout.clampWindow(
            s.soundcheckViewWindowSec / scale,
            s.playbackDurationSec,
        )
        val rel = if (s.soundcheckViewWindowSec > 0f) {
            (focalSec - s.soundcheckViewStartSec) / s.soundcheckViewWindowSec
        } else {
            0.5f
        }
        val newStart = focalSec - rel * newWindow
        setSoundcheckView(newStart, newWindow)
    }

    fun setSoundcheckLoopRegion(startSec: Float, endSec: Float) {
        val duration = _state.value.playbackDurationSec
        if (duration <= 0f) return
        var start = min(startSec, endSec).coerceIn(0f, duration)
        var end = max(startSec, endSec).coerceIn(0f, duration)
        if (end <= start + 0.05f) {
            end = (start + 0.25f).coerceAtMost(duration)
            if (end <= start + 0.05f) start = (end - 0.25f).coerceAtLeast(0f)
        }
        if (end <= start + 0.05f) return
        _state.update {
            it.copy(
                soundcheckLoopStartSec = start,
                soundcheckLoopEndSec = end,
                soundcheckLoopSelecting = false,
            )
        }
    }

    fun setSoundcheckLoopEnabled(enabled: Boolean) {
        scope.launch {
            captureMutex.withLock {
                _state.update { it.copy(soundcheckLoopEnabled = enabled) }
                if (_state.value.isPlaying) {
                    restartSoundcheckPlaybackLocked()
                }
            }
        }
    }

    fun setSoundcheckLoopSelecting(selecting: Boolean) {
        _state.update { it.copy(soundcheckLoopSelecting = selecting) }
    }

    fun setSoundcheckLoopIn() {
        val s = _state.value
        val duration = s.playbackDurationSec
        if (duration <= 0f) return
        val pos = s.playbackPositionSec.coerceIn(0f, duration)
        val end = s.soundcheckLoopEndSec ?: duration
        setSoundcheckLoopRegion(pos, max(end, pos + 0.1f).coerceAtMost(duration))
    }

    fun setSoundcheckLoopOut() {
        val s = _state.value
        val duration = s.playbackDurationSec
        if (duration <= 0f) return
        val pos = s.playbackPositionSec.coerceIn(0f, duration)
        val start = s.soundcheckLoopStartSec ?: 0f
        setSoundcheckLoopRegion(min(start, pos - 0.1f).coerceAtLeast(0f), pos)
    }

    fun toggleSoundcheckLoopButton() {
        val s = _state.value
        when {
            s.soundcheckLoopSelecting -> _state.update { it.copy(soundcheckLoopSelecting = false) }
            s.soundcheckLoopStartSec != null && s.soundcheckLoopEndSec != null ->
                setSoundcheckLoopEnabled(!s.soundcheckLoopEnabled)
            else -> _state.update { it.copy(soundcheckLoopSelecting = true) }
        }
    }

    fun playSoundcheck(startFrame: Long = 0) {
        scope.launch {
            captureMutex.withLock {
                try {
                    startSoundcheckPlaybackLocked(startFrame)
                } catch (e: Exception) {
                    OmtLog.e("MixerSession", "playSoundcheck failed", e)
                    withContext(Dispatchers.IO) { player.stopAndAwait() }
                    stopPlaybackStatusUpdates()
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            transportState = TransportState.IDLE,
                            statusMessage = e.message,
                        )
                    }
                }
            }
        }
    }

    fun stopSoundcheck(resetPosition: Boolean = true, restoreRouting: Boolean = true) {
        finishPlaybackTransport(
            resetPosition = resetPosition,
            releaseNative = isFlow8Active(),
            restoreRouting = restoreRouting,
        )
    }

    private fun stopSoundcheckHard(resetPosition: Boolean = true, restoreRouting: Boolean = true) {
        finishPlaybackTransport(
            resetPosition = resetPosition,
            releaseNative = true,
            restoreRouting = restoreRouting,
        )
    }

    fun seekSoundcheck(positionSec: Float) {
        scope.launch {
            val s = _state.value
            val duration = s.playbackDurationSec
            if (duration <= 0f) return@launch
            val clamped = positionSec.coerceIn(0f, duration)
            val sampleRate = s.soundcheckSampleRate
            if (sampleRate <= 0) return@launch
            val frame = (clamped * sampleRate).toLong()
            captureMutex.withLock {
                _state.update { it.copy(playbackPositionSec = clamped) }
                if (player.isPlaying) {
                    player.seekToFrame(frame)
                }
            }
            AudioSessionBridge.refreshPlaybackNotification(_state.value)
        }
    }

    fun seekToPreviousTrackmark() {
        val marks = _state.value.trackmarks.sortedBy { it.startSec }
        val pos = _state.value.playbackPositionSec
        val target = if (marks.isEmpty()) {
            0f
        } else {
            marks.lastOrNull { it.startSec < pos - 0.25f }?.startSec ?: marks.first().startSec
        }
        seekSoundcheck(target)
    }

    fun seekToNextTrackmark() {
        val marks = _state.value.trackmarks.sortedBy { it.startSec }
        val pos = _state.value.playbackPositionSec
        val duration = _state.value.playbackDurationSec
        val target = if (marks.isEmpty()) {
            duration.coerceAtLeast(0f)
        } else {
            marks.firstOrNull { it.startSec > pos + 0.25f }?.startSec ?: marks.last().startSec
        }
        seekSoundcheck(target)
    }

    fun addTrackmark(title: String, startSec: Float) {
        val sessionDir = _state.value.selectedSoundcheckDir ?: return
        val duration = _state.value.playbackDurationSec
        if (duration <= 0f) return
        val clamped = startSec.coerceIn(0f, duration)
        scope.launch {
            val dir = File(sessionDir)
            val metadata = withContext(Dispatchers.IO) {
                SessionMetadata.read(dir)?.withResolvedChannels(dir)
            } ?: return@launch
            val existing = _state.value.trackmarks.toMutableList()
            val nextIndex = (existing.maxOfOrNull { it.index } ?: 0) + 1
            existing += SessionTrackmark(
                index = nextIndex,
                title = title.trim().ifBlank { "Track ${nextIndex.toString().padStart(2, '0')}" },
                startSec = clamped,
            )
            val sorted = existing.sortedBy { it.startSec }
                .mapIndexed { idx, mark -> mark.copy(index = idx + 1) }
            withContext(Dispatchers.IO) {
                SessionCueFile.write(
                    dir,
                    sorted,
                    SessionCueFile.preferredAudioFileName(dir, metadata),
                )
            }
            _state.update { it.copy(trackmarks = sorted) }
            seekSoundcheck(clamped)
        }
    }

    fun toggleSoundcheckPlayback() {
        val playing = player.isPlaying || _state.value.isPlaying
        val trace = if (playing) {
            TransportTrace("SOUNDCHECK-PAUSE")
        } else {
            TransportTraceHub.start(mixerId, "SOUNDCHECK-PLAY")
        }
        trace.mark("toggle isPlaying=$playing player=${player.isPlaying}")
        if (player.isPlaying || _state.value.isPlaying) {
            val sampleRate = _state.value.soundcheckSampleRate.takeIf { it > 0 } ?: 48_000
            val posSec = if (sampleRate > 0 && player.isPlaying) {
                player.status.positionFrames.toFloat() / sampleRate
            } else {
                _state.value.playbackPositionSec
            }
            stopPlaybackStatusUpdates()
            _state.update {
                it.copy(
                    isPlaying = false,
                    transportState = TransportState.IDLE,
                    soundcheckMeterLevels = emptyMap(),
                    playbackPositionSec = posSec,
                )
            }
            AudioSessionBridge.refreshPlaybackNotification(_state.value)
            trace.mark("UI paused, dispatching suspend to IO")
            scope.launch(Dispatchers.IO) {
                captureMutex.withLock {
                    trace.mark("captureMutex acquired for suspend")
                    stopSoundcheckLocked(skipStateUpdate = true, trace = trace)
                }
            }
        } else {
            _state.update {
                it.copy(
                    isPlaying = true,
                    transportState = TransportState.PLAYING,
                    warningMessage = null,
                )
            }
            trace.mark("UI set isPlaying=true, dispatching start to IO")
            scope.launch(Dispatchers.IO) {
                captureMutex.withLock {
                    trace.mark("captureMutex acquired for start")
                    try {
                        startSoundcheckPlaybackLockedFromCurrentPosition(trace)
                        TransportTraceHub.finish(mixerId, "playback started")
                    } catch (e: Exception) {
                        OmtLog.e("MixerSession", "toggleSoundcheckPlayback failed", e)
                        player.stopAndAwait()
                        stopPlaybackStatusUpdates()
                        _state.update {
                            it.copy(
                                isPlaying = false,
                                transportState = TransportState.IDLE,
                                statusMessage = e.message,
                            )
                        }
                        TransportTraceHub.finish(mixerId, "playback failed: ${e.message}")
                    }
                }
            }
        }
    }

    fun pauseSoundcheckPlayback() {
        val sampleRate = _state.value.soundcheckSampleRate.takeIf { it > 0 } ?: 48_000
        val posSec = if (sampleRate > 0 && player.isPlaying) {
            player.status.positionFrames.toFloat() / sampleRate
        } else {
            _state.value.playbackPositionSec
        }
        stopPlaybackStatusUpdates()
        _state.update {
            it.copy(
                isPlaying = false,
                transportState = TransportState.IDLE,
                soundcheckMeterLevels = emptyMap(),
                playbackPositionSec = posSec,
            )
        }
        AudioSessionBridge.refreshPlaybackNotification(_state.value)
        scope.launch(Dispatchers.IO) {
            captureMutex.withLock {
                if (player.isPlaying) {
                    stopSoundcheckLocked(skipStateUpdate = true)
                }
            }
        }
    }

    fun playSoundcheckPlayback() {
        val trace = TransportTraceHub.start(mixerId, "SOUNDCHECK-PLAY")
        _state.update {
            it.copy(
                isPlaying = true,
                transportState = TransportState.PLAYING,
                warningMessage = null,
            )
        }
        trace.mark("UI isPlaying=true")
        scope.launch(Dispatchers.IO) {
            captureMutex.withLock {
                trace.mark("captureMutex acquired")
                try {
                    startSoundcheckPlaybackLockedFromCurrentPosition(trace)
                    TransportTraceHub.finish(mixerId, "playback started")
                } catch (e: Exception) {
                    OmtLog.e("MixerSession", "playSoundcheckPlayback failed", e)
                    player.stopAndAwait()
                    stopPlaybackStatusUpdates()
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            transportState = TransportState.IDLE,
                            statusMessage = e.message,
                        )
                    }
                    TransportTraceHub.finish(mixerId, "playback failed: ${e.message}")
                }
            }
        }
    }

    private suspend fun startSoundcheckPlaybackLockedFromCurrentPosition(trace: TransportTrace? = null) {
        val dir = _state.value.selectedSoundcheckDir
            ?: throw IllegalStateException("No soundcheck session selected")
        trace?.mark("loading session metadata")
        val metadata = cachedSoundcheckMetadata?.takeIf { cachedSoundcheckDir == dir }
            ?: withContext(Dispatchers.IO) {
                SessionMetadata.read(File(dir))?.withResolvedChannels(File(dir))
            }
            ?: throw IllegalStateException("Could not read session metadata")
        val frame = (_state.value.playbackPositionSec * metadata.sampleRate).toLong()
        trace?.mark("metadata ready startFrame=$frame usbStream=${usbStream != null}")
        startSoundcheckPlaybackLocked(frame, trace)
    }

    private fun isFlow8Active(): Boolean =
        activeDescriptor?.let { Flow8UsbPlaybackProfile.isFlow8(it) } == true

    /** UI state can lag [CaptureSessionEngine.isRecording] briefly after transport start. */
    private fun isRecordingTransport(): Boolean =
        _state.value.isRecording || captureEngine.isRecording

    private suspend fun prepareUsbForPlaybackLocked() {
        if (isFlow8Active()) {
            prepareFlow8UsbForPlaybackLocked()
            return
        }
        if (_state.value.isMonitoring) {
            captureEngine.updateMonitor(MonitorMixConfig(enabled = false))
            _state.update { it.copy(isMonitoring = false) }
        }
        captureEngine.updateVuMetering(false)
        _state.update { it.copy(isVuMetering = false) }
        withContext(Dispatchers.IO) {
            if (player.isPlaying) {
                player.stopAndAwait()
            }
            AudioEngineRouter.stopPlayback()
            if (captureEngine.isCaptureActive && !_state.value.isRecording) {
                captureEngine.stopCapture()
            }
            AudioEngineRouter.stopAllRecording()
            usbStream?.close()
            usbStream = null
        }
    }

    /** Release FLOW 8 UAC2 playback and close the USB stream so capture/VU can reopen cleanly. */
    private suspend fun teardownFlow8UsbPlaybackLocked(trace: TransportTrace? = null) {
        captureEngine.updateVuMetering(false)
        _state.update { it.copy(isVuMetering = false) }
        withContext(Dispatchers.IO) {
            if (player.isPlaying) {
                player.stopAndAwait()
            }
            AudioEngineRouter.stopPlayback()
            if (captureEngine.isCaptureActive && !_state.value.isRecording) {
                captureEngine.stopCapture()
            }
            AudioEngineRouter.stopAllRecording()
            usbStream?.close()
            usbStream = null
            delay(Flow8UsbPlaybackProfile.POST_PLAYBACK_STOP_DELAY_MS)
        }
        trace?.mark("FLOW 8 USB playback torn down (stream closed)")
        OmtLog.i("MixerSession", "FLOW 8 USB playback torn down for $mixerId")
        if (_state.value.appMode == AppMode.MULTITRACK_RECORD && vuCaptureDesired()) {
            syncVuMeterCapture()
        }
    }

    /** Quiesce FLOW 8 playback USB before opening the capture stream (VU / record). */
    private suspend fun prepareFlow8UsbForCaptureLocked() {
        withContext(Dispatchers.IO) {
            if (player.isPlaying) {
                player.stopAndAwait()
            }
            AudioEngineRouter.stopPlayback()
            if (isRecordingTransport()) {
                OmtLog.i("MixerSession", "FLOW 8 playback stopped; keeping capture during recording")
                return@withContext
            }
            if (captureEngine.isCaptureActive &&
                captureEngine.isReceivingAudio(if (_state.value.appMode == AppMode.MULTITRACK_RECORD) 5_000 else 1_500)
            ) {
                OmtLog.i("MixerSession", "FLOW 8 capture already active — skip USB teardown")
                return@withContext
            }
            if (captureEngine.isCaptureActive && !captureEngine.isReceivingAudio(5_000)) {
                captureEngine.stopCapture()
            }
            AudioEngineRouter.stopAllRecording()
            usbStream?.close()
            usbStream = null
            delay(Flow8UsbPlaybackProfile.POST_PLAYBACK_STOP_DELAY_MS)
        }
        OmtLog.i("MixerSession", "FLOW 8 USB prepared for capture")
    }

    /** FLOW 8 needs capture fully released and a short settle delay before UAC2 playback. */
    private suspend fun prepareFlow8UsbForPlaybackLocked() {
        if (_state.value.isMonitoring) {
            captureEngine.updateMonitor(MonitorMixConfig(enabled = false))
            _state.update { it.copy(isMonitoring = false) }
        }
        captureEngine.updateVuMetering(false)
        _state.update { it.copy(isVuMetering = false) }
        withContext(Dispatchers.IO) {
            if (player.isPlaying) {
                player.stopAndAwait()
            }
            AudioEngineRouter.stopPlayback()
            if (captureEngine.isCaptureActive && !_state.value.isRecording) {
                captureEngine.stopCapture()
            }
            AudioEngineRouter.stopAllRecording()
            usbStream?.close()
            usbStream = null
            delay(Flow8UsbPlaybackProfile.PRE_PLAYBACK_DELAY_MS)
        }
        OmtLog.i("MixerSession", "FLOW 8 USB prepared for playback")
    }

    /** Stop USB capture/playback so XR18 accepts OSC routing changes (verified on hardware). */
    private suspend fun quiesceUsbBeforeRoutingLocked(keepCaptureForFlow8Record: Boolean = false) {
        val t0 = System.nanoTime()
        if (player.isPlaying) {
            player.stopAndAwait()
        }
        if (_state.value.isMonitoring) {
            captureEngine.updateMonitor(MonitorMixConfig(enabled = false))
            _state.update { it.copy(isMonitoring = false) }
        }
        captureEngine.updateVuMetering(false)
        _state.update { it.copy(isVuMetering = false) }
        val keepCapture = keepCaptureForFlow8Record && isFlow8Active()
        if (captureEngine.isCaptureActive && !isRecordingTransport() && !keepCapture) {
            captureEngine.stopCapture()
        }
        org.openmultitrack.mixer.behringer.Xr18RoutingLog.info(
            "quiesceUsb ${(System.nanoTime() - t0) / 1_000_000}ms keepCapture=$keepCapture",
        )
    }

    private suspend fun startSoundcheckPlaybackLocked(startFrame: Long, trace: TransportTrace? = null) {
        val descriptor = activeDescriptor
            ?: throw IllegalStateException("Mixer not connected — reconnect USB")
        val probe = activeProbe
            ?: throw IllegalStateException("Mixer not ready — wait for probe to finish")
        trace?.mark("startSoundcheckPlaybackLocked startFrame=$startFrame")
        val sessionDir = _state.value.selectedSoundcheckDir
            ?: throw IllegalStateException("No soundcheck session selected")
        val dir = File(sessionDir)
        val metadata = cachedSoundcheckMetadata?.takeIf { cachedSoundcheckDir == sessionDir }
            ?: withContext(Dispatchers.IO) {
                SessionMetadata.read(dir)?.withResolvedChannels(dir)
            }
            ?: throw IllegalStateException("Could not read session metadata")
        val cachedDurationSec = _state.value.playbackDurationSec
            .takeIf { it > 0f && _state.value.selectedSoundcheckDir == sessionDir }
        val durationSec = cachedDurationSec
            ?: withContext(Dispatchers.IO) { SessionPlaybackDuration.durationSec(dir, metadata) }
        val durationFrames = (durationSec * metadata.sampleRate.coerceAtLeast(1)).toLong()
        if (durationFrames <= 0L) {
            throw IllegalStateException("Session has no playable audio on disk")
        }
        val clampedStart = startFrame.coerceIn(0L, (durationFrames - 1).coerceAtLeast(0L))
        withContext(Dispatchers.IO) {
            if (isFlow8Active()) {
                trace?.mark("preparing FLOW 8 USB for playback")
                prepareFlow8UsbForPlaybackLocked()
            } else {
                trace?.mark("preparing USB for playback")
                prepareUsbForPlaybackLocked()
                trace?.mark("USB prepared for playback")
            }
            if (player.isPlaying) {
                trace?.mark("player still playing, stopping first")
                if (isFlow8Active()) {
                    player.stopAndAwait()
                } else {
                    player.suspendAndAwait()
                }
            }
            trace?.mark("soundcheck routing before playback USB route")
            when (val routing = routingBeforeSoundcheckLocked(dir, metadata)) {
                RoutingHookResult.Cancelled ->
                    throw IllegalStateException("Soundcheck routing cancelled")
                is RoutingHookResult.Failed ->
                    throw IllegalStateException(routing.message)
                else -> Unit
            }
            val ui = _state.value
            val usbOutputs = maxPlaybackChannelsFromProbe(probe).coerceAtLeast(1)
            trace?.mark("ensurePlaybackRoute usbStream=${usbStream != null} fd=${usbStream?.fd}")
            val route = ensurePlaybackLocked(descriptor, probe, usbOutputs).getOrThrow()
            trace?.mark("route resolved backend=${route.backend}")
            when (val routingRetry = routingAfterSoundcheckPlaybackStartedLocked()) {
                RoutingHookResult.Cancelled ->
                    throw IllegalStateException("Soundcheck routing cancelled")
                is RoutingHookResult.Failed ->
                    throw IllegalStateException(routingRetry.message)
                else -> Unit
            }
            trace?.mark("soundcheck routing confirmed with USB route open")
            val mixContext = PlaybackMixContext(
                appMode = ui.appMode,
                sessionChannelCount = metadata.channels.size,
                usbOutputCount = usbOutputs,
                routing = routingConfig,
                strips = ui.channelStrips,
            )
            val loopStart = ui.soundcheckLoopStartSec?.takeIf { ui.soundcheckLoopEnabled }
                ?.let { (it * metadata.sampleRate).toLong() }
            val loopEnd = ui.soundcheckLoopEndSec?.takeIf { ui.soundcheckLoopEnabled }
                ?.let { (it * metadata.sampleRate).toLong() }
            trace?.mark("calling player.playSession")
            player.playSession(
                scope = scope,
                sessionDir = dir,
                metadata = metadata,
                route = route,
                usbDevice = activeUsbDevice,
                startFrame = clampedStart,
                loopStartFrame = loopStart,
                loopEndFrame = loopEnd,
                loopEnabled = ui.soundcheckLoopEnabled,
                mixContext = mixContext,
            ).getOrThrow()
            trace?.mark("player.playSession returned")
        }
        val statusMessage = when (_state.value.appMode) {
            AppMode.SIMPLE_PLAY -> "Playing stereo mix to USB 1+2"
            else -> "Playing to USB returns"
        }
        _state.update {
            it.copy(
                isPlaying = true,
                transportState = TransportState.PLAYING,
                statusMessage = statusMessage,
                warningMessage = null,
                playbackChannelCount = maxPlaybackChannelsFromProbe(probe),
                playbackPositionSec = clampedStart.toFloat() / metadata.sampleRate,
                playbackDurationSec = durationSec,
            )
        }
        trace?.mark("starting playback status updates")
        startPlaybackStatusUpdates(metadata.sampleRate, trace)
        AudioSessionBridge.rebuildNotification()
        trace?.mark("startSoundcheckPlaybackLocked complete")
    }

    private suspend fun restartSoundcheckPlaybackLocked() {
        val sessionDir = _state.value.selectedSoundcheckDir ?: return
        val metadata = SessionMetadata.read(File(sessionDir)) ?: return
        val frame = (_state.value.playbackPositionSec * metadata.sampleRate).toLong()
        startSoundcheckPlaybackLocked(frame)
    }

    private suspend fun stopSoundcheckLocked(
        skipStateUpdate: Boolean = false,
        releaseNative: Boolean = false,
        restoreRouting: Boolean = true,
        trace: TransportTrace? = null,
    ) {
        val flow8 = isFlow8Active()
        val hardStop = releaseNative || flow8
        trace?.mark("stopSoundcheckLocked releaseNative=$hardStop flow8=$flow8")
        val sampleRate = _state.value.soundcheckSampleRate.takeIf { it > 0 } ?: 48_000
        val posSec = if (sampleRate > 0 && player.isPlaying) {
            player.status.positionFrames.toFloat() / sampleRate
        } else {
            _state.value.playbackPositionSec
        }
        if (!skipStateUpdate) {
            stopPlaybackStatusUpdates()
        }
        if (hardStop) {
            player.stopAndAwait()
            trace?.mark("hard stop complete")
        } else {
            player.suspendAndAwait()
            trace?.mark("suspend complete (native engine kept warm)")
        }
        if (flow8) {
            teardownFlow8UsbPlaybackLocked(trace)
        }
        if (restoreRouting) {
            quiesceUsbBeforeRoutingLocked()
            RoutingAutomationBridge.hooks?.afterSoundcheckRestore()
        }
        if (!skipStateUpdate) {
            _state.update {
                it.copy(
                    isPlaying = false,
                    transportState = TransportState.IDLE,
                    playbackPositionSec = posSec,
                    soundcheckMeterLevels = emptyMap(),
                )
            }
        }
    }

    fun applyScribbleLabels(labels: List<UsbChannelScribble>) {
        val byIndex = labels.associateBy { it.stripIndex }
        _state.update { s ->
            s.copy(
                channelStrips = s.channelStrips.map { strip ->
                    val scribble = byIndex[strip.index] ?: return@map strip
                    val raw = scribble.name?.takeIf { it.isNotBlank() } ?: return@map strip
                    val parsed = ScribbleStripLabel.parse(raw)
                    strip.copy(
                        label = raw,
                        displayName = parsed.displayName,
                        iconId = scribble.iconId ?: parsed.iconId,
                        colorArgb = scribble.colorArgb ?: strip.colorArgb,
                    )
                },
            )
        }
    }

    fun updateChannelStrip(index: Int, transform: (ChannelStripState) -> ChannelStripState) {
        _state.update { s ->
            s.copy(
                channelStrips = s.channelStrips.map { if (it.index == index) transform(it) else it },
            )
        }
        applyMonitorRouting()
        val s = _state.value
        if (s.isPlaying && s.appMode.isPlaybackMode) {
            scope.launch {
                captureMutex.withLock {
                    if (_state.value.isPlaying) restartSoundcheckPlaybackLocked()
                }
            }
        }
    }

    fun setMonitorOutputDevice(deviceId: Int) {
        _state.update { it.copy(monitorOutputDeviceId = deviceId) }
        applyMonitorRouting()
    }

    fun setMonitorGain(gain: Float) {
        settings.monitorGainLinear = gain
        applyMonitorRouting()
    }

    private fun recordWaveformHistorySec(): Float =
        settings.recordWaveformHistorySec.coerceIn(
            org.openmultitrack.app.ui.daw.RecordViewLayout.MIN_HISTORY_SEC,
            org.openmultitrack.app.ui.daw.RecordViewLayout.MAX_HISTORY_SEC,
        )

    private fun recordWaveformDisplayWindowSec(): Float =
        settings.recordWaveformWindowSec.coerceIn(5f, 120f)

    fun updateWaveformConfig() {
        val history = recordWaveformHistorySec()
        captureEngine.setWaveformConfig(
            windowSec = history,
            peaksPerSecond = CaptureSessionEngine.peaksPerSecondForWindow(history),
        )
    }

    fun setRecordView(viewStartSec: Float, viewWindowSec: Float) {
        setRecordViewWindow(viewWindowSec)
    }

    fun setRecordViewWindow(viewWindowSec: Float) {
        val history = recordWaveformHistorySec()
        val elapsed = _state.value.recordElapsedSec
        val (start, window) = org.openmultitrack.app.ui.daw.RecordViewLayout.layout(
            elapsedSec = elapsed,
            viewWindowSec = viewWindowSec,
            historySec = history,
        )
        _state.update {
            it.copy(
                recordViewStartSec = start,
                recordViewWindowSec = window,
                recordViewFollowPlayhead = true,
            )
        }
    }

    fun zoomRecordView(scale: Float, focalSec: Float) {
        val s = _state.value
        val history = recordWaveformHistorySec()
        val currentWindow = effectiveRecordViewWindow(s)
        val newWindow = org.openmultitrack.app.ui.daw.RecordViewLayout.clampWindow(
            currentWindow / scale,
            history,
        )
        setRecordViewWindow(newWindow)
    }

    fun panRecordView(deltaSec: Float) {
        // Horizontal pan is disabled during recording — viewport stays left-anchored or follows the live edge.
    }

    private fun effectiveRecordViewWindow(s: MixerSessionUiState): Float {
        val history = recordWaveformHistorySec()
        val displayDefault = recordWaveformDisplayWindowSec()
        val window = s.recordViewWindowSec.takeIf { it > 0f } ?: displayDefault
        return org.openmultitrack.app.ui.daw.RecordViewLayout.clampWindow(window, history)
    }

    private fun withRecordViewFollowPlayhead(s: MixerSessionUiState, elapsedSec: Float): MixerSessionUiState {
        if (!s.recordViewFollowPlayhead) return s
        val window = effectiveRecordViewWindow(s)
        val start = org.openmultitrack.app.ui.daw.RecordViewLayout.anchoredStartSec(elapsedSec, window)
        return s.copy(recordViewStartSec = start)
    }

    private fun resetRecordViewAfterStop() {
        val bufferWindow = recordWaveformDisplayWindowSec()
        _state.update {
            it.copy(
                recordViewStartSec = 0f,
                recordViewWindowSec = bufferWindow,
                recordViewFollowPlayhead = true,
            )
        }
    }

    fun startMonitoring() {
        val descriptor = activeDescriptor ?: return
        val probe = activeProbe ?: return
        scope.launch {
            captureMutex.withLock {
                withContext(Dispatchers.IO) {
                    ensureCapture(descriptor, probe).getOrThrow()
                }
                _state.update {
                    it.copy(
                        isMonitoring = true,
                        waveformPeaks = emptyMap(),
                        recordElapsedSec = 0f,
                        statusMessage = "Monitoring",
                        warningMessage = null,
                    )
                }
                applyMonitorRouting(enabled = true)
                startCaptureUiUpdates()
            }
        }
    }

    fun stopMonitoring() {
        captureEngine.updateMonitor(MonitorMixConfig(enabled = false))
        _state.update { it.copy(isMonitoring = false, statusMessage = "Monitor off") }
        syncVuMeterCapture()
        AudioSessionBridge.rebuildNotification()
    }

    fun canStartRecording(): Boolean {
        val prof = profile ?: return false
        if (VirtualMixer.isDemoMixer(prof)) {
            return activeProbe != null
        }
        return activeDescriptor != null && activeProbe != null
    }

    fun attachVirtualDemoProbe() {
        val descriptor = VirtualMixerProbe.usbDescriptor()
        val probe = VirtualMixerProbe.demoProbeResult()
        val chCount = VirtualMixer.DEMO_CHANNEL_COUNT
        val strips = DemoBandChannels.channelStripStates()
        activeDescriptor = descriptor
        activeProbe = probe
        _state.update {
            it.copy(
                usbDescriptor = descriptor,
                probe = probe,
                probing = false,
                captureChannelCount = chCount,
                playbackChannelCount = maxPlaybackChannelsFromProbe(probe),
                channelStrips = strips,
                statusMessage = "Ready — Demo band ($chCount channels)",
            )
        }
        syncVuMeterCapture()
        refreshStorageEstimate()
    }

    fun startRecording() {
        val descriptor = activeDescriptor ?: return
        val probe = activeProbe ?: return
        val prof = profile ?: return
        if (TransportTraceHub.trace(mixerId) == null) {
            TransportTraceHub.start(mixerId, "RECORD-START")
        }
        TransportTraceHub.mark(mixerId, "session.startRecording")
        scope.launch {
            try {
                var recordingStarted = false
                TransportTraceHub.mark(mixerId, "waiting captureMutex")
                captureMutex.withLock {
                    TransportTraceHub.mark(mixerId, "captureMutex acquired")
                    withContext(Dispatchers.IO) {
                        TransportTraceHub.mark(mixerId, "quiesceUsb")
                        quiesceUsbBeforeRoutingLocked(keepCaptureForFlow8Record = true)
                        TransportTraceHub.mark(mixerId, "routing.beforeRecord")
                        when (val routing = routingBeforeRecordLocked()) {
                            RoutingHookResult.Cancelled -> {
                                TransportTraceHub.finish(mixerId, "cancelled at routing")
                                return@withContext
                            }
                            is RoutingHookResult.Failed -> {
                                TransportTraceHub.finish(mixerId, "routing failed")
                                _state.update { it.copy(warningMessage = routing.message) }
                                return@withContext
                            }
                            else -> TransportTraceHub.mark(mixerId, "routing.beforeRecord → $routing")
                        }
                        if (!captureEngine.isCaptureActive) {
                            TransportTraceHub.mark(mixerId, "ensureCapture")
                            ensureCapture(descriptor, probe).getOrThrow()
                            TransportTraceHub.mark(mixerId, "ensureCapture ok ch=${captureEngine.activeChannelCount}")
                        } else {
                            TransportTraceHub.mark(mixerId, "capture already active")
                            syncChannelStripsToCaptureCount(captureEngine.activeChannelCount)
                        }
                        val writePlan = org.openmultitrack.app.data.RecordingWritePlan.create(
                            storageResolver,
                            settings,
                            prof.storageFolderName(),
                        )
                        TransportTraceHub.mark(mixerId, "captureEngine.startRecording")
                        captureEngine.resetLiveWaveformBuffers()
                        captureEngine.startRecording(
                            CaptureSessionEngine.RecordingConfig(
                                mixerId = prof.id,
                                mixerFolderName = prof.storageFolderName(),
                                storageRoot = storageRoot,
                                channelStrips = _state.value.channelStrips,
                                writePlan = writePlan,
                            ),
                        ).getOrThrow()
                        TransportTraceHub.mark(mixerId, "captureEngine.startRecording ok")
                        recordingStarted = true
                    }
                }
                if (!recordingStarted) return@launch
                val sessionDir = captureEngine.activeSessionDir
                if (sessionDir != null) {
                    settings.setActiveRecording(prof.id, sessionDir.absolutePath)
                }
                acquireRecordingWakeLock()
                val bufferWindow = recordWaveformDisplayWindowSec()
                val startedAt = captureEngine.recordingStartedAtEpochMs() ?: System.currentTimeMillis()
                lastMediaProgressSec = -1
                _state.update {
                    it.copy(
                        isRecording = true,
                        transportState = TransportState.RECORDING,
                        statusMessage = "Recording…",
                        warningMessage = null,
                        lastRecordingPath = sessionDir?.absolutePath ?: it.lastRecordingPath,
                        recordViewStartSec = 0f,
                        recordViewWindowSec = org.openmultitrack.app.ui.daw.RecordViewLayout.clampWindow(
                            bufferWindow,
                            recordWaveformHistorySec(),
                        ),
                        recordViewFollowPlayhead = true,
                        recordStartedAtEpochMs = startedAt,
                    )
                }
                startCaptureUiUpdates()
                AudioSessionBridge.rebuildNotification()
                TransportTraceHub.mark(mixerId, "UI isRecording=true")
                TransportTraceHub.finish(mixerId, "recording active dir=${sessionDir?.name}")
            } catch (e: Exception) {
                OmtLog.e("MixerSession", "startRecording failed", e)
                TransportTraceHub.finish(mixerId, "failed: ${e.message}")
                _state.update { it.copy(statusMessage = e.message) }
            }
        }
    }

    fun resumeRecording(sessionDir: File, scribbleLabels: List<UsbChannelScribble>? = null) {
        val descriptor = activeDescriptor ?: return
        val probe = activeProbe ?: return
        val meta = SessionMetadata.read(sessionDir) ?: return
        scope.launch {
            try {
                captureMutex.withLock {
                    withContext(Dispatchers.IO) {
                        restoreChannelStripsFromMetadata(meta)
                        if (!captureEngine.isCaptureActive) {
                            ensureCapture(descriptor, probe).getOrThrow()
                        } else {
                            syncChannelStripsToCaptureCount(captureEngine.activeChannelCount)
                        }
                        if (!scribbleLabels.isNullOrEmpty()) {
                            applyScribbleLabels(scribbleLabels)
                        }
                        captureEngine.resumeRecording(sessionDir).getOrThrow()
                    }
                }
                settings.setActiveRecording(
                    meta.mixerId.takeIf { it.isNotBlank() } ?: profile?.id ?: mixerId,
                    sessionDir.absolutePath,
                )
                acquireRecordingWakeLock()
                val startedAt = captureEngine.recordingStartedAtEpochMs() ?: meta.startedAtEpochMs
                lastMediaProgressSec = -1
                _state.update {
                    it.copy(
                        isRecording = true,
                        transportState = TransportState.RECORDING,
                        statusMessage = "Recording resumed",
                        warningMessage = null,
                        lastRecordingPath = sessionDir.absolutePath,
                        recordStartedAtEpochMs = startedAt,
                    )
                }
                startCaptureUiUpdates()
                AudioSessionBridge.rebuildNotification()
            } catch (e: Exception) {
                OmtLog.e("MixerSession", "resumeRecording failed", e)
                _state.update { it.copy(statusMessage = e.message) }
            }
        }
    }

    fun finalizeIncompleteRecording(sessionDir: File) {
        scope.launch {
            withContext(Dispatchers.IO) {
                SessionMetadata.read(sessionDir)?.let { meta ->
                    meta.channels.forEach { ch ->
                        val file = File(sessionDir, ch.fileName)
                        if (file.exists() && file.length() > 44) {
                            WavWriter.openForAppend(file, 1, meta.sampleRate).close()
                        }
                    }
                    meta.markComplete(sessionDir)
                }
            }
            if (settings.activeRecordingSessionDir == sessionDir.absolutePath) {
                settings.clearActiveRecording()
            }
            _state.update {
                it.copy(
                    statusMessage = "Incomplete session finalized",
                    warningMessage = null,
                )
            }
        }
    }

    fun pauseRecording() {
        scope.launch {
            val session = captureMutex.withLock {
                withContext(Dispatchers.IO) { captureEngine.pauseRecording() }
            }
            releaseRecordingWakeLock()
            lastMediaProgressSec = -1
            _state.update {
                it.copy(
                    isRecording = false,
                    transportState = TransportState.IDLE,
                    lastRecordingPath = session?.filePath ?: it.lastRecordingPath,
                    statusMessage = "Recording paused",
                    recordStartedAtEpochMs = 0L,
                    waveformPeaks = emptyMap(),
                    recordElapsedSec = 0f,
                )
            }
            resetRecordViewAfterStop()
            syncVuMeterCapture()
            AudioSessionBridge.rebuildNotification()
        }
    }

    fun stopRecording(restoreRouting: Boolean = true) {
        if (TransportTraceHub.trace(mixerId) == null) {
            TransportTraceHub.start(mixerId, "RECORD-STOP")
        }
        TransportTraceHub.mark(mixerId, "stopRecording restoreRouting=$restoreRouting")
        scope.launch {
            val session = captureMutex.withLock {
                TransportTraceHub.mark(mixerId, "captureMutex acquired for stop")
                withContext(Dispatchers.IO) {
                    TransportTraceHub.mark(mixerId, "captureEngine.stopRecording")
                    val ended = captureEngine.stopRecording()
                    TransportTraceHub.mark(mixerId, "captureEngine.stopRecording done path=${ended?.filePath}")
                    if (restoreRouting) {
                        TransportTraceHub.mark(mixerId, "routing.afterRecordRestore")
                        quiesceUsbBeforeRoutingLocked()
                        RoutingAutomationBridge.hooks?.afterRecordRestore()
                        TransportTraceHub.mark(mixerId, "routing.afterRecordRestore done")
                    }
                    ended
                }
            }
            settings.clearActiveRecording()
            releaseRecordingWakeLock()
            lastMediaProgressSec = -1
            TransportTraceHub.mark(mixerId, "UI isRecording=false")
            _state.update {
                it.copy(
                    isRecording = false,
                    transportState = TransportState.IDLE,
                    lastRecordingPath = session?.filePath ?: it.lastRecordingPath,
                    statusMessage = session?.let { s -> "Saved → ${s.filePath}" } ?: "Stopped",
                    recordStartedAtEpochMs = 0L,
                    waveformPeaks = emptyMap(),
                    recordElapsedSec = 0f,
                )
            }
            resetRecordViewAfterStop()
            syncVuMeterCapture()
            if (_state.value.appMode.isPlaybackMode) {
                TransportTraceHub.mark(mixerId, "refreshSoundcheckLibrary (playback mode)")
                refreshSoundcheckLibrary()
            }
            AudioSessionBridge.rebuildNotification()
            TransportTraceHub.mark(mixerId, "stopRecording session layer done")
        }
    }

    fun syncVuMeterCapture() {
        requestVuMeterSync()
    }

    suspend fun syncVuMeterCaptureLocked() {
        if (_state.value.appMode != AppMode.MULTITRACK_RECORD) {
            captureEngine.updateVuMetering(false)
            _state.update { it.copy(isVuMetering = false) }
            return
        }
        captureMutex.withLock {
            syncVuMeterCaptureWhileLocked()
        }
    }

    /** Caller must hold [captureMutex]. */
    private suspend fun syncVuMeterCaptureWhileLocked() {
        val want = vuCaptureDesired()
        captureEngine.updateVuMetering(want)
        if (want) {
            val descriptor = activeDescriptor ?: return
            val probe = activeProbe ?: return
            val recordMode = _state.value.appMode == AppMode.MULTITRACK_RECORD
            val receiveWindowMs = when {
                isRecordingTransport() -> 5_000L
                recordMode -> 5_000L
                else -> 1_500L
            }
            withContext(Dispatchers.IO) {
                if (isRecordingTransport() && captureEngine.isCaptureActive) {
                    return@withContext
                }
                val needsCapture = !captureEngine.isCaptureActive ||
                    (!captureEngine.isSyntheticCapture() && !captureEngine.isNativeCaptureOwner()) ||
                    (captureEngine.isCaptureActive && !captureEngine.isReceivingAudio(receiveWindowMs))
                if (needsCapture) {
                    if (captureEngine.isCaptureActive &&
                        !captureEngine.isReceivingAudio(receiveWindowMs) &&
                        !isRecordingTransport() &&
                        !recordMode
                    ) {
                        OmtLog.w("VuMeter", "VU capture stalled for $mixerId — restarting")
                        captureEngine.stopCapture()
                        usbStream?.close()
                        usbStream = null
                    }
                    val result = ensureCapture(descriptor, probe)
                    if (result.isFailure) {
                        val message = result.exceptionOrNull()?.message.orEmpty()
                        val busyElsewhere = message.contains("capture in use", ignoreCase = true)
                        if (!busyElsewhere) {
                            OmtLog.w("VuMeter", "VU capture failed for $mixerId: $message")
                        }
                        _state.update {
                            it.copy(
                                isVuMetering = false,
                                warningMessage = if (busyElsewhere) it.warningMessage else message,
                            )
                        }
                        return@withContext
                    }
                }
            }
            if (!captureEngine.isCaptureActive) {
                _state.update { it.copy(isVuMetering = false) }
            } else {
                _state.update { it.copy(isVuMetering = true, warningMessage = null) }
            }
        } else {
            _state.update { it.copy(isVuMetering = false) }
            stopWaveformUpdatesIfIdle()
            releaseCaptureIfIdleLocked()
        }
        ensureLiveCaptureUiUpdates()
    }

    internal fun debugRawMeterPeaksForTest(): FloatArray = captureEngine.debugRawMeterPeaks()

    fun onUsbDetached(deviceName: String?) {
        val desc = activeDescriptor ?: return
        if (deviceName == null || deviceName != desc.deviceName) return
        usbDetachJob?.cancel()
        if (_state.value.isPlaying) {
            finishPlaybackTransport(resetPosition = false, releaseNative = true)
        } else {
            scope.launch(Dispatchers.IO) {
                captureMutex.withLock { player.stop() }
            }
        }
        captureEngine.setUsbDegraded(true)
        _state.update {
            it.copy(
                isUsbDegraded = true,
                warningMessage = "No USB audio — waiting for device. Recording silence if active.",
                transportState = if (it.isRecording) TransportState.RECORDING_DEGRADED else it.transportState,
            )
        }
        usbDetachJob = scope.launch {
            delay(settings.usbDetachDebounceMs)
            withContext(Dispatchers.IO) {
                if (_state.value.isUsbDegraded) {
                    captureMutex.withLock {
                        if (!_state.value.isRecording && captureEngine.isCaptureActive) {
                            captureEngine.stopCapture()
                        }
                        usbStream?.close()
                        usbStream = null
                    }
                }
            }
        }
    }

    fun onUsbAttached(descriptor: UsbAudioDeviceDescriptor) {
        val profileMatch = profile?.let {
            it.vendorId == descriptor.vendorId &&
                it.productId == descriptor.productId &&
                (it.serialNumber == null || it.serialNumber == descriptor.serialNumber)
        } == true
        if (activeDescriptor?.deviceName != descriptor.deviceName &&
            profile?.usbDeviceName != descriptor.deviceName &&
            !profileMatch
        ) {
            return
        }
        usbDetachJob?.cancel()
        activeDescriptor = descriptor
        scope.launch {
            val probe = activeProbe
            if (probe == null) {
                OmtLog.w("MixerSession", "USB attached but no cached probe for $mixerId — re-probe required")
                _state.update {
                    it.copy(
                        usbDescriptor = descriptor,
                        statusMessage = "USB reconnected — probing mixer…",
                    )
                }
                return@launch
            }
            val reconnectResult = captureMutex.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { ensureCapture(descriptor, probe) }
                }
            }
            reconnectResult.onFailure { e ->
                OmtLog.e("MixerSession", "USB reconnect capture failed for $mixerId", e)
                _state.update {
                    it.copy(
                        warningMessage = "USB reconnected but audio failed: ${e.message}",
                        statusMessage = "Retrying USB audio…",
                    )
                }
                return@launch
            }
            captureEngine.setUsbDegraded(false)
            _state.update {
                it.copy(
                    isUsbDegraded = false,
                    warningMessage = null,
                    usbDescriptor = descriptor,
                    transportState = when {
                        it.isRecording -> TransportState.RECORDING
                        it.transportState == TransportState.RECORDING_DEGRADED -> TransportState.RECORDING
                        else -> it.transportState
                    },
                    statusMessage = if (it.isRecording) "USB reconnected — recording resumed" else "USB reconnected",
                )
            }
            if (_state.value.isMonitoring) applyMonitorRouting(enabled = true)
            startCaptureUiUpdates()
        }
    }

    fun shutdown() {
        releaseRecordingWakeLock()
        waveformJob?.cancel()
        waveformJob = null
        stopPlaybackStatusUpdates()
        player.stop()
        scope.launch {
            captureMutex.withLock {
                withContext(Dispatchers.IO) { captureEngine.stopCapture() }
            }
            usbStream?.close()
            usbStream = null
        }
    }

    private fun finishPlaybackTransport(
        resetPosition: Boolean,
        cancelStatusJob: Boolean = true,
        releaseNative: Boolean = false,
        restoreRouting: Boolean = true,
    ) {
        val trace = TransportTrace(if (releaseNative) "uiStopHard" else "uiStopSuspend")
        trace.mark("finishPlaybackTransport resetPosition=$resetPosition releaseNative=$releaseNative")
        if (cancelStatusJob) {
            stopPlaybackStatusUpdates()
        }
        _state.update {
            it.copy(
                isPlaying = false,
                transportState = TransportState.IDLE,
                playbackPositionSec = if (resetPosition) 0f else it.playbackPositionSec,
                soundcheckMeterLevels = emptyMap(),
            )
        }
        lastMediaProgressSec = -1
        AudioSessionBridge.refreshPlaybackNotification(_state.value)
        trace.mark("UI state updated, dispatching stop to IO")
        scope.launch(Dispatchers.IO) {
            captureMutex.withLock {
                trace.mark("captureMutex acquired for stop")
                if (releaseNative) {
                    player.stopAndAwait()
                    trace.mark("hard stop complete")
                } else {
                    player.suspendAndAwait()
                    trace.mark("suspend complete (native engine kept warm)")
                }
                if (isFlow8Active()) {
                    teardownFlow8UsbPlaybackLocked(trace)
                }
                if (restoreRouting) {
                    quiesceUsbBeforeRoutingLocked()
                    RoutingAutomationBridge.hooks?.afterSoundcheckRestore()
                }
            }
        }
    }

    private suspend fun routingBeforeRecordLocked(): RoutingHookResult {
        val prof = profile ?: return RoutingHookResult.Skipped
        val armed = _state.value.channelStrips.filter { it.armed }.map { it.index }.toSet()
        return RoutingAutomationBridge.hooks?.beforeRecordApply(prof, armed) ?: RoutingHookResult.Skipped
    }

    private suspend fun routingBeforeSoundcheckLocked(dir: File, metadata: SessionMetadata): RoutingHookResult {
        val prof = profile ?: return RoutingHookResult.Skipped
        val trackChannels = SoundcheckTrackChannels.indicesWithTracks(dir, metadata)
        return RoutingAutomationBridge.hooks?.beforeSoundcheckApply(prof, trackChannels)
            ?: RoutingHookResult.Skipped
    }

    private suspend fun routingAfterSoundcheckPlaybackStartedLocked(): RoutingHookResult {
        val prof = profile ?: return RoutingHookResult.Skipped
        return RoutingAutomationBridge.hooks?.afterSoundcheckPlaybackStarted(prof)
            ?: RoutingHookResult.Skipped
    }

    private fun startPlaybackStatusUpdates(sampleRate: Int, trace: TransportTrace? = null) {
        playbackStatusJob?.cancel()
        playbackStatusJob = scope.launch {
            var lastPosSec = -1f
            var idleChecks = 0
            var lastAdvanceMs = System.currentTimeMillis()
            var loggedFirstCursorAdvance = false
            val endToleranceFrames = (sampleRate * PlaybackTransportPolicy.END_TOLERANCE_SEC)
                .toLong()
                .coerceAtLeast(1L)
            while (isActive) {
                val st = player.status
                val activelyPlaying = st.state == TransportState.PLAYING || player.isPlaying
                val posSec = if (sampleRate > 0) {
                    st.positionFrames.toFloat() / sampleRate
                } else {
                    _state.value.playbackPositionSec
                }
                val durSec = if (sampleRate > 0) {
                    st.durationFrames.toFloat() / sampleRate
                } else {
                    _state.value.playbackDurationSec
                }
                if (!activelyPlaying) {
                    val durationSec = _state.value.playbackDurationSec.coerceAtLeast(durSec)
                    val reachedEnd = PlaybackTransportPolicy.shouldFinishAtEnd(
                        positionSec = posSec,
                        durationSec = durationSec,
                        loopEnabled = false,
                    )
                    if (!reachedEnd) {
                        idleChecks++
                        if (idleChecks < 2) {
                            delay(16)
                            continue
                        }
                    }
                    lastMediaProgressSec = -1
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            transportState = TransportState.IDLE,
                            playbackPositionSec = if (reachedEnd) 0f else posSec,
                            soundcheckMeterLevels = emptyMap(),
                            warningMessage = if (reachedEnd) null else it.warningMessage,
                        )
                    }
                    AudioSessionBridge.refreshPlaybackNotification(_state.value)
                    break
                }
                idleChecks = 0
                val loopEnabled = _state.value.soundcheckLoopEnabled
                val reachedEnd = PlaybackTransportPolicy.shouldFinishAtEnd(
                    positionFrames = st.positionFrames,
                    durationFrames = st.durationFrames,
                    loopEnabled = loopEnabled,
                    toleranceFrames = endToleranceFrames,
                ) || PlaybackTransportPolicy.shouldFinishAtEnd(
                    positionSec = posSec,
                    durationSec = durSec.coerceAtLeast(_state.value.playbackDurationSec),
                    loopEnabled = loopEnabled,
                )
                if (reachedEnd) {
                    finishPlaybackTransport(
                        resetPosition = true,
                        cancelStatusJob = false,
                        releaseNative = isFlow8Active(),
                    )
                    trace?.mark(
                        if (isFlow8Active()) {
                            "playback reached end, FLOW 8 hard stop"
                        } else {
                            "playback reached end, suspended for fast replay"
                        },
                    )
                    break
                }
                if (posSec > lastPosSec + 0.02f) {
                    if (!loggedFirstCursorAdvance) {
                        loggedFirstCursorAdvance = true
                        trace?.mark("UI cursor first advanced to ${"%.3f".format(posSec)}s")
                    }
                    lastPosSec = posSec
                    lastAdvanceMs = System.currentTimeMillis()
                } else if (
                    lastPosSec >= 0f &&
                    durSec > 0f &&
                    posSec < durSec - 0.25f &&
                    System.currentTimeMillis() - lastAdvanceMs > 2_500L
                ) {
                    OmtLog.w("MixerSession", "playback stalled at ${posSec}s — stopping")
                    org.openmultitrack.app.util.AppLogBuffer.append(
                        "W",
                        "Transport",
                        "Playback stalled at ${"%.2f".format(posSec)}s — recycling USB audio",
                    )
                    captureMutex.withLock {
                        stopSoundcheckLocked(releaseNative = true, restoreRouting = false)
                        _state.update {
                            it.copy(
                                warningMessage = if (isFlow8Active()) {
                                    "Playback stalled — tap Play again (USB audio was reset)."
                                } else {
                                    "Playback stalled — USB audio may be busy. Try again."
                                },
                            )
                        }
                    }
                    AudioSessionBridge.refreshPlaybackNotification(_state.value)
                    break
                }
                _state.update { s ->
                    s.copy(
                        isPlaying = true,
                        transportState = TransportState.PLAYING,
                        playbackPositionSec = posSec,
                        playbackDurationSec = durSec.takeIf { it > 0f } ?: s.playbackDurationSec,
                    )
                }
                AudioSessionBridge.tickMediaProgress(_state.value)
                delay(50)
            }
        }
    }

    private fun prepareSoundcheckSessionUi(
        sessionDir: String,
        metadata: SessionMetadata,
        durationSec: Float,
    ) {
        cachedSoundcheckDir = sessionDir
        cachedSoundcheckMetadata = metadata
        restoreSoundcheckStripsFromMetadata(metadata)
        val windowSec = org.openmultitrack.app.ui.daw.SoundcheckViewLayout.initialWindowSec(
            settings.playbackWaveformWindowSec,
            durationSec,
        )
        val channelTotal = metadata.channels.size
        val keepTransport = _state.value.isPlaying && _state.value.selectedSoundcheckDir == sessionDir
        val trackmarks = if (settings.chapterSupportEnabled) {
            SessionCueFile.read(File(sessionDir))
        } else {
            emptyList()
        }
        _state.update {
            it.copy(
                selectedSoundcheckDir = sessionDir,
                playbackPositionSec = if (keepTransport) it.playbackPositionSec else 0f,
                playbackDurationSec = durationSec,
                trackmarks = trackmarks,
                soundcheckSampleRate = metadata.sampleRate,
                soundcheckWaveforms = SessionWaveformOverview(
                    peaksByChannel = emptyMap(),
                    peaksPerSec = SessionWaveformExtractor.DEFAULT_PEAKS_PER_SEC,
                    durationSec = durationSec,
                ),
                soundcheckWaveformsLoading = channelTotal > 0,
                soundcheckWaveformProgress = 0f,
                soundcheckWaveformChannelsLoaded = 0,
                soundcheckWaveformChannelsTotal = channelTotal,
                soundcheckViewStartSec = 0f,
                soundcheckViewWindowSec = windowSec,
                soundcheckLoopStartSec = null,
                soundcheckLoopEndSec = null,
                soundcheckLoopEnabled = false,
                soundcheckLoopSelecting = false,
            )
        }
    }

    private fun loadSoundcheckWaveforms(sessionDir: String) {
        val current = _state.value
        if (current.selectedSoundcheckDir == sessionDir && current.isPlaying) {
            soundcheckWaveformJob?.cancel()
            soundcheckWaveformJob = scope.launch {
                val dir = File(sessionDir)
                val metadata = withContext(Dispatchers.IO) {
                    SessionMetadata.read(dir)?.withResolvedChannels(dir)
                } ?: return@launch
                loadSoundcheckWaveformsBackground(dir, metadata)
            }
            return
        }
        soundcheckWaveformJob?.cancel()
        soundcheckWaveformJob = scope.launch {
            val dir = File(sessionDir)
            val metadata = withContext(Dispatchers.IO) {
                SessionMetadata.read(dir)?.withResolvedChannels(dir)
            } ?: return@launch
            val durationSec = withContext(Dispatchers.IO) {
                SessionPlaybackDuration.durationSec(dir, metadata)
            }
            prepareSoundcheckSessionUi(sessionDir, metadata, durationSec)
            loadSoundcheckWaveformsBackground(dir, metadata)
        }
    }

    private fun loadSoundcheckWaveformsBackground(dir: File, metadata: SessionMetadata) {
        soundcheckWaveformJob?.cancel()
        soundcheckWaveformJob = scope.launch {
            TransportTraceHub.mark(mixerId, "waveforms load begin ${dir.name}")
            val wfT0 = System.nanoTime()
            val cached = withContext(Dispatchers.IO) { SessionWaveformCache.load(dir, metadata) }
            if (cached != null) {
                applySoundcheckWaveformOverview(cached, loading = false)
                TransportTraceHub.mark(
                    mixerId,
                    "waveforms cache hit ${cached.peaksByChannel.size} ch " +
                        "${(System.nanoTime() - wfT0) / 1_000_000}ms",
                )
                return@launch
            }
            TransportTraceHub.mark(mixerId, "waveforms extracting ${metadata.channels.size} ch")
            val overview = withContext(Dispatchers.IO) {
                SessionWaveformExtractor.extractIncremental(dir, metadata) { chIndex, peaks, completed, total ->
                    launch(Dispatchers.Main.immediate) {
                        mergeSoundcheckChannelPeaks(chIndex, peaks, completed, total)
                    }
                }
            }
            withContext(Dispatchers.IO) { SessionWaveformCache.save(dir, overview) }
            applySoundcheckWaveformOverview(overview, loading = false)
            TransportTraceHub.mark(
                mixerId,
                "waveforms extract done ${overview.peaksByChannel.size} ch " +
                    "${(System.nanoTime() - wfT0) / 1_000_000}ms",
            )
        }
    }

    private fun mergeSoundcheckChannelPeaks(
        channelIndex: Int,
        peaks: FloatArray,
        completed: Int,
        total: Int,
    ) {
        _state.update { s ->
            val base = s.soundcheckWaveforms ?: SessionWaveformOverview(
                peaksByChannel = emptyMap(),
                peaksPerSec = SessionWaveformExtractor.DEFAULT_PEAKS_PER_SEC,
                durationSec = s.playbackDurationSec,
            )
            val updatedPeaks = base.peaksByChannel.toMutableMap()
            updatedPeaks[channelIndex] = peaks
            s.copy(
                soundcheckWaveforms = base.copy(peaksByChannel = updatedPeaks),
                soundcheckWaveformsLoading = completed < total,
                soundcheckWaveformProgress = if (total > 0) completed.toFloat() / total else 1f,
                soundcheckWaveformChannelsLoaded = completed,
                soundcheckWaveformChannelsTotal = total,
            )
        }
    }

    private fun applySoundcheckWaveformOverview(overview: SessionWaveformOverview, loading: Boolean) {
        _state.update {
            val durationSec = if (!it.isPlaying && overview.durationSec > 0f) {
                overview.durationSec
            } else {
                it.playbackDurationSec
            }
            it.copy(
                soundcheckWaveforms = overview,
                soundcheckWaveformsLoading = loading,
                soundcheckWaveformProgress = if (loading) it.soundcheckWaveformProgress else 1f,
                soundcheckWaveformChannelsLoaded = if (loading) {
                    it.soundcheckWaveformChannelsLoaded
                } else {
                    overview.peaksByChannel.size
                },
                soundcheckWaveformChannelsTotal = overview.peaksByChannel.size
                    .coerceAtLeast(it.soundcheckWaveformChannelsTotal),
                playbackDurationSec = durationSec,
                soundcheckViewWindowSec = org.openmultitrack.app.ui.daw.SoundcheckViewLayout.clampWindow(
                    it.soundcheckViewWindowSec,
                    durationSec,
                ),
            )
        }
    }

    private fun restoreSoundcheckStripsFromMetadata(meta: SessionMetadata) {
        _state.update { s ->
            val strips = meta.channels.sortedBy { it.index }.map { metaCh ->
                val existing = s.channelStrips.firstOrNull { it.index == metaCh.index }
                    ?: ChannelStripState(index = metaCh.index)
                val hasCachedLabel = existing.displayName.isNotBlank() || existing.label.isNotBlank()
                existing.copy(
                    displayName = if (hasCachedLabel) existing.displayName else metaCh.displayName,
                    label = if (hasCachedLabel) existing.label else labelFromSessionFileName(metaCh.fileName),
                    colorArgb = if (hasCachedLabel) existing.colorArgb else metaCh.colorArgb,
                    iconId = existing.iconId,
                    armed = true,
                    monitoring = false,
                    solo = false,
                )
            }
            s.copy(
                channelStrips = strips,
                captureChannelCount = strips.size.coerceAtLeast(s.captureChannelCount),
            )
        }
    }

    fun updateSoundcheckViewConfig() {
        _state.update { s ->
            s.copy(
                soundcheckViewWindowSec = org.openmultitrack.app.ui.daw.SoundcheckViewLayout.clampWindow(
                    settings.playbackWaveformWindowSec,
                    s.playbackDurationSec,
                ),
            )
        }
    }

    private fun stopPlaybackStatusUpdates() {
        playbackStatusJob?.cancel()
        playbackStatusJob = null
    }

    private fun refreshStorageEstimate() {
        val root = storageRoot
        if (!root.isDirectory) return
        val freeBytes = runCatching {
            StatFs(root.absolutePath).availableBytes
        }.getOrDefault(0L)
        val armed = _state.value.channelStrips.count { it.armed }.coerceAtLeast(1)
        val sampleRate = (_state.value.probe?.input?.sampleRate ?: 48_000).coerceAtLeast(1)
        val bytesPerSec = sampleRate.toLong() * 3L * armed
        val estimateSec = if (bytesPerSec > 0) freeBytes.toFloat() / bytesPerSec else 0f
        _state.update {
            it.copy(
                storageFreeBytes = freeBytes,
                storageRecordEstimateSec = estimateSec,
            )
        }
    }

    private fun startCaptureUiUpdates() {
        ensureLiveCaptureUiUpdates()
    }

    private fun liveCaptureUiUpdatesDesired(): Boolean =
        _state.value.isMonitoring || _state.value.isRecording || vuCaptureDesired()

    private fun ensureLiveCaptureUiUpdates() {
        if (!liveCaptureUiUpdatesDesired()) {
            stopWaveformUpdatesIfIdle()
            return
        }
        if (waveformJob?.isActive == true) return
        waveformJob?.cancel()
        refreshStorageEstimate()
        waveformJob = scope.launch {
            var storageTick = 0
            var healTick = 0
            while (isActive) {
                if (liveCaptureUiUpdatesDesired()) {
                    if (!captureEngine.isCaptureActive) {
                        if (vuCaptureDesired() && healTick++ % 25 == 0) {
                            captureMutex.withLock {
                                if (vuCaptureDesired() && !captureEngine.isCaptureActive) {
                                    syncVuMeterCaptureWhileLocked()
                                }
                            }
                        }
                    } else {
                        healTick = 0
                        if (storageTick++ % 25 == 0) {
                            refreshStorageEstimate()
                        }
                        val levels = captureEngine.captureMeterLevels()
                        val rawPeaks = captureEngine.debugRawMeterPeaks()
                        val nowNs = System.nanoTime()
                        if (nowNs - lastVuLogNs >= 2_000_000_000L) {
                            lastVuLogNs = nowNs
                            val ch1 = levels[0] ?: 0f
                            val ch2 = levels[1] ?: 0f
                            val raw1 = rawPeaks.getOrElse(0) { 0f }
                            val raw2 = rawPeaks.getOrElse(1) { 0f }
                            if (levels.isNotEmpty()) {
                                OmtLog.i(
                                    "VuMeter",
                                    "ch1 vu=${"%.3f".format(ch1)} raw=${"%.4f".format(raw1)} " +
                                        "ch2 vu=${"%.3f".format(ch2)} raw=${"%.4f".format(raw2)} " +
                                        "vuOnly=${_state.value.isVuMetering} " +
                                        "monitoring=${_state.value.isMonitoring}",
                                )
                            }
                        }
                        _state.update { s ->
                            when {
                                s.isRecording -> {
                                    val elapsed = captureEngine.recordElapsedSec()
                                    val updated = withRecordViewFollowPlayhead(s, elapsed).copy(
                                        waveformPeaks = captureEngine.waveformSnapshots(normalize = false),
                                        recordElapsedSec = elapsed,
                                        captureMeterLevels = levels,
                                    )
                                    val mediaSec = elapsed.toInt()
                                    if (mediaSec != lastMediaProgressSec) {
                                        lastMediaProgressSec = mediaSec
                                        AudioSessionBridge.tickMediaProgress(updated)
                                    }
                                    updated
                                }
                                s.isMonitoring -> s.copy(
                                    captureMeterLevels = levels,
                                    waveformPeaks = emptyMap(),
                                    recordElapsedSec = 0f,
                                )
                                else -> s.copy(captureMeterLevels = levels)
                            }
                        }
                    }
                } else {
                    stopWaveformUpdatesIfIdle()
                    break
                }
                delay(40)
            }
        }
    }

    private fun restoreChannelStripsFromMetadata(meta: SessionMetadata) {
        val maxIndex = meta.channels.maxOfOrNull { it.index } ?: return
        val chCount = maxOf(_state.value.captureChannelCount, maxIndex + 1)
        _state.update { s ->
            val strips = (0 until chCount).map { i ->
                val existing = s.channelStrips.getOrNull(i) ?: ChannelStripState(index = i)
                val metaCh = meta.channels.firstOrNull { it.index == i }
                if (metaCh != null) {
                    val hasCachedLabel = existing.displayName.isNotBlank() || existing.label.isNotBlank()
                    existing.copy(
                        displayName = if (hasCachedLabel) existing.displayName else metaCh.displayName,
                        label = if (hasCachedLabel) {
                            existing.label
                        } else {
                            labelFromSessionFileName(metaCh.fileName)
                        },
                        colorArgb = if (hasCachedLabel) existing.colorArgb else metaCh.colorArgb,
                        iconId = existing.iconId,
                        armed = true,
                    )
                } else {
                    existing.copy(armed = false)
                }
            }
            s.copy(channelStrips = strips, captureChannelCount = chCount)
        }
    }

    private fun labelFromSessionFileName(fileName: String): String {
        val withoutExt = fileName.removeSuffix(".wav")
        val dash = withoutExt.indexOf(" - ")
        return if (dash >= 0) withoutExt.substring(dash + 3) else ""
    }

    private fun stopWaveformUpdatesIfIdle() {
        if (!_state.value.isMonitoring && !_state.value.isRecording && !vuCaptureDesired()) {
            waveformJob?.cancel()
            waveformJob = null
            captureEngine.clearWaveforms()
            _state.update { it.copy(waveformPeaks = emptyMap(), captureMeterLevels = emptyMap()) }
        }
    }

    private fun vuCaptureDesired(): Boolean {
        val prof = profile
        val hasSource = activeProbe != null && (
            activeDescriptor != null ||
                (prof != null && VirtualMixer.isDemoMixer(prof))
            )
        return isActiveMixer() &&
            settings.showVuMeters &&
            _state.value.appMode == AppMode.MULTITRACK_RECORD &&
            hasSource &&
            !_state.value.isMonitoring &&
            !isRecordingTransport() &&
            !_state.value.isPlaying
    }

    private fun acquireRecordingWakeLock() {
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        releaseRecordingWakeLock()
        recordingWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OpenMultiTrack:Recording",
        ).apply { setReferenceCounted(false) }
        recordingWakeLock?.acquire()
    }

    private fun releaseRecordingWakeLock() {
        recordingWakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        recordingWakeLock = null
    }

    private fun applyMonitorRouting(enabled: Boolean = _state.value.isMonitoring) {
        val s = _state.value
        val monitorChannels = s.channelStrips.filter { it.monitoring }.map { it.index }.toSet()
        val solo = s.channelStrips.firstOrNull { it.solo }?.index
        captureEngine.updateMonitor(
            MonitorMixConfig(
                enabled = enabled,
                channelMonitoring = monitorChannels,
                soloChannel = solo,
                gainLinear = settings.monitorGainLinear,
                outputDeviceId = s.monitorOutputDeviceId,
            ),
        )
    }

    private suspend fun ensureCapture(
        descriptor: UsbAudioDeviceDescriptor,
        probe: FullUsbProbeResult,
    ): Result<Int> {
        val capT0 = System.nanoTime()
        updateWaveformConfig()
        if (VirtualMixer.isDemoMixer(profile ?: return Result.failure(IllegalStateException("No mixer profile")))) {
            if (captureEngine.isCaptureActive && captureEngine.isSyntheticCapture()) {
                return Result.success(captureEngine.activeChannelCount)
            }
            return captureEngine.startSyntheticCapture(
                scope = scope,
                channelCount = VirtualMixer.DEMO_CHANNEL_COUNT,
                sampleRateHz = VirtualMixer.SAMPLE_RATE_HZ,
                generator = SyntheticCaptureGenerator.fromDemoBand(VirtualMixer.SAMPLE_RATE_HZ),
            ).map {
                syncChannelStripsToCaptureCount(captureEngine.activeChannelCount)
                captureEngine.activeChannelCount
            }
        }
        if (_state.value.appMode == AppMode.MULTITRACK_RECORD &&
            captureEngine.isCaptureActive &&
            captureEngine.isNativeCaptureOwner() &&
            !isRecordingTransport()
        ) {
            return Result.success(
                captureEngine.activeChannelCount.coerceAtLeast(channelCountFromProbe(probe)),
            )
        }
        if (captureEngine.isCaptureActive && !captureEngine.isUsbDegraded && captureEngine.isNativeCaptureOwner()) {
            val receiveWindowMs = when {
                isRecordingTransport() -> 5_000L
                _state.value.appMode == AppMode.MULTITRACK_RECORD -> 5_000L
                else -> 1_500L
            }
            if (captureEngine.isReceivingAudio(receiveWindowMs)) {
                val count = channelCountFromProbe(probe)
                return Result.success(count)
            }
            if (isRecordingTransport()) {
                OmtLog.w(
                    "MixerSession",
                    "Capture stalled during recording for $mixerId — reconnecting stream",
                )
            } else if (_state.value.appMode == AppMode.MULTITRACK_RECORD) {
                OmtLog.i(
                    "MixerSession",
                    "Capture active in record mode for $mixerId — keeping stream despite brief stall",
                )
                return Result.success(captureEngine.activeChannelCount)
            } else {
                OmtLog.w("MixerSession", "Capture active but not receiving audio for $mixerId — reopening")
                withContext(Dispatchers.IO) { captureEngine.stopCapture() }
                usbStream?.close()
                usbStream = null
            }
        }
        if (captureEngine.isCaptureActive && !captureEngine.isNativeCaptureOwner()) {
            if (isRecordingTransport() && !captureEngine.isUsbDegraded) {
                OmtLog.e("MixerSession", "Refusing capture handoff while recording ($mixerId)")
                return Result.failure(IllegalStateException("USB capture busy while recording"))
            }
            if (!isRecordingTransport()) {
                withContext(Dispatchers.IO) { captureEngine.stopCapture() }
            }
        }
        val requested = channelCountFromProbe(probe)
        val device = enumerator.getUsbDevice(descriptor.deviceName)
            ?: return Result.failure(IllegalStateException("USB device not found"))
        if (isFlow8Active() && !isRecordingTransport()) {
            val recordMode = _state.value.appMode == AppMode.MULTITRACK_RECORD
            when {
                !captureEngine.isCaptureActive -> prepareFlow8UsbForCaptureLocked()
                !recordMode && !captureEngine.isReceivingAudio(5_000) -> prepareFlow8UsbForCaptureLocked()
            }
        }
        val stream = openStream(descriptor)
            ?: return Result.failure(IllegalStateException("Could not open USB device"))
        usbStream?.close()
        usbStream = stream
        activeProbe = probe
        activeUsbDevice = device
        val route = AudioEngineRouter.resolveCaptureRoute(probe, stream, requested)
            ?: return Result.failure(IllegalStateException("No capture route"))
        return captureEngine.startCapture(scope, route, device).map {
            syncChannelStripsToCaptureCount(captureEngine.activeChannelCount)
            TransportTraceHub.mark(
                mixerId,
                "ensureCapture native ${(System.nanoTime() - capT0) / 1_000_000}ms " +
                    "ch=${captureEngine.activeChannelCount}",
            )
            captureEngine.activeChannelCount
        }
    }

    private fun syncChannelStripsToCaptureCount(captureCh: Int) {
        if (captureCh <= 0) return
        val demoStrips = if (profile?.let(VirtualMixer::isDemoMixer) == true) {
            DemoBandChannels.channelStripStates()
        } else {
            null
        }
        _state.update { s ->
            val existing = s.channelStrips
            when {
                demoStrips != null && existing.size != captureCh -> s.copy(
                    channelStrips = demoStrips.take(captureCh),
                    captureChannelCount = captureCh,
                )
                existing.size == captureCh -> s.copy(captureChannelCount = captureCh)
                existing.size > captureCh -> s.copy(
                    channelStrips = existing.take(captureCh),
                    captureChannelCount = captureCh,
                )
                else -> {
                    val strips = existing.toMutableList()
                    for (i in existing.size until captureCh) {
                        strips.add(demoStrips?.getOrNull(i) ?: ChannelStripState(index = i))
                    }
                    s.copy(channelStrips = strips, captureChannelCount = captureCh)
                }
            }
        }
    }

    private fun channelCountFromProbe(probe: FullUsbProbeResult): Int =
        probe.uac2Caps?.maxCaptureChannels?.takeIf { it > 0 }
            ?: probe.input?.takeIf { it.isSuccess }?.channelCount
            ?: 0

    private fun openStream(descriptor: UsbAudioDeviceDescriptor): UsbAudioStreamHandle? {
        val device = enumerator.getUsbDevice(descriptor.deviceName) ?: return null
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
        return UsbAudioStreamHandle.open(appContext, usbManager, device)
    }

    private suspend fun releaseCaptureIfIdleLocked() {
        if (_state.value.appMode.isPlaybackMode && activeDescriptor != null) {
            if (!captureEngine.isRecording &&
                !_state.value.isMonitoring &&
                !vuCaptureDesired()
            ) {
                captureEngine.updateVuMetering(false)
                withContext(Dispatchers.IO) { captureEngine.stopCapture() }
            }
            return
        }
        if (!captureEngine.isRecording &&
            !_state.value.isMonitoring &&
            !_state.value.isPlaying &&
            !vuCaptureDesired()
        ) {
            captureEngine.updateVuMetering(false)
            withContext(Dispatchers.IO) { captureEngine.stopCapture() }
            usbStream?.close()
            usbStream = null
        }
    }

    private suspend fun ensurePlaybackLocked(
        descriptor: UsbAudioDeviceDescriptor,
        probe: FullUsbProbeResult,
        channelCount: Int,
    ): Result<org.openmultitrack.usb.PlaybackRoute> {
        val playT0 = System.nanoTime()
        val prof = profile
        if (prof != null && VirtualMixer.isDemoMixer(prof)) {
            val rate = probe.input?.sampleRate?.takeIf { it > 0 } ?: 48_000
            val route = VirtualDevicePlayback.resolveRoute(appContext, channelCount, rate)
                ?: return Result.failure(IllegalStateException("No audio output device"))
            return Result.success(route)
        }
        val device = enumerator.getUsbDevice(descriptor.deviceName)
            ?: return Result.failure(IllegalStateException("USB device not found"))
        if (usbStream == null) {
            OmtLog.i("MixerSession", "ensurePlayback opening USB stream for ${descriptor.deviceName}")
            val stream = openStream(descriptor)
                ?: return Result.failure(IllegalStateException("Could not open USB device"))
            usbStream = stream
            activeUsbDevice = device
            OmtLog.i("MixerSession", "ensurePlayback USB stream open fd=${stream.fd}")
        }
        val route = AudioEngineRouter.resolvePlaybackRoute(probe, usbStream, channelCount)
            ?: return Result.failure(IllegalStateException("No playback route"))
        TransportTraceHub.mark(
            mixerId,
            "ensurePlayback ${(System.nanoTime() - playT0) / 1_000_000}ms backend=${route.backend}",
        )
        return Result.success(route)
    }

    private fun maxPlaybackChannelsFromProbe(probe: FullUsbProbeResult): Int =
        MixerUsbChannelCounts.playbackChannels(probe)
}
