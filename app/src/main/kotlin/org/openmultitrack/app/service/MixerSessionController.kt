package org.openmultitrack.app.service

import android.content.Context
import android.hardware.usb.UsbDevice
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
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
import org.openmultitrack.app.audio.LiveWaveformSnapshot
import org.openmultitrack.app.audio.MonitorMixConfig
import org.openmultitrack.app.audio.SessionPlayer
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.root.LoopbackSetup
import org.openmultitrack.app.root.RootShell
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.sessionio.session.SessionLibrary
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.wav.SessionWaveformCache
import org.openmultitrack.sessionio.wav.SessionWaveformExtractor
import org.openmultitrack.sessionio.wav.SessionWaveformOverview
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.sessionio.wav.WavWriter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.FullUsbProbeResult
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
    val isUsbDegraded: Boolean = false,
    val isVirtualMicActive: Boolean = false,
    val isPlaying: Boolean = false,
    val transportState: TransportState = TransportState.IDLE,
    val statusMessage: String? = null,
    val warningMessage: String? = null,
    val lastRecordingPath: String? = null,
    val monitorOutputDeviceId: Int = -1,
    val captureChannelCount: Int = 0,
    val recordElapsedSec: Float = 0f,
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
)

class MixerSessionController(
    val mixerId: String,
    private val appContext: Context,
    private val enumerator: UsbAudioEnumerator,
    private val settings: AppSettingsStore,
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

    private val storageRoot: File
        get() = settings.storageRootPath?.let { File(it) }
            ?: File(appContext.getExternalFilesDir(null), "OpenMultiTrack")

    private val _state = MutableStateFlow(MixerSessionUiState(mixerId = mixerId))
    val state: StateFlow<MixerSessionUiState> = _state.asStateFlow()

    fun setProfile(mixer: MixerProfile) {
        profile = mixer
        _state.update { it.copy(mixerProfile = mixer) }
    }

    fun setProbeResult(descriptor: UsbAudioDeviceDescriptor, probe: FullUsbProbeResult) {
        activeDescriptor = descriptor
        activeProbe = probe
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
                channelStrips = strips,
                statusMessage = "Ready — $chCount channels",
            )
        }
    }

    fun setProbing(probing: Boolean) {
        _state.update { it.copy(probing = probing) }
    }

    fun setAppMode(mode: AppMode) {
        if (mode == AppMode.VIRTUAL_SOUNDCHECK && _state.value.isRecording) {
            scope.launch { stopRecording() }
        }
        if (mode == AppMode.MULTITRACK_RECORD) {
            stopSoundcheck()
        }
        _state.update { it.copy(appMode = mode) }
        if (mode == AppMode.VIRTUAL_SOUNDCHECK) {
            refreshSoundcheckLibrary()
            _state.update {
                it.copy(soundcheckViewWindowSec = settings.playbackWaveformWindowSec.coerceIn(30f, 600f))
            }
        }
    }

    fun refreshSoundcheckLibrary() {
        val prof = profile ?: return
        scope.launch {
            val sessions = withContext(Dispatchers.IO) {
                SessionLibrary.listCompletedSessions(storageRoot, prof.storageFolderName(), prof.id)
            }.map { summary ->
                SoundcheckSessionItem(
                    sessionDir = summary.dir.absolutePath,
                    title = summary.displayTitle,
                    startedAtEpochMs = summary.metadata.startedAtEpochMs,
                    durationSec = summary.durationSec,
                    channelCount = summary.channelCount,
                )
            }
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
        scope.launch {
            captureMutex.withLock {
                stopSoundcheckLocked()
                soundcheckWaveformJob?.cancel()
            }
            val dir = File(sessionDir)
            val metadata = withContext(Dispatchers.IO) {
                SessionMetadata.read(dir)?.withResolvedChannels(dir)
            } ?: return@launch
            val durationSec = item?.durationSec?.takeIf { it > 0f }
                ?: withContext(Dispatchers.IO) { SessionWaveformExtractor.durationSec(dir, metadata) }
            prepareSoundcheckSessionUi(sessionDir, metadata, durationSec)
            loadSoundcheckWaveformsBackground(dir, metadata)
        }
    }

    fun setSoundcheckView(viewStartSec: Float, viewWindowSec: Float) {
        val duration = _state.value.playbackDurationSec
        val window = viewWindowSec.coerceIn(30f, 600f)
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
        val newWindow = (s.soundcheckViewWindowSec / scale).coerceIn(30f, 600f)
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
                    _state.update { it.copy(statusMessage = e.message) }
                }
            }
        }
    }

    fun stopSoundcheck() {
        scope.launch {
            captureMutex.withLock { stopSoundcheckLocked() }
        }
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
        }
    }

    fun toggleSoundcheckPlayback() {
        scope.launch {
            captureMutex.withLock {
                if (_state.value.isPlaying) {
                    stopSoundcheckLocked()
                } else {
                    val dir = _state.value.selectedSoundcheckDir ?: return@withLock
                    val metadata = SessionMetadata.read(File(dir)) ?: return@withLock
                    val frame = (_state.value.playbackPositionSec * metadata.sampleRate).toLong()
                    try {
                        startSoundcheckPlaybackLocked(frame)
                    } catch (e: Exception) {
                        OmtLog.e("MixerSession", "toggleSoundcheckPlayback failed", e)
                        _state.update { it.copy(statusMessage = e.message) }
                    }
                }
            }
        }
    }

    private suspend fun startSoundcheckPlaybackLocked(startFrame: Long) {
        val descriptor = activeDescriptor ?: return
        val probe = activeProbe ?: return
        val sessionDir = _state.value.selectedSoundcheckDir ?: return
        val dir = File(sessionDir)
        val metadata = withContext(Dispatchers.IO) {
            SessionMetadata.read(dir)?.withResolvedChannels(dir)
        } ?: return
        withContext(Dispatchers.IO) {
            player.stopAndAwait()
            val playbackChannels = minOf(
                metadata.channels.size,
                maxPlaybackChannelsFromProbe(probe),
            ).coerceAtLeast(1)
            val playbackMetadata = metadata.copy(
                channels = metadata.channels.sortedBy { it.index }.take(playbackChannels),
            )
            val route = ensurePlaybackLocked(descriptor, probe, playbackChannels).getOrThrow()
            val ui = _state.value
            val loopStart = ui.soundcheckLoopStartSec?.takeIf { ui.soundcheckLoopEnabled }
                ?.let { (it * metadata.sampleRate).toLong() }
            val loopEnd = ui.soundcheckLoopEndSec?.takeIf { ui.soundcheckLoopEnabled }
                ?.let { (it * metadata.sampleRate).toLong() }
            player.playSession(
                scope = scope,
                sessionDir = dir,
                metadata = playbackMetadata,
                route = route,
                usbDevice = activeUsbDevice,
                startFrame = startFrame,
                loopStartFrame = loopStart,
                loopEndFrame = loopEnd,
                loopEnabled = ui.soundcheckLoopEnabled,
            ).getOrThrow()
        }
        startPlaybackStatusUpdates(metadata.sampleRate)
        _state.update {
            it.copy(
                isPlaying = true,
                transportState = TransportState.PLAYING,
                statusMessage = "Playing to USB returns",
                warningMessage = null,
            )
        }
    }

    private suspend fun restartSoundcheckPlaybackLocked() {
        val sessionDir = _state.value.selectedSoundcheckDir ?: return
        val metadata = SessionMetadata.read(File(sessionDir)) ?: return
        val frame = (_state.value.playbackPositionSec * metadata.sampleRate).toLong()
        startSoundcheckPlaybackLocked(frame)
    }

    private suspend fun stopSoundcheckLocked() {
        val sampleRate = _state.value.selectedSoundcheckDir
            ?.let { SessionMetadata.read(File(it))?.sampleRate }
            ?: 48_000
        val posSec = if (sampleRate > 0 && player.isPlaying) {
            player.status.positionFrames.toFloat() / sampleRate
        } else {
            _state.value.playbackPositionSec
        }
        withContext(Dispatchers.IO) { player.stopAndAwait() }
        stopPlaybackStatusUpdates()
        _state.update {
            it.copy(
                isPlaying = false,
                transportState = TransportState.IDLE,
                playbackPositionSec = posSec,
                soundcheckMeterLevels = emptyMap(),
            )
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
    }

    fun setMonitorOutputDevice(deviceId: Int) {
        _state.update { it.copy(monitorOutputDeviceId = deviceId) }
        applyMonitorRouting()
    }

    fun setMonitorGain(gain: Float) {
        settings.monitorGainLinear = gain
        applyMonitorRouting()
    }

    fun updateWaveformConfig() {
        captureEngine.setWaveformConfig(
            windowSec = settings.recordWaveformWindowSec,
            peaksPerSecond = CaptureSessionEngine.DEFAULT_WAVEFORM_PEAKS_PER_SEC,
        )
    }

    fun startMonitoring() {
        val descriptor = activeDescriptor ?: return
        val probe = activeProbe ?: return
        scope.launch {
            captureMutex.withLock {
                withContext(Dispatchers.IO) {
                    ensureCapture(descriptor, probe).getOrThrow()
                }
                applyMonitorRouting(enabled = true)
                startCaptureUiUpdates()
                _state.update {
                    it.copy(
                        isMonitoring = true,
                        waveformPeaks = emptyMap(),
                        recordElapsedSec = 0f,
                        statusMessage = "Monitoring",
                        warningMessage = null,
                    )
                }
            }
        }
    }

    fun stopMonitoring() {
        captureEngine.updateMonitor(MonitorMixConfig(enabled = false))
        _state.update { it.copy(isMonitoring = false, statusMessage = "Monitor off") }
        stopWaveformUpdatesIfIdle()
        scope.launch {
            captureMutex.withLock { releaseCaptureIfIdleLocked() }
        }
    }

    fun startRecording() {
        val descriptor = activeDescriptor ?: return
        val probe = activeProbe ?: return
        val prof = profile ?: return
        scope.launch {
            try {
                captureMutex.withLock {
                    withContext(Dispatchers.IO) {
                        if (!captureEngine.isCaptureActive) {
                            ensureCapture(descriptor, probe).getOrThrow()
                        } else {
                            syncChannelStripsToCaptureCount(captureEngine.activeChannelCount)
                        }
                        captureEngine.startRecording(
                            CaptureSessionEngine.RecordingConfig(
                                mixerId = prof.id,
                                mixerFolderName = prof.storageFolderName(),
                                storageRoot = storageRoot,
                                channelStrips = _state.value.channelStrips,
                            ),
                        ).getOrThrow()
                    }
                }
                startCaptureUiUpdates()
                val sessionDir = captureEngine.activeSessionDir
                if (sessionDir != null) {
                    settings.setActiveRecording(prof.id, sessionDir.absolutePath)
                }
                _state.update {
                    it.copy(
                        isRecording = true,
                        transportState = TransportState.RECORDING,
                        statusMessage = "Recording…",
                        warningMessage = null,
                        lastRecordingPath = sessionDir?.absolutePath ?: it.lastRecordingPath,
                    )
                }
            } catch (e: Exception) {
                OmtLog.e("MixerSession", "startRecording failed", e)
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
                startCaptureUiUpdates()
                settings.setActiveRecording(
                    meta.mixerId.takeIf { it.isNotBlank() } ?: profile?.id ?: mixerId,
                    sessionDir.absolutePath,
                )
                _state.update {
                    it.copy(
                        isRecording = true,
                        transportState = TransportState.RECORDING,
                        statusMessage = "Recording resumed",
                        warningMessage = null,
                        lastRecordingPath = sessionDir.absolutePath,
                    )
                }
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

    fun stopRecording() {
        scope.launch {
            val session = captureMutex.withLock {
                val ended = withContext(Dispatchers.IO) { captureEngine.stopRecording() }
                releaseCaptureIfIdleLocked()
                ended
            }
            settings.clearActiveRecording()
            _state.update {
                it.copy(
                    isRecording = false,
                    transportState = TransportState.IDLE,
                    lastRecordingPath = session?.filePath ?: it.lastRecordingPath,
                    statusMessage = session?.let { s -> "Saved → ${s.filePath}" } ?: "Stopped",
                )
            }
            stopWaveformUpdatesIfIdle()
            if (_state.value.appMode == AppMode.VIRTUAL_SOUNDCHECK) {
                refreshSoundcheckLibrary()
            }
        }
    }

    fun onUsbDetached(deviceName: String?) {
        val desc = activeDescriptor ?: return
        if (deviceName == null || deviceName != desc.deviceName) return
        usbDetachJob?.cancel()
        if (_state.value.isPlaying) {
            stopSoundcheck()
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
            if (probe == null) return@launch
            captureMutex.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { ensureCapture(descriptor, probe) }
                }
            }
            captureEngine.setUsbDegraded(false)
            _state.update {
                it.copy(
                    isUsbDegraded = false,
                    warningMessage = null,
                    usbDescriptor = descriptor,
                    transportState = if (it.isRecording) TransportState.RECORDING else it.transportState,
                    statusMessage = "USB reconnected",
                )
            }
            if (_state.value.isMonitoring) applyMonitorRouting(enabled = true)
        }
    }

    fun shutdown() {
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

    private fun startPlaybackStatusUpdates(sampleRate: Int) {
        playbackStatusJob?.cancel()
        playbackStatusJob = scope.launch {
            var lastPosSec = -1f
            while (isActive) {
                val st = player.status
                if (st.state != TransportState.PLAYING) {
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            transportState = TransportState.IDLE,
                            soundcheckMeterLevels = emptyMap(),
                        )
                    }
                    break
                }
                val posSec = if (sampleRate > 0) st.positionFrames.toFloat() / sampleRate else 0f
                val durSec = if (sampleRate > 0) st.durationFrames.toFloat() / sampleRate else 0f
                val meters = player.meterLevelsSnapshot()
                if (abs(posSec - lastPosSec) >= 0.08f) {
                    lastPosSec = posSec
                }
                _state.update { s ->
                    s.copy(
                        isPlaying = true,
                        transportState = TransportState.PLAYING,
                        playbackPositionSec = posSec,
                        playbackDurationSec = durSec.coerceAtLeast(s.playbackDurationSec),
                        soundcheckMeterLevels = meters,
                    )
                }
                delay(80)
            }
        }
    }

    private fun prepareSoundcheckSessionUi(
        sessionDir: String,
        metadata: SessionMetadata,
        durationSec: Float,
    ) {
        restoreSoundcheckStripsFromMetadata(metadata)
        val windowSec = settings.playbackWaveformWindowSec.coerceIn(30f, 600f)
        val channelTotal = metadata.channels.size
        _state.update {
            it.copy(
                selectedSoundcheckDir = sessionDir,
                playbackPositionSec = 0f,
                playbackDurationSec = durationSec,
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
        soundcheckWaveformJob?.cancel()
        soundcheckWaveformJob = scope.launch {
            val dir = File(sessionDir)
            val metadata = withContext(Dispatchers.IO) {
                SessionMetadata.read(dir)?.withResolvedChannels(dir)
            } ?: return@launch
            val durationSec = withContext(Dispatchers.IO) { SessionWaveformExtractor.durationSec(dir, metadata) }
            prepareSoundcheckSessionUi(sessionDir, metadata, durationSec)
            loadSoundcheckWaveformsBackground(dir, metadata)
        }
    }

    private fun loadSoundcheckWaveformsBackground(dir: File, metadata: SessionMetadata) {
        soundcheckWaveformJob?.cancel()
        soundcheckWaveformJob = scope.launch {
            val cached = withContext(Dispatchers.IO) { SessionWaveformCache.load(dir, metadata) }
            if (cached != null) {
                applySoundcheckWaveformOverview(cached, loading = false)
                return@launch
            }
            val overview = withContext(Dispatchers.IO) {
                SessionWaveformExtractor.extractIncremental(dir, metadata) { chIndex, peaks, completed, total ->
                    launch(Dispatchers.Main.immediate) {
                        mergeSoundcheckChannelPeaks(chIndex, peaks, completed, total)
                    }
                }
            }
            withContext(Dispatchers.IO) { SessionWaveformCache.save(dir, overview) }
            applySoundcheckWaveformOverview(overview, loading = false)
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
                playbackDurationSec = overview.durationSec.coerceAtLeast(it.playbackDurationSec),
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
        val window = settings.playbackWaveformWindowSec.coerceIn(30f, 600f)
        _state.update { it.copy(soundcheckViewWindowSec = window) }
    }

    private fun stopPlaybackStatusUpdates() {
        playbackStatusJob?.cancel()
        playbackStatusJob = null
    }

    private fun startCaptureUiUpdates() {
        waveformJob?.cancel()
        waveformJob = scope.launch {
            while (isActive) {
                if (captureEngine.isCaptureActive) {
                    val levels = captureEngine.captureMeterLevels()
                    val recording = _state.value.isRecording
                    val monitoring = _state.value.isMonitoring
                    when {
                        recording -> {
                            val peaks = captureEngine.waveformSnapshots(normalize = true)
                            _state.update {
                                it.copy(
                                    waveformPeaks = peaks,
                                    recordElapsedSec = captureEngine.recordElapsedSec(),
                                    captureMeterLevels = levels,
                                )
                            }
                        }
                        monitoring -> {
                            _state.update {
                                it.copy(
                                    captureMeterLevels = levels,
                                    waveformPeaks = emptyMap(),
                                    recordElapsedSec = 0f,
                                )
                            }
                        }
                    }
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
        if (!_state.value.isMonitoring && !_state.value.isRecording) {
            waveformJob?.cancel()
            waveformJob = null
            captureEngine.clearWaveforms()
            _state.update { it.copy(waveformPeaks = emptyMap(), captureMeterLevels = emptyMap()) }
        }
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
        updateWaveformConfig()
        if (captureEngine.isCaptureActive && !captureEngine.isUsbDegraded) {
            val count = channelCountFromProbe(probe)
            return Result.success(count)
        }
        val requested = channelCountFromProbe(probe)
        val device = enumerator.getUsbDevice(descriptor.deviceName)
            ?: return Result.failure(IllegalStateException("USB device not found"))
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
            captureEngine.activeChannelCount
        }
    }

    private fun syncChannelStripsToCaptureCount(captureCh: Int) {
        if (captureCh <= 0) return
        _state.update { s ->
            val existing = s.channelStrips
            when {
                existing.size == captureCh -> s.copy(captureChannelCount = captureCh)
                existing.size > captureCh -> s.copy(
                    channelStrips = existing.take(captureCh),
                    captureChannelCount = captureCh,
                )
                else -> {
                    val strips = existing.toMutableList()
                    for (i in existing.size until captureCh) {
                        strips.add(ChannelStripState(index = i))
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
        if (!captureEngine.isRecording && !_state.value.isMonitoring && !_state.value.isPlaying) {
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
        val device = enumerator.getUsbDevice(descriptor.deviceName)
            ?: return Result.failure(IllegalStateException("USB device not found"))
        if (usbStream == null) {
            val stream = openStream(descriptor)
                ?: return Result.failure(IllegalStateException("Could not open USB device"))
            usbStream = stream
            activeUsbDevice = device
        }
        val route = AudioEngineRouter.resolvePlaybackRoute(probe, usbStream, channelCount)
            ?: return Result.failure(IllegalStateException("No playback route"))
        return Result.success(route)
    }

    private fun maxPlaybackChannelsFromProbe(probe: FullUsbProbeResult): Int =
        probe.uac2Caps?.maxPlaybackChannels?.takeIf { it > 0 }
            ?: probe.output?.takeIf { it.isSuccess }?.channelCount
            ?: 2
}
