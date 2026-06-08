package org.openmultitrack.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.data.ScribbleStripCache
import org.openmultitrack.app.service.AudioSessionClient
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.usb.AudioOutputDeviceLabel
import org.openmultitrack.usb.LabeledAudioDevice
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.app.audio.RecordAudioPermissions
import org.openmultitrack.app.scribble.Flow8BlePermissions
import org.openmultitrack.app.ui.daw.StatusToast
import org.openmultitrack.app.scribble.Flow8BleScribbleImporter
import org.openmultitrack.app.scribble.IncompleteRecordingStore
import org.openmultitrack.app.scribble.OscLanDiscovery
import org.openmultitrack.app.scribble.ScribbleImportSupport
import org.openmultitrack.mixer.behringer.Xr18ScribbleImporter
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService

data class Flow8PairingDialogState(
    val mixerId: String,
)

private sealed class PendingFlow8Action {
    data class ShowPairingDialog(val mixerId: String) : PendingFlow8Action()
    data class Import(val mixerId: String) : PendingFlow8Action()
}

private sealed class PendingAudioAction {
    data class Record(val mixerId: String) : PendingAudioAction()
    data class Monitor(val mixerId: String) : PendingAudioAction()
    data class Resume(val mixerId: String, val sessionDir: java.io.File) : PendingAudioAction()
}

data class DawUiState(
    val mixers: List<MixerProfile> = emptyList(),
    val activeMixerId: String? = null,
    val sessionByMixer: Map<String, MixerSessionUiState> = emptyMap(),
    val outputDevices: List<LabeledAudioDevice> = emptyList(),
    val availableUsbDevices: List<UsbAudioDeviceDescriptor> = emptyList(),
    val addableUsbDevices: List<UsbAudioDeviceDescriptor> = emptyList(),
    val showAddMixerDialog: Boolean = false,
    val showSettings: Boolean = false,
    val showLogViewer: Boolean = false,
    val flow8PairingDialog: Flow8PairingDialogState? = null,
    val statusToast: StatusToast? = null,
    val hideArmButton: Boolean = false,
    val hideMonitorButton: Boolean = false,
    val hideSoloButton: Boolean = false,
    val showWaveforms: Boolean = true,
    val stripNumberMode: StripNumberMode = StripNumberMode.BOTH,
    val stripIconMode: StripIconMode = StripIconMode.SHOW,
    val pendingRecordingResume: Boolean = false,
)

class MainViewModel(
    private val appContext: Context,
    private val enumerator: UsbAudioEnumerator,
    private val probeService: UsbAudioProbeService,
    private val mixerStore: MixerDeviceStore,
    private val scribbleStripCache: ScribbleStripCache,
    private val settings: AppSettingsStore,
    private val sessionClient: AudioSessionClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DawUiState(
            hideArmButton = settings.hideArmButton,
            hideMonitorButton = settings.hideMonitorButton,
            hideSoloButton = settings.hideSoloButton,
            showWaveforms = settings.showWaveforms,
            stripNumberMode = settings.stripNumberMode,
            stripIconMode = settings.stripIconMode,
        ),
    )
    val uiState: StateFlow<DawUiState> = _uiState.asStateFlow()

    private val observedMixerIds = mutableSetOf<String>()
    private val scribbleImportMutex = Mutex()
    private var usbRecoveryJob: Job? = null
    private var sessionAttached = false

    private val _usbPermissionRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val usbPermissionRequests: SharedFlow<String> = _usbPermissionRequests.asSharedFlow()
    private val _bluetoothPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val bluetoothPermissionRequests: SharedFlow<Unit> = _bluetoothPermissionRequests.asSharedFlow()
    private val _audioPermissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val audioPermissionRequests: SharedFlow<Unit> = _audioPermissionRequests.asSharedFlow()
    private var pendingFlow8Action: PendingFlow8Action? = null
    private var pendingAudioAction: PendingAudioAction? = null
    private val lastSessionStatusByMixer = mutableMapOf<String, String?>()

    init {
        sessionClient.onManagerLost {
            observedMixerIds.clear()
            sessionAttached = false
            OmtLog.w("ViewModel", "session service lost — will re-attach on reconnect")
        }
        sessionClient.bind()
        loadMixers()
        sessionClient.whenReady { manager ->
            if (!sessionAttached) {
                attachToSessionManager(manager)
                sessionAttached = true
            }
            viewModelScope.launch {
                manager.activeMixerId.collect { id ->
                    _uiState.update { it.copy(activeMixerId = id) }
                }
            }
            refreshUsbAndOutputs()
        }
    }

    fun onAppResumed() {
        refreshUsbAndOutputs()
    }

    fun onBluetoothPermissionsResult(granted: Boolean) {
        if (!granted) {
            pendingFlow8Action = null
            showStatus("Bluetooth permission is required for FLOW 8 scribble import (BLE).")
            return
        }
        when (val action = pendingFlow8Action) {
            null -> Unit
            is PendingFlow8Action.ShowPairingDialog -> {
                pendingFlow8Action = null
                maybeAutoImportFlow8Scribble(action.mixerId)
            }
            is PendingFlow8Action.Import -> {
                pendingFlow8Action = null
                val profile = _uiState.value.mixers.firstOrNull { it.id == action.mixerId } ?: return
                viewModelScope.launch { importScribbleForMixer(profile) }
            }
        }
    }

    fun onAudioPermissionResult(granted: Boolean) {
        if (!granted) {
            pendingAudioAction = null
            showStatus("Microphone permission is required to record or monitor USB audio.")
            return
        }
        when (val action = pendingAudioAction) {
            null -> Unit
            is PendingAudioAction.Record -> {
                pendingAudioAction = null
                startRecordInternal(action.mixerId)
            }
            is PendingAudioAction.Monitor -> {
                pendingAudioAction = null
                startMonitorInternal(action.mixerId)
            }
            is PendingAudioAction.Resume -> {
                pendingAudioAction = null
                resumeRecordingInternal(action.mixerId, action.sessionDir)
            }
        }
    }

    fun dismissStatusToast() {
        _uiState.update { it.copy(statusToast = null) }
    }

    fun showSessionStatus(mixerId: String, message: String) {
        showStatus(message, mixerId)
    }

    private fun showStatus(message: String, mixerId: String? = null) {
        val name = (mixerId ?: _uiState.value.activeMixerId)?.let { id ->
            _uiState.value.mixers.firstOrNull { it.id == id }?.displayName
        }
        val text = when {
            name == null -> message
            message.startsWith("$name:") || message.startsWith("$name —") -> message
            else -> "$name: $message"
        }
        _uiState.update { it.copy(statusToast = StatusToast(text)) }
    }

    private fun shouldShowScribbleStatus(profile: MixerProfile, backgroundRefresh: Boolean): Boolean =
        !backgroundRefresh && profile.id == _uiState.value.activeMixerId

    private fun ensureAudioPermission(action: PendingAudioAction): Boolean {
        if (RecordAudioPermissions.hasPermission(appContext)) {
            pendingAudioAction = null
            return true
        }
        pendingAudioAction = action
        _audioPermissionRequests.tryEmit(Unit)
        val mixerId = when (action) {
            is PendingAudioAction.Record -> action.mixerId
            is PendingAudioAction.Monitor -> action.mixerId
            is PendingAudioAction.Resume -> action.mixerId
        }
        showStatus("Allow microphone access to record or monitor.", mixerId)
        return false
    }

    fun refreshUsbAndOutputs() {
        viewModelScope.launch {
            val usb = withContext(Dispatchers.IO) { enumerator.listUsbDevices() }
            val outputs = withContext(Dispatchers.IO) {
                AudioOutputDeviceLabel.labelAll(enumerator.listAudioOutputDevices())
            }
            val addable = usb.filter { !mixerStore.isAlreadyAdded(it) }
            _uiState.update {
                it.copy(availableUsbDevices = usb, addableUsbDevices = addable, outputDevices = outputs)
            }
            syncMixerUsbNames(usb)
            sessionClient.withManager { mgr ->
                mixerStore.listMixers().forEach { autoProbeMixer(it, mgr, usb) }
            }
            scheduleUsbRecoveryIfNeeded()
        }
    }

    fun showAddMixerDialog(show: Boolean) {
        _uiState.update { it.copy(showAddMixerDialog = show) }
        if (show) refreshUsbAndOutputs()
    }

    fun addMixer(descriptor: UsbAudioDeviceDescriptor) {
        if (!mixerStore.isBehringerAudioInterface(descriptor)) {
            showStatus("Not a supported audio interface.")
            return
        }
        val existing = mixerStore.findMatchingMixer(descriptor)
        if (existing != null) {
            _uiState.update {
                it.copy(
                    showAddMixerDialog = false,
                    activeMixerId = existing.id,
                )
            }
            showStatus("Already added.", existing.id)
            setActiveMixer(existing.id)
            return
        }
        val profile = mixerStore.addMixer(descriptor)
        loadMixers()
        sessionClient.withManager { mgr ->
            mgr.registerMixer(profile)
            autoProbeMixer(profile, mgr)
        }
        _uiState.update { it.copy(showAddMixerDialog = false) }
        setActiveMixer(profile.id)
    }

    fun removeMixer(id: String) {
        val removed = _uiState.value.mixers.firstOrNull { it.id == id } ?: return
        mixerStore.removeMixer(id)
        settings.clearAppModeForMixer(id)
        scribbleStripCache.delete(id)
        observedMixerIds.remove(id)
        sessionClient.withManager { it.unregisterMixer(id) }
        val remaining = mixerStore.listMixers()
        val nextActive = if (_uiState.value.activeMixerId == id) {
            remaining.firstOrNull()?.id
        } else {
            _uiState.value.activeMixerId?.takeIf { active -> remaining.any { it.id == active } }
                ?: remaining.firstOrNull()?.id
        }
        settings.lastActiveMixerId = nextActive
        _uiState.update {
            it.copy(
                mixers = remaining,
                sessionByMixer = it.sessionByMixer - id,
                activeMixerId = nextActive,
                statusToast = if (remaining.isEmpty()) null else StatusToast("Removed ${removed.displayName}."),
            )
        }
    }

    fun setActiveMixer(id: String) {
        settings.lastActiveMixerId = id
        sessionClient.withManager { it.setActiveMixer(id) }
        _uiState.update { it.copy(activeMixerId = id) }
        onFlow8MixerReady(id)
        onOscMixerReady(id)
    }

    fun setAppMode(mixerId: String, mode: AppMode) {
        settings.setAppModeForMixer(mixerId, mode)
        sessionClient.withManager { it.getOrCreate(mixerId).setAppMode(mode) }
    }

    fun refreshSoundcheckLibrary(mixerId: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).refreshSoundcheckLibrary() }
    }

    fun selectSoundcheckSession(mixerId: String, sessionDir: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).selectSoundcheckSession(sessionDir) }
    }

    fun toggleSoundcheckPlayback(mixerId: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).toggleSoundcheckPlayback() }
    }

    fun stopSoundcheck(mixerId: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).stopSoundcheck() }
    }

    fun seekSoundcheck(mixerId: String, positionSec: Float) {
        sessionClient.withManager { it.getOrCreate(mixerId).seekSoundcheck(positionSec) }
    }

    fun panSoundcheckView(mixerId: String, deltaSec: Float) {
        sessionClient.withManager { it.getOrCreate(mixerId).panSoundcheckView(deltaSec) }
    }

    fun zoomSoundcheckView(mixerId: String, scale: Float, focalSec: Float) {
        sessionClient.withManager { it.getOrCreate(mixerId).zoomSoundcheckView(scale, focalSec) }
    }

    fun setSoundcheckView(mixerId: String, viewStartSec: Float, viewWindowSec: Float) {
        sessionClient.withManager { it.getOrCreate(mixerId).setSoundcheckView(viewStartSec, viewWindowSec) }
    }

    fun setSoundcheckLoopRegion(mixerId: String, startSec: Float, endSec: Float) {
        sessionClient.withManager { it.getOrCreate(mixerId).setSoundcheckLoopRegion(startSec, endSec) }
    }

    fun toggleSoundcheckLoop(mixerId: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).toggleSoundcheckLoopButton() }
    }

    fun setPlaybackWaveformWindowSec(sec: Float) {
        val rounded = sec.coerceIn(30f, 600f).let { kotlin.math.round(it) }
        settings.playbackWaveformWindowSec = rounded
        sessionClient.withManager { mgr ->
            mgr.mixerIds().forEach { mgr.getOrCreate(it).updateSoundcheckViewConfig() }
        }
    }

    fun toggleArm(mixerId: String, index: Int) {
        sessionClient.withManager { mgr ->
            mgr.getOrCreate(mixerId).updateChannelStrip(index) { it.copy(armed = !it.armed) }
        }
    }

    fun toggleMonitor(mixerId: String, index: Int) {
        sessionClient.withManager { mgr ->
            mgr.getOrCreate(mixerId).updateChannelStrip(index) { it.copy(monitoring = !it.monitoring) }
        }
    }

    fun toggleSolo(mixerId: String, index: Int) {
        sessionClient.withManager { mgr ->
            val ctrl = mgr.getOrCreate(mixerId)
            val wasSolo = ctrl.state.value.channelStrips.firstOrNull { it.index == index }?.solo == true
            ctrl.state.value.channelStrips.forEach { strip ->
                ctrl.updateChannelStrip(strip.index) {
                    it.copy(solo = if (strip.index == index) !wasSolo else false)
                }
            }
        }
    }

    fun setMonitorOutput(mixerId: String, deviceId: Int) {
        sessionClient.withManager { it.getOrCreate(mixerId).setMonitorOutputDevice(deviceId) }
    }

    fun startMonitor(mixerId: String) {
        if (!ensureAudioPermission(PendingAudioAction.Monitor(mixerId))) return
        startMonitorInternal(mixerId)
    }

    private fun startMonitorInternal(mixerId: String) {
        if (!sessionClient.promoteForeground("Monitor")) {
            showStatus("Could not start monitor — allow microphone permission and try again.", mixerId)
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).startMonitoring() }
    }

    fun stopMonitor(mixerId: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).stopMonitoring() }
    }

    fun startRecord(mixerId: String) {
        if (!ensureAudioPermission(PendingAudioAction.Record(mixerId))) return
        startRecordInternal(mixerId)
    }

    private fun startRecordInternal(mixerId: String) {
        if (!sessionClient.promoteForeground("Recording")) {
            showStatus("Could not start recording — allow microphone permission and try again.", mixerId)
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).startRecording() }
    }

    fun stopRecord(mixerId: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).stopRecording() }
        clearPendingRecordingResumeIfNeeded(mixerId)
    }

    fun finalizeIncompleteRecording(mixerId: String) {
        val sessionDir = IncompleteRecordingStore.latestIncompleteSession(appContext, settings, mixerId)
            ?: return
        sessionClient.withManager { it.getOrCreate(mixerId).finalizeIncompleteRecording(sessionDir) }
        _uiState.update { it.copy(pendingRecordingResume = false) }
        showStatus("Incomplete recording finalized.", mixerId)
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (ScribbleImportSupport.supportsOsc(profile)) {
            onOscMixerReady(mixerId)
        } else if (ScribbleImportSupport.supportsFlow8(profile) && mixerId == _uiState.value.activeMixerId) {
            onFlow8MixerReady(mixerId)
        }
    }

    private fun resumeRecordingInternal(mixerId: String, sessionDir: java.io.File) {
        if (!sessionClient.promoteForeground("Recording resumed")) {
            showStatus("Could not resume recording — allow microphone permission and try again.", mixerId)
            return
        }
        val cachedLabels = scribbleStripCache.load(mixerId)
        sessionClient.withManager {
            it.getOrCreate(mixerId).resumeRecording(sessionDir, cachedLabels)
        }
        _uiState.update { it.copy(pendingRecordingResume = false) }
        showStatus("Recording resumed automatically.", mixerId)
        if (!cachedLabels.isNullOrEmpty()) {
            AppLogBuffer.append("I", "Scribble", "Applied cached strip labels on recording resume (${cachedLabels.size} channels)")
        }
        AppLogBuffer.append("I", "Record", "Resumed incomplete session at ${sessionDir.absolutePath}")
    }

    private fun maybeAutoResumeRecording(mixerId: String) {
        val session = _uiState.value.sessionByMixer[mixerId]
        if (session?.isRecording == true) return
        val sessionDir = IncompleteRecordingStore.latestIncompleteSession(appContext, settings, mixerId)
            ?: return
        if (!ensureAudioPermission(PendingAudioAction.Resume(mixerId, sessionDir))) return
        if (scribbleStripCache.hasCache(mixerId)) {
            applyCachedScribble(mixerId)
        }
        resumeRecordingInternal(mixerId, sessionDir)
    }

    private fun clearPendingRecordingResumeIfNeeded(mixerId: String) {
        if (!IncompleteRecordingStore.hasIncompleteRecording(appContext, settings, mixerId)) {
            _uiState.update { it.copy(pendingRecordingResume = false) }
        }
    }

    fun onUsbPermissionGranted(deviceName: String) {
        OmtLog.i("ViewModel", "USB permission granted for $deviceName")
        val usbDevice = enumerator.getUsbDevice(deviceName)
        sessionClient.withManager { mgr ->
            val usb = enumerator.listUsbDevices()
            mixerStore.listMixers()
                .filter { profile ->
                    profile.usbDeviceName == deviceName ||
                        (usbDevice != null &&
                            profile.vendorId == usbDevice.vendorId &&
                            profile.productId == usbDevice.productId)
                }
                .forEach { autoProbeMixer(it, mgr, usb) }
        }
        refreshUsbAndOutputs()
    }

    fun onUsbDetached(deviceName: String?) {
        sessionClient.withManager { it.onUsbDetached(deviceName) }
        refreshUsbAndOutputs()
    }

    fun onUsbAttached(device: UsbAudioDeviceDescriptor) {
        sessionClient.withManager { it.onUsbAttached(device) }
        refreshUsbAndOutputs()
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    fun showLogViewer(show: Boolean) {
        _uiState.update { it.copy(showLogViewer = show) }
    }

    fun setMonitorGain(gain: Float) {
        settings.monitorGainLinear = gain
        sessionClient.withManager { mgr ->
            _uiState.value.mixers.forEach { m ->
                mgr.getOrCreate(m.id).setMonitorGain(gain)
            }
        }
    }

    fun setHideArmButton(hide: Boolean) {
        settings.hideArmButton = hide
        _uiState.update { it.copy(hideArmButton = hide) }
    }

    fun setHideMonitorButton(hide: Boolean) {
        settings.hideMonitorButton = hide
        _uiState.update { it.copy(hideMonitorButton = hide) }
    }

    fun setHideSoloButton(hide: Boolean) {
        settings.hideSoloButton = hide
        _uiState.update { it.copy(hideSoloButton = hide) }
    }

    fun setShowWaveforms(show: Boolean) {
        settings.showWaveforms = show
        _uiState.update { it.copy(showWaveforms = show) }
    }

    fun setRecordWaveformWindowSec(sec: Float) {
        val rounded = sec.coerceIn(5f, 120f).let { kotlin.math.round(it) }
        settings.recordWaveformWindowSec = rounded
        sessionClient.withManager { mgr ->
            mgr.mixerIds().forEach { mgr.getOrCreate(it).updateWaveformConfig() }
        }
    }

    fun setStripNumberMode(mode: StripNumberMode) {
        settings.stripNumberMode = mode
        _uiState.update { it.copy(stripNumberMode = mode) }
    }

    fun setStripIconMode(mode: StripIconMode) {
        settings.stripIconMode = mode
        _uiState.update { it.copy(stripIconMode = mode) }
    }

    fun loadScribbleStrip(mixerId: String) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!ScribbleImportSupport.supports(profile)) {
            showStatus("Loading strip labels from this mixer is not supported yet.", mixerId)
            return
        }
        if (ScribbleImportSupport.supportsFlow8(profile)) {
            requestFlow8PairingDialog(mixerId)
            return
        }
        viewModelScope.launch {
            importScribbleForMixer(profile)
        }
    }

    fun confirmFlow8PairingImport() {
        val dialog = _uiState.value.flow8PairingDialog ?: return
        _uiState.update { it.copy(flow8PairingDialog = null) }
        val profile = _uiState.value.mixers.firstOrNull { it.id == dialog.mixerId } ?: return
        if (!ensureFlow8BluetoothPermission(PendingFlow8Action.Import(profile.id))) {
            return
        }
        viewModelScope.launch {
            importScribbleForMixer(profile)
        }
    }

    fun dismissFlow8PairingDialog() {
        _uiState.update { it.copy(flow8PairingDialog = null) }
    }

    private suspend fun importScribbleForMixer(profile: MixerProfile, backgroundRefresh: Boolean = false) {
        scribbleImportMutex.withLock {
            importScribbleForMixerLocked(profile, backgroundRefresh)
        }
    }

    private suspend fun importScribbleForMixerLocked(profile: MixerProfile, backgroundRefresh: Boolean = false) {
        val flow8 = ScribbleImportSupport.supportsFlow8(profile)
        val previousFingerprint = if (backgroundRefresh) scribbleStripCache.loadFingerprint(profile.id) else null
        if (shouldShowScribbleStatus(profile, backgroundRefresh)) {
            showStatus(
                if (flow8) {
                    Flow8BleScribbleImporter.PAIRING_SCANNING_MESSAGE
                } else {
                    "Loading strip labels from mixer over OSC/LAN…"
                },
                profile.id,
            )
        }
        try {
            val result: Result<Pair<MixerProfile, List<org.openmultitrack.mixer.behringer.UsbChannelScribble>>> = when {
                flow8 -> withContext(Dispatchers.IO) {
                    Flow8BleScribbleImporter(
                        context = appContext,
                        onStatus = { status ->
                            if (profile.id == _uiState.value.activeMixerId) {
                                showStatus(status, profile.id)
                            }
                        },
                    ).fetchChannelLabels().map { profile to it }
                }
                ScribbleImportSupport.supportsOsc(profile) -> {
                    if (shouldShowScribbleStatus(profile, backgroundRefresh)) {
                        showStatus("Searching for mixer on LAN (OSC)…", profile.id)
                    }
                    val host = withContext(Dispatchers.IO) {
                        val saved = profile.oscHost
                        saved?.let { OscLanDiscovery.probeMixerAt(appContext, it, timeoutMs = 2000) }
                            ?: OscLanDiscovery.discoverMixerIp(
                                appContext,
                                preferHost = saved,
                                timeoutMs = 20_000,
                            )
                    }
                    if (host == null) {
                        OmtLog.w("ViewModel", "OSC discovery failed for ${profile.displayName}")
                        if (shouldShowScribbleStatus(profile, backgroundRefresh)) {
                            showStatus(
                                "Not found on LAN (OSC). Same Wi‑Fi as mixer? See Menu → Log viewer.",
                                profile.id,
                            )
                        }
                        return
                    }
                    OmtLog.i("ViewModel", "OSC discovery found $host for ${profile.displayName}")
                    withContext(Dispatchers.IO) {
                        Xr18ScribbleImporter().fetchUsbLabels(host).map { labels ->
                            profile.copy(oscHost = host) to labels
                        }
                    }
                }
                else -> Result.failure(IllegalStateException("Unsupported mixer"))
            }
            result.onSuccess { (updatedProfile, labels) ->
                val fingerprint = ScribbleStripCache.fingerprint(labels)
                val unchanged = backgroundRefresh && previousFingerprint == fingerprint
                if (!unchanged) {
                    sessionClient.withManager { mgr ->
                        mgr.getOrCreate(profile.id).applyScribbleLabels(labels)
                    }
                }
                scribbleStripCache.save(profile.id, labels)
                val saved = updatedProfile.copy(scribbleImported = true)
                mixerStore.saveMixer(saved)
                loadMixers()
                val named = labels.count { !it.name.isNullOrBlank() }
                if (unchanged) {
                    OmtLog.d("ViewModel", "OSC scribble unchanged for ${profile.id} ($named labels)")
                } else {
                    AppLogBuffer.append("I", "Scribble", "Imported $named channel labels for ${profile.displayName}")
                    if (shouldShowScribbleStatus(profile, backgroundRefresh)) {
                        showStatus("Loaded $named strip labels from mixer", profile.id)
                    }
                    OmtLog.i("ViewModel", "scribble imported $named labels for ${profile.id}")
                }
            }.onFailure { e ->
                OmtLog.e("ViewModel", "scribble import failed", e)
                if (shouldShowScribbleStatus(profile, backgroundRefresh)) {
                    showStatus(flow8ScribbleFailureMessage(e), profile.id)
                }
            }
        } catch (e: Exception) {
            OmtLog.e("ViewModel", "scribble import failed", e)
            if (shouldShowScribbleStatus(profile, backgroundRefresh)) {
                showStatus(
                    if (flow8) flow8ScribbleFailureMessage(e) else "Scribble import failed: ${e.message}",
                    profile.id,
                )
            }
        }
    }

    private fun flow8ScribbleFailureMessage(error: Throwable): String {
        val msg = error.message.orEmpty()
        if (msg.contains("pairing", ignoreCase = true) || msg.contains("not found", ignoreCase = true)) {
            return msg
        }
        if (msg.contains("GATT write failed", ignoreCase = true) || msg.contains("status=133")) {
            return "FLOW 8 pairing expired. MENU → PAIRING → PAIR APP, then try again."
        }
        return "FLOW 8 scribble import failed: $msg"
    }

    override fun onCleared() {
        sessionClient.unbind()
        super.onCleared()
    }

    private fun loadMixers() {
        val mixers = mixerStore.listMixers()
        val lastActive = settings.lastActiveMixerId
        val activeId = lastActive?.takeIf { id -> mixers.any { it.id == id } }
            ?: mixers.firstOrNull()?.id
        _uiState.update { it.copy(mixers = mixers, activeMixerId = activeId) }
    }

    /**
     * Registers mixers with the bound session service, subscribes to their state, and probes USB.
     * Must run after [AudioSessionClient.whenReady] — early [withManager] calls are no-ops while binding.
     */
    private fun attachToSessionManager(manager: org.openmultitrack.app.service.MultiMixerSessionManager) {
        val mixers = _uiState.value.mixers
        OmtLog.i("ViewModel", "attachToSessionManager mixers=${mixers.size}")
        mixers.forEach { profile ->
            manager.registerMixer(profile)
            val ctrl = manager.getOrCreate(profile.id)
            val mode = settings.appModeForMixer(profile.id)
            ctrl.setAppMode(mode)
            if (mode == AppMode.VIRTUAL_SOUNDCHECK) {
                ctrl.refreshSoundcheckLibrary()
            }
            observeMixerSession(profile.id, manager)
        }
        _uiState.value.activeMixerId?.let { activeId ->
            manager.setActiveMixer(activeId)
            onFlow8MixerReady(activeId)
            onOscMixerReady(activeId)
        }
    }

    private fun observeMixerSession(mixerId: String, manager: org.openmultitrack.app.service.MultiMixerSessionManager) {
        if (!observedMixerIds.add(mixerId)) return
        viewModelScope.launch {
            manager.getOrCreate(mixerId).state.collect { session ->
                _uiState.update { ui ->
                    ui.copy(sessionByMixer = ui.sessionByMixer + (mixerId to session))
                }
                val msg = session.statusMessage
                val prev = lastSessionStatusByMixer[mixerId]
                if (
                    msg != null &&
                    msg != prev &&
                    mixerId == _uiState.value.activeMixerId &&
                    session.warningMessage == null
                ) {
                    showSessionStatus(mixerId, msg)
                }
                lastSessionStatusByMixer[mixerId] = msg
            }
        }
    }

    private fun syncMixerUsbNames(usb: List<UsbAudioDeviceDescriptor>) {
        val updated = _uiState.value.mixers.map { profile ->
            val match = usb.firstOrNull { d ->
                d.vendorId == profile.vendorId && d.productId == profile.productId &&
                    (profile.serialNumber == null || d.serialNumber == profile.serialNumber)
            }
            if (match != null && match.deviceName != profile.usbDeviceName) {
                val saved = profile.copy(usbDeviceName = match.deviceName, productName = match.productName)
                mixerStore.saveMixer(saved)
                saved
            } else {
                profile
            }
        }
        if (updated != _uiState.value.mixers) {
            _uiState.update { it.copy(mixers = updated) }
        }
    }

    private fun autoProbeMixer(
        profile: MixerProfile,
        manager: org.openmultitrack.app.service.MultiMixerSessionManager,
        usbDevices: List<UsbAudioDeviceDescriptor> = _uiState.value.availableUsbDevices,
    ) {
        val usb = resolveUsbDevice(profile, usbDevices)
        if (usb == null) {
            OmtLog.w("ViewModel", "USB device not found for ${profile.displayName} (vid=${profile.vendorId} pid=${profile.productId})")
            showStatus("Not on USB — reconnecting automatically…", profile.id)
            return
        }
        val resolvedProfile = if (usb.deviceName != profile.usbDeviceName || usb.productName != profile.productName) {
            val updated = profile.copy(usbDeviceName = usb.deviceName, productName = usb.productName)
            mixerStore.saveMixer(updated)
            loadMixers()
            updated
        } else {
            profile
        }
        if (!enumerator.hasUsbPermission(usb.deviceName)) {
            OmtLog.w("ViewModel", "USB permission needed for ${resolvedProfile.displayName} (${usb.deviceName})")
            _usbPermissionRequests.tryEmit(usb.deviceName)
            showStatus("Allow USB access when prompted.", resolvedProfile.id)
            return
        }
        viewModelScope.launch {
            manager.getOrCreate(resolvedProfile.id).setProbing(true)
            AppLogBuffer.append("I", "Probe", "Auto-probing ${resolvedProfile.displayName} on ${usb.deviceName}")
            val result = withContext(Dispatchers.IO) { probeService.probe(usb) }
            manager.onProbeComplete(resolvedProfile.id, usb, result)
            OmtLog.i("ViewModel", "auto-probed ${resolvedProfile.displayName}")
            usbRecoveryJob?.cancel()
            val resumePending = IncompleteRecordingStore.hasIncompleteRecording(appContext, settings, resolvedProfile.id)
            _uiState.update { it.copy(pendingRecordingResume = resumePending) }
            if (resumePending) {
                AppLogBuffer.append("I", "Scribble", "Will apply cached labels when resuming incomplete recording")
                maybeAutoResumeRecording(resolvedProfile.id)
            } else if (ScribbleImportSupport.supportsOsc(resolvedProfile)) {
                onOscMixerReady(resolvedProfile.id)
            } else if (
                ScribbleImportSupport.supportsFlow8(resolvedProfile) &&
                resolvedProfile.id == _uiState.value.activeMixerId
            ) {
                onFlow8MixerReady(resolvedProfile.id)
            }
        }
    }

    private fun onFlow8MixerReady(mixerId: String) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!ScribbleImportSupport.supportsFlow8(profile)) return
        val session = _uiState.value.sessionByMixer[mixerId]
        if (session == null || session.probing || session.probe == null) {
            OmtLog.d("ViewModel", "defer FLOW 8 scribble until probe completes for ${profile.displayName}")
            return
        }
        if (scribbleStripCache.hasCache(mixerId)) {
            applyCachedScribble(mixerId)
            return
        }
        maybeAutoImportFlow8Scribble(mixerId)
    }

    private fun onOscMixerReady(mixerId: String) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!ScribbleImportSupport.supportsOsc(profile)) return
        val session = _uiState.value.sessionByMixer[mixerId]
        if (session == null || session.probing || session.probe == null) {
            OmtLog.d("ViewModel", "defer OSC scribble until probe completes for ${profile.displayName}")
            return
        }
        val hadCache = scribbleStripCache.hasCache(mixerId)
        if (hadCache) {
            applyCachedScribble(mixerId)
        }
        maybeBackgroundRefreshOscScribble(mixerId, quiet = hadCache)
    }

    private fun applyCachedScribble(mixerId: String) {
        val labels = scribbleStripCache.load(mixerId) ?: return
        sessionClient.withManager { mgr ->
            mgr.getOrCreate(mixerId).applyScribbleLabels(labels)
        }
        OmtLog.i("ViewModel", "applied cached scribble ($mixerId, ${labels.size} channels)")
        AppLogBuffer.append("I", "Scribble", "Loaded cached strip labels (${labels.size} channels)")
    }

    private fun maybeBackgroundRefreshOscScribble(mixerId: String, quiet: Boolean) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!ScribbleImportSupport.supportsOsc(profile)) return
        if (IncompleteRecordingStore.hasIncompleteRecording(appContext, settings, mixerId)) {
            AppLogBuffer.append("I", "Scribble", "Skipped OSC scribble refresh — incomplete recording to resume")
            return
        }
        val session = _uiState.value.sessionByMixer[mixerId] ?: return
        if (!canAutoImportScribble(session)) {
            OmtLog.i("ViewModel", "skipped OSC scribble refresh — mixer is recording or soundchecking")
            return
        }
        viewModelScope.launch {
            importScribbleForMixer(profile, backgroundRefresh = quiet)
        }
    }

    private fun requestFlow8PairingDialog(mixerId: String) {
        if (!ensureFlow8BluetoothPermission(PendingFlow8Action.ShowPairingDialog(mixerId))) {
            return
        }
        _uiState.update { it.copy(flow8PairingDialog = Flow8PairingDialogState(mixerId)) }
    }

    private fun ensureFlow8BluetoothPermission(pending: PendingFlow8Action? = null): Boolean {
        if (Flow8BlePermissions.hasAll(appContext)) {
            pendingFlow8Action = null
            return true
        }
        pendingFlow8Action = pending
        _bluetoothPermissionRequests.tryEmit(Unit)
        val mixerId = when (pending) {
            is PendingFlow8Action.ShowPairingDialog -> pending.mixerId
            is PendingFlow8Action.Import -> pending.mixerId
            null -> _uiState.value.activeMixerId
        }
        showStatus("Allow Bluetooth access for FLOW 8 scribble import (BLE).", mixerId)
        return false
    }

    private fun maybeAutoImportFlow8Scribble(mixerId: String) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!ScribbleImportSupport.supportsFlow8(profile)) return
        if (scribbleStripCache.hasCache(mixerId)) return
        if (!ensureFlow8BluetoothPermission(PendingFlow8Action.ShowPairingDialog(mixerId))) {
            return
        }
        if (IncompleteRecordingStore.hasIncompleteRecording(appContext, settings, mixerId)) {
            AppLogBuffer.append("I", "Scribble", "Skipped FLOW 8 auto-import — incomplete recording to resume")
            return
        }
        val session = _uiState.value.sessionByMixer[mixerId] ?: return
        if (!canAutoImportScribble(session)) {
            OmtLog.i("ViewModel", "skipped FLOW 8 auto-import — mixer is recording or soundchecking")
            return
        }
        if (_uiState.value.flow8PairingDialog?.mixerId == mixerId) return
        _uiState.update { it.copy(flow8PairingDialog = Flow8PairingDialogState(mixerId)) }
    }

    private fun canAutoImportScribble(session: MixerSessionUiState): Boolean {
        if (session.isRecording) return false
        if (session.transportState == TransportState.RECORDING ||
            session.transportState == TransportState.RECORDING_DEGRADED
        ) {
            return false
        }
        if (session.appMode == AppMode.VIRTUAL_SOUNDCHECK) {
            if (session.isPlaying || session.transportState == TransportState.PLAYING) return false
        }
        return true
    }

    private fun resolveUsbDevice(
        profile: MixerProfile,
        usbDevices: List<UsbAudioDeviceDescriptor>,
    ): UsbAudioDeviceDescriptor? = enumerator.findMatchingDevice(
        vendorId = profile.vendorId,
        productId = profile.productId,
        serialNumber = profile.serialNumber,
        preferredDeviceName = profile.usbDeviceName,
    ) ?: usbDevices.firstOrNull { device ->
        device.vendorId == profile.vendorId &&
            device.productId == profile.productId &&
            (profile.serialNumber == null || device.serialNumber == profile.serialNumber)
    }

    private fun scheduleUsbRecoveryIfNeeded() {
        val needsRecovery = mixerStore.listMixers().any { profile ->
            val session = _uiState.value.sessionByMixer[profile.id]
            val stripsEmpty = session?.channelStrips.isNullOrEmpty()
            val usbMissing = resolveUsbDevice(profile, _uiState.value.availableUsbDevices) == null
            stripsEmpty || usbMissing
        }
        if (!needsRecovery) {
            usbRecoveryJob?.cancel()
            return
        }
        if (usbRecoveryJob?.isActive == true) return
        usbRecoveryJob = viewModelScope.launch {
            repeat(10) { attempt ->
                if (!isActive) return@launch
                delay(2_000)
                OmtLog.i("ViewModel", "USB auto-recovery attempt ${attempt + 1}/10")
                val usb = withContext(Dispatchers.IO) { enumerator.listUsbDevices() }
                _uiState.update { it.copy(availableUsbDevices = usb) }
                syncMixerUsbNames(usb)
                sessionClient.withManager { mgr ->
                    mixerStore.listMixers().forEach { autoProbeMixer(it, mgr, usb) }
                }
                val recovered = mixerStore.listMixers().all { profile ->
                    val session = _uiState.value.sessionByMixer[profile.id]
                    !session?.channelStrips.isNullOrEmpty()
                }
                if (recovered) {
                    dismissStatusToast()
                    return@launch
                }
            }
            showStatus("USB mixer still not ready — unplug and replug the cable, then wait a few seconds.")
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val enumerator = UsbAudioEnumerator(appContext)
                    return MainViewModel(
                        appContext,
                        enumerator,
                        UsbAudioProbeService(enumerator),
                        MixerDeviceStore(appContext),
                        ScribbleStripCache(appContext),
                        AppSettingsStore(appContext),
                        AudioSessionClient(appContext),
                    ) as T
                }
            }
    }
}
