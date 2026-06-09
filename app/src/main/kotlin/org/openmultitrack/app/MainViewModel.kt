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
import org.openmultitrack.app.data.MixerRoutingStore
import org.openmultitrack.app.data.ScribbleStripCache
import org.openmultitrack.app.service.AudioSessionClient
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.service.SoundcheckSessionItem
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode
import org.openmultitrack.domain.session.TransportState
import org.openmultitrack.usb.AudioOutputDeviceLabel
import org.openmultitrack.usb.LabeledAudioDevice
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.app.audio.RecordAudioPermissions
import org.openmultitrack.app.scribble.Flow8BlePermissions
import org.openmultitrack.app.device.DevicePrerequisites
import org.openmultitrack.app.device.PrerequisiteItem
import org.openmultitrack.app.ui.daw.StatusToast
import org.openmultitrack.domain.mixer.MixerRoutingConfig
import org.openmultitrack.domain.remote.RemoteConnectionState
import org.openmultitrack.domain.remote.RemotePairedHost
import org.openmultitrack.domain.remote.RemoteRole
import org.openmultitrack.remote.RemoteDiscoveredHost
import org.json.JSONObject
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

data class SoundcheckLoadPromptState(
    val mixerId: String,
    val sessionDir: String,
)

data class DawUiState(
    val mixers: List<MixerProfile> = emptyList(),
    val activeMixerId: String? = null,
    val sessionByMixer: Map<String, MixerSessionUiState> = emptyMap(),
    val outputDevices: List<LabeledAudioDevice> = emptyList(),
    val availableUsbDevices: List<UsbAudioDeviceDescriptor> = emptyList(),
    val addableUsbDevices: List<UsbAudioDeviceDescriptor> = emptyList(),
    val showAddMixerDialog: Boolean = false,
    val showMixerPicker: Boolean = false,
    val mixerSettingsMixerId: String? = null,
    val showRemoteControlSheet: Boolean = false,
    val mixerRoutingById: Map<String, MixerRoutingConfig> = emptyMap(),
    val showSettings: Boolean = false,
    val showLogViewer: Boolean = false,
    val flow8PairingDialog: Flow8PairingDialogState? = null,
    val statusToast: StatusToast? = null,
    val prerequisites: List<PrerequisiteItem> = emptyList(),
    val hideArmButton: Boolean = false,
    val hideMonitorButton: Boolean = false,
    val hideSoloButton: Boolean = false,
    val hideRoutingBadges: Boolean = false,
    val showWaveforms: Boolean = true,
    val showVuMeters: Boolean = true,
    val monitorGainLinear: Float = 2.5f,
    val recordWaveformWindowSec: Float = 15f,
    val playbackWaveformWindowSec: Float = 180f,
    val stripNumberMode: StripNumberMode = StripNumberMode.HIDE_WHEN_LABELED,
    val stripIconMode: StripIconMode = StripIconMode.SHOW,
    /** mixerId → sessionDir for recordings interrupted by an unexpected app exit. */
    val interruptedRecordings: Map<String, String> = emptyMap(),
    /** mixerId → status text while auto-resuming an interrupted recording. */
    val interruptedRecordingRecovery: Map<String, String> = emptyMap(),
    val remoteRole: RemoteRole = RemoteRole.OFF,
    val remoteConnectionState: RemoteConnectionState = RemoteConnectionState.DISCONNECTED,
    val remoteHostName: String? = null,
    val remoteConnectedHost: String? = null,
    val remoteLocalIp: String? = null,
    val remoteDiscoveredHosts: List<RemoteDiscoveredHost> = emptyList(),
    val remotePairedHosts: List<RemotePairedHost> = emptyList(),
    val remotePairingUri: String? = null,
    val remotePairingPin: String? = null,
    val remoteHostDeviceId: String? = null,
    val remoteError: String? = null,
    val remoteConnectedClientCount: Int = 0,
    val soundcheckLoadPrompt: SoundcheckLoadPromptState? = null,
    val promptLoadSoundcheckAfterRecord: Boolean = true,
    val showRecordingStorageInfoButton: Boolean = true,
    val autoShowRecordingStorageTooltip: Boolean = true,
)

fun hasNewerRecordingThanSelected(
    sessions: List<SoundcheckSessionItem>,
    lastSelectedDir: String?,
): Boolean {
    if (sessions.isEmpty()) return false
    val newest = sessions.maxByOrNull { it.startedAtEpochMs } ?: return false
    val lastSelected = lastSelectedDir?.let { dir -> sessions.firstOrNull { it.sessionDir == dir } }
    return when {
        lastSelected == null -> newest.startedAtEpochMs > 0L
        else -> newest.startedAtEpochMs > lastSelected.startedAtEpochMs
    }
}

class MainViewModel(
    private val appContext: Context,
    private val enumerator: UsbAudioEnumerator,
    private val probeService: UsbAudioProbeService,
    private val mixerStore: MixerDeviceStore,
    private val scribbleStripCache: ScribbleStripCache,
    private val settings: AppSettingsStore,
    private val routingStore: MixerRoutingStore,
    private val sessionClient: AudioSessionClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DawUiState(
            hideArmButton = settings.hideArmButton,
            hideMonitorButton = settings.hideMonitorButton,
            hideSoloButton = settings.hideSoloButton,
            hideRoutingBadges = settings.hideRoutingBadges,
            showWaveforms = settings.showWaveforms,
            showVuMeters = settings.showVuMeters,
            monitorGainLinear = settings.monitorGainLinear,
            recordWaveformWindowSec = settings.recordWaveformWindowSec,
            playbackWaveformWindowSec = settings.playbackWaveformWindowSec,
            stripNumberMode = settings.stripNumberMode,
            stripIconMode = settings.stripIconMode,
            promptLoadSoundcheckAfterRecord = settings.promptLoadSoundcheckAfterRecord,
            showRecordingStorageInfoButton = settings.showRecordingStorageInfoButton,
            autoShowRecordingStorageTooltip = settings.autoShowRecordingStorageTooltip,
        ),
    )
    val uiState: StateFlow<DawUiState> = _uiState.asStateFlow()

    private val observedMixerIds = mutableSetOf<String>()
    private val scribbleImportMutex = Mutex()
    private var usbRecoveryJob: Job? = null
    private val interruptedRecoveryJobs = mutableMapOf<String, Job>()
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
    private val wasRecordingByMixer = mutableMapOf<String, Boolean>()
    /** Mixer IDs where this device initiated stop-record and should show the soundcheck load prompt. */
    private val soundcheckPromptPending = mutableSetOf<String>()

    init {
        sessionClient.onManagerLost {
            observedMixerIds.clear()
            sessionAttached = false
            OmtLog.w("ViewModel", "session service lost — will re-attach on reconnect")
        }
        sessionClient.bind()
        if (settings.remoteRole != RemoteRole.CLIENT) {
            loadMixers()
        }
        sessionClient.whenReady { manager ->
            if (!sessionAttached) {
                attachToSessionManager(manager)
                sessionAttached = true
            }
            viewModelScope.launch {
                manager.activeMixerId.collect { id ->
                    if (!isRemoteClient()) {
                        _uiState.update { it.copy(activeMixerId = id) }
                    }
                }
            }
            attachRemoteControl()
            refreshUsbAndOutputs()
        }
    }

    private fun isRemoteClient(): Boolean =
        sessionClient.getRemoteControl()?.isRemoteClientConnected() == true

    /**
     * Shows the post-record soundcheck prompt only after recording has fully stopped.
     * The pending flag must not be cleared on intermediate session updates (e.g. waveform ticks
     * while [MixerSessionUiState.isRecording] is still true right after the user taps Stop).
     */
    private fun maybeShowSoundcheckLoadPrompt(
        mixerId: String,
        session: MixerSessionUiState,
    ): SoundcheckLoadPromptState? {
        if (mixerId !in soundcheckPromptPending) return null
        if (session.isRecording) return null
        if (!settings.promptLoadSoundcheckAfterRecord) return null
        val sessionDir = session.lastRecordingPath ?: return null
        if (session.appMode != AppMode.MULTITRACK_RECORD) return null
        soundcheckPromptPending.remove(mixerId)
        return SoundcheckLoadPromptState(mixerId = mixerId, sessionDir = sessionDir)
    }

    private fun remoteCommand(command: String, payload: JSONObject = JSONObject()) {
        sessionClient.withRemoteControl { remote ->
            if (remote.isRemoteClientConnected()) {
                remote.sendCommand(command, payload)
            }
        }
    }

    private fun attachRemoteControl() {
        val remote = sessionClient.getRemoteControl() ?: return
        remote.refreshPairingState()
        if (remote.state.value.role != settings.remoteRole && !remote.isRemoteClientConnected()) {
            remote.applyRole(settings.remoteRole)
        }
        viewModelScope.launch {
            remote.state.collect { remoteState -> applyRemoteState(remoteState) }
        }
    }

    private fun applyRemoteState(remoteState: org.openmultitrack.app.remote.RemoteControlUiState) {
        val previousRole = _uiState.value.remoteRole
        _uiState.update { ui ->
            val base = ui.copy(
                remoteRole = remoteState.role,
                remoteConnectionState = remoteState.connectionState,
                remoteHostName = remoteState.hostName,
                remoteConnectedHost = remoteState.connectedHost,
                remoteLocalIp = remoteState.localHostIp,
                remoteDiscoveredHosts = remoteState.discoveredHosts,
                remotePairedHosts = remoteState.pairedHosts,
                remotePairingUri = remoteState.pairingUri,
                remotePairingPin = remoteState.pairingPin,
                remoteHostDeviceId = remoteState.hostDeviceId,
                remoteError = remoteState.errorMessage,
                remoteConnectedClientCount = remoteState.connectedClientCount,
            )
            val mirrorReady = remoteState.mixers.isNotEmpty() || remoteState.sessionByMixer.isNotEmpty()
            var soundcheckPrompt: SoundcheckLoadPromptState? = ui.soundcheckLoadPrompt
            if (remoteState.role == RemoteRole.CLIENT && mirrorReady) {
                remoteState.sessionByMixer.forEach { (mixerId, session) ->
                    wasRecordingByMixer[mixerId] = session.isRecording
                    maybeShowSoundcheckLoadPrompt(mixerId, session)?.let { prompt ->
                        soundcheckPrompt = prompt
                    }
                }
            }
            when {
                remoteState.role == RemoteRole.CLIENT && mirrorReady -> {
                    val settingsPatch = remoteState.uiSettings
                    base.copy(
                        mixers = remoteState.mixers,
                        activeMixerId = remoteState.activeMixerId
                            ?: remoteState.mixers.firstOrNull()?.id,
                        sessionByMixer = remoteState.sessionByMixer,
                        hideArmButton = settingsPatch?.hideArmButton ?: ui.hideArmButton,
                        hideMonitorButton = settingsPatch?.hideMonitorButton ?: ui.hideMonitorButton,
                        hideSoloButton = settingsPatch?.hideSoloButton ?: ui.hideSoloButton,
                        hideRoutingBadges = ui.hideRoutingBadges,
                        showWaveforms = settingsPatch?.showWaveforms ?: ui.showWaveforms,
                        showVuMeters = settingsPatch?.showVuMeters ?: ui.showVuMeters,
                        monitorGainLinear = settingsPatch?.monitorGainLinear ?: ui.monitorGainLinear,
                        recordWaveformWindowSec = settingsPatch?.recordWaveformWindowSec
                            ?: ui.recordWaveformWindowSec,
                        playbackWaveformWindowSec = settingsPatch?.playbackWaveformWindowSec
                            ?: ui.playbackWaveformWindowSec,
                        stripNumberMode = settingsPatch?.stripNumberMode?.let {
                            StripNumberMode.entries.getOrElse(it) { StripNumberMode.HIDE_WHEN_LABELED }
                        } ?: ui.stripNumberMode,
                        stripIconMode = settingsPatch?.stripIconMode?.let {
                            StripIconMode.entries.getOrElse(it) { StripIconMode.SHOW }
                        } ?: ui.stripIconMode,
                        soundcheckLoadPrompt = soundcheckPrompt,
                    )
                }
                remoteState.role == RemoteRole.CLIENT &&
                    remoteState.connectionState == RemoteConnectionState.DISCONNECTED &&
                    !mirrorReady -> {
                    base.copy(
                        mixers = emptyList(),
                        activeMixerId = null,
                        sessionByMixer = emptyMap(),
                    )
                }
                else -> base
            }
        }
        if (previousRole == RemoteRole.CLIENT && remoteState.role == RemoteRole.OFF) {
            wasRecordingByMixer.clear()
            loadMixers()
        }
    }

    fun setRemoteRole(role: RemoteRole) {
        settings.remoteRole = role
        sessionClient.withRemoteControl { it.applyRole(role) }
        if (role != RemoteRole.CLIENT) {
            loadMixers()
        }
    }

    fun discoverRemoteHosts() {
        sessionClient.withRemoteControl { it.discoverHosts() }
    }

    fun connectRemoteHost(host: RemoteDiscoveredHost) {
        sessionClient.withRemoteControl { it.connectToHost(host) }
    }

    fun disconnectRemote() {
        sessionClient.withRemoteControl { it.exitRemoteClientMode() }
        wasRecordingByMixer.clear()
        soundcheckPromptPending.clear()
        loadMixers()
    }

    fun exitRemoteMode() = disconnectRemote()

    fun showMixerPicker(show: Boolean) {
        _uiState.update { it.copy(showMixerPicker = show) }
    }

    fun showMixerSettings(mixerId: String?) {
        _uiState.update { it.copy(mixerSettingsMixerId = mixerId) }
    }

    fun showRemoteControlSheet(show: Boolean) {
        if (show) {
            sessionClient.withRemoteControl { it.refreshPairingState() }
        }
        _uiState.update { it.copy(showRemoteControlSheet = show) }
    }

    fun enterRemoteClientMode() {
        sessionClient.withRemoteControl { it.enterRemoteClientMode() }
        _uiState.update { it.copy(showRemoteControlSheet = true) }
    }

    fun enableRemoteHosting() {
        wasRecordingByMixer.clear()
        sessionClient.withRemoteControl {
            it.applyRole(RemoteRole.HOST)
            it.refreshPairingState()
        }
        loadMixers()
    }

    fun pairRemoteFromQr(uri: String) {
        sessionClient.withRemoteControl { it.pairFromQr(uri) }
    }

    fun connectRemoteManual(host: String, pin: String) {
        sessionClient.withRemoteControl { it.connectManual(host, pin) }
    }

    fun saveMixerRouting(mixerId: String, config: MixerRoutingConfig) {
        routingStore.save(mixerId, config)
        _uiState.update { it.copy(mixerRoutingById = routingStore.loadAll()) }
        sessionClient.withManager { it.getOrCreate(mixerId).setRouting(config) }
    }

    fun updateChannelInputSource(mixerId: String, logicalIndex: Int, usbIndex: Int) {
        val updated = routingStore.get(mixerId).copy(
            inputMap = routingStore.get(mixerId).inputMap + (logicalIndex to usbIndex),
        )
        saveMixerRouting(mixerId, updated)
    }

    fun updateChannelOutputTarget(mixerId: String, logicalIndex: Int, usbIndex: Int) {
        val updated = routingStore.get(mixerId).copy(
            outputMap = routingStore.get(mixerId).outputMap + (logicalIndex to usbIndex),
        )
        saveMixerRouting(mixerId, updated)
    }

    fun setChannelHidden(mixerId: String, logicalIndex: Int, soundcheckMode: Boolean, hidden: Boolean) {
        val current = routingStore.get(mixerId)
        val updated = if (soundcheckMode) {
            current.copy(
                hiddenSoundcheck = if (hidden) {
                    current.hiddenSoundcheck + logicalIndex
                } else {
                    current.hiddenSoundcheck - logicalIndex
                },
            )
        } else {
            current.copy(
                hiddenRecord = if (hidden) {
                    current.hiddenRecord + logicalIndex
                } else {
                    current.hiddenRecord - logicalIndex
                },
            )
        }
        saveMixerRouting(mixerId, updated)
    }

    fun routingFor(mixerId: String): MixerRoutingConfig =
        _uiState.value.mixerRoutingById[mixerId] ?: MixerRoutingConfig()

    fun onAppResumed() {
        refreshUsbAndOutputs(recordingSafe = true)
        refreshPrerequisites()
    }

    fun refreshPrerequisites() {
        val items = DevicePrerequisites.unmet(appContext, _uiState.value.mixers)
        _uiState.update { it.copy(prerequisites = items) }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            showStatus("Location permission is required for FLOW 8 Bluetooth scan on this Android version.")
        }
        refreshPrerequisites()
    }

    fun onBluetoothPermissionsResult(granted: Boolean) {
        if (!granted) {
            pendingFlow8Action = null
            refreshPrerequisites()
            return
        }
        refreshPrerequisites()
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
            refreshPrerequisites()
            return
        }
        refreshPrerequisites()
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
                if (scribbleStripCache.hasCache(action.mixerId)) {
                    applyCachedScribble(action.mixerId)
                }
                resumeRecordingInternal(action.mixerId, action.sessionDir)
                if (_uiState.value.sessionByMixer[action.mixerId]?.isRecording != true) {
                    scheduleInterruptedRecordingRecovery(action.mixerId)
                }
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

    fun refreshUsbAndOutputs(recordingSafe: Boolean = false, scanAllUsb: Boolean = false) {
        viewModelScope.launch {
            val profiles = mixerStore.listMixers()
            val usb = withContext(Dispatchers.IO) {
                if (scanAllUsb || profiles.isEmpty()) {
                    enumerator.listUsbDevices()
                } else {
                    enumerator.listDevicesForProfiles(profiles)
                }
            }
            val outputs = withContext(Dispatchers.IO) {
                AudioOutputDeviceLabel.labelAll(enumerator.listAudioOutputDevices())
            }
            val addable = if (scanAllUsb || _uiState.value.showAddMixerDialog) {
                withContext(Dispatchers.IO) { enumerator.listUsbDevices() }
                    .filter { !mixerStore.isAlreadyAdded(it) }
            } else {
                _uiState.value.addableUsbDevices
            }
            _uiState.update {
                it.copy(availableUsbDevices = usb, addableUsbDevices = addable, outputDevices = outputs)
            }
            syncMixerUsbNames(usb)
            sessionClient.withManager { mgr ->
                val recordingActive = isAnyMixerRecording(mgr)
                if (recordingSafe && recordingActive) {
                    mixerStore.listMixers().forEach { profile ->
                        refreshInterruptedRecording(profile.id, mgr)
                    }
                } else {
                    mixerStore.listMixers().forEach { autoProbeMixer(it, mgr, usb) }
                }
            }
            scheduleUsbRecoveryIfNeeded()
        }
    }

    fun showAddMixerDialog(show: Boolean) {
        _uiState.update { it.copy(showAddMixerDialog = show) }
        if (show) refreshUsbAndOutputs(scanAllUsb = true)
    }

    fun isSavedMixerUsbDevice(device: android.hardware.usb.UsbDevice): Boolean {
        val desc = org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor(
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            manufacturerName = device.manufacturerName,
            productName = device.productName,
            serialNumber = null,
            isLikelyBehringerMixer = org.openmultitrack.usb.BehringerUsbIdentifiers.isLikelyBehringerMixer(
                device.vendorId,
                device.productName,
            ),
            guessedModel = org.openmultitrack.usb.BehringerUsbIdentifiers.guessModel(device.productName),
            androidAudioDeviceId = null,
        )
        return mixerStore.findMatchingMixer(desc) != null
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
        routingStore.remove(id)
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
        if (isRemoteClient()) {
            remoteCommand("set_active_mixer", JSONObject().put("mixerId", id))
            return
        }
        settings.lastActiveMixerId = id
        sessionClient.withManager { it.setActiveMixer(id) }
        _uiState.update { it.copy(activeMixerId = id) }
        onFlow8MixerReady(id)
        onOscMixerReady(id)
    }

    fun setAppMode(mixerId: String, mode: AppMode) {
        if (isRemoteClient()) {
            remoteCommand(
                "set_app_mode",
                JSONObject().put("mixerId", mixerId).put("mode", mode.ordinal),
            )
            return
        }
        settings.setAppModeForMixer(mixerId, mode)
        sessionClient.withManager { mgr ->
            val ctrl = mgr.getOrCreate(mixerId)
            ctrl.setRouting(routingStore.get(mixerId))
            ctrl.setAppMode(mode)
            if (mode.isPlaybackMode) {
                ctrl.refreshSoundcheckLibrary()
            }
        }
    }

    fun loadRecordingIntoSoundcheck(mixerId: String, sessionDir: String) {
        dismissSoundcheckLoadPrompt()
        if (isRemoteClient()) {
            remoteCommand(
                "load_into_soundcheck",
                JSONObject()
                    .put("mixerId", mixerId)
                    .put("sessionDir", sessionDir),
            )
            return
        }
        loadRecordingIntoSoundcheckLocal(mixerId, sessionDir)
    }

    fun loadRecordingIntoSimplePlay(mixerId: String, sessionDir: String) {
        dismissSoundcheckLoadPrompt()
        if (isRemoteClient()) {
            remoteCommand(
                "load_into_simple_play",
                JSONObject()
                    .put("mixerId", mixerId)
                    .put("sessionDir", sessionDir),
            )
            return
        }
        loadRecordingIntoSimplePlayLocal(mixerId, sessionDir)
    }

    private fun loadRecordingIntoSoundcheckLocal(mixerId: String, sessionDir: String) {
        setAppMode(mixerId, AppMode.VIRTUAL_SOUNDCHECK)
        selectSoundcheckSession(mixerId, sessionDir)
        refreshSoundcheckLibrary(mixerId)
    }

    private fun loadRecordingIntoSimplePlayLocal(mixerId: String, sessionDir: String) {
        setAppMode(mixerId, AppMode.SIMPLE_PLAY)
        selectSoundcheckSession(mixerId, sessionDir)
        refreshSoundcheckLibrary(mixerId)
    }

    fun dismissSoundcheckLoadPrompt() {
        _uiState.update { it.copy(soundcheckLoadPrompt = null) }
    }

    fun setPromptLoadSoundcheckAfterRecord(enabled: Boolean) {
        settings.promptLoadSoundcheckAfterRecord = enabled
        _uiState.update { it.copy(promptLoadSoundcheckAfterRecord = enabled) }
    }

    fun setShowRecordingStorageInfoButton(enabled: Boolean) {
        settings.showRecordingStorageInfoButton = enabled
        _uiState.update { it.copy(showRecordingStorageInfoButton = enabled) }
    }

    fun setAutoShowRecordingStorageTooltip(enabled: Boolean) {
        settings.autoShowRecordingStorageTooltip = enabled
        _uiState.update { it.copy(autoShowRecordingStorageTooltip = enabled) }
    }

    fun renameSoundcheckSession(mixerId: String, sessionDir: String, newTitle: String) {
        if (isRemoteClient()) {
            remoteCommand(
                "rename_soundcheck_session",
                JSONObject()
                    .put("mixerId", mixerId)
                    .put("sessionDir", sessionDir)
                    .put("title", newTitle),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).renameSoundcheckSession(sessionDir, newTitle) }
    }

    fun deleteSoundcheckSession(mixerId: String, sessionDir: String) {
        if (isRemoteClient()) {
            remoteCommand(
                "delete_soundcheck_session",
                JSONObject()
                    .put("mixerId", mixerId)
                    .put("sessionDir", sessionDir),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).deleteSoundcheckSession(sessionDir) }
    }

    fun lastSelectedSoundcheckSession(mixerId: String): String? =
        settings.lastSelectedSoundcheckSession(mixerId)

    fun refreshSoundcheckLibrary(mixerId: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).refreshSoundcheckLibrary() }
    }

    fun selectSoundcheckSession(mixerId: String, sessionDir: String) {
        settings.setLastSelectedSoundcheckSession(mixerId, sessionDir)
        if (isRemoteClient()) {
            remoteCommand(
                "select_soundcheck",
                JSONObject().put("mixerId", mixerId).put("sessionDir", sessionDir),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).selectSoundcheckSession(sessionDir) }
    }

    fun toggleSoundcheckPlayback(mixerId: String) {
        if (isRemoteClient()) {
            val playing = _uiState.value.sessionByMixer[mixerId]?.isPlaying == true
            remoteCommand(
                if (playing) "pause_playback" else "play_playback",
                JSONObject().put("mixerId", mixerId),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).toggleSoundcheckPlayback() }
    }

    fun stopSoundcheck(mixerId: String) {
        if (isRemoteClient()) {
            remoteCommand("stop_playback", JSONObject().put("mixerId", mixerId))
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).stopSoundcheck() }
    }

    fun seekSoundcheck(mixerId: String, positionSec: Float) {
        if (isRemoteClient()) {
            remoteCommand(
                "seek",
                JSONObject().put("mixerId", mixerId).put("positionSec", positionSec.toDouble()),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).seekSoundcheck(positionSec) }
    }

    fun panSoundcheckView(mixerId: String, deltaSec: Float) {
        if (isRemoteClient()) {
            remoteCommand(
                "pan_soundcheck_view",
                JSONObject().put("mixerId", mixerId).put("deltaSec", deltaSec.toDouble()),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).panSoundcheckView(deltaSec) }
    }

    fun zoomSoundcheckView(mixerId: String, scale: Float, focalSec: Float) {
        if (isRemoteClient()) {
            remoteCommand(
                "zoom_soundcheck_view",
                JSONObject()
                    .put("mixerId", mixerId)
                    .put("scale", scale.toDouble())
                    .put("focalSec", focalSec.toDouble()),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).zoomSoundcheckView(scale, focalSec) }
    }

    fun setSoundcheckView(mixerId: String, viewStartSec: Float, viewWindowSec: Float) {
        if (isRemoteClient()) {
            remoteCommand(
                "set_soundcheck_view",
                JSONObject()
                    .put("mixerId", mixerId)
                    .put("viewStartSec", viewStartSec.toDouble())
                    .put("viewWindowSec", viewWindowSec.toDouble()),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).setSoundcheckView(viewStartSec, viewWindowSec) }
    }

    fun setSoundcheckLoopRegion(mixerId: String, startSec: Float, endSec: Float) {
        if (isRemoteClient()) {
            remoteCommand(
                "set_loop",
                JSONObject()
                    .put("mixerId", mixerId)
                    .put("action", "region")
                    .put("startSec", startSec.toDouble())
                    .put("endSec", endSec.toDouble()),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).setSoundcheckLoopRegion(startSec, endSec) }
    }

    fun toggleSoundcheckLoop(mixerId: String) {
        if (isRemoteClient()) {
            remoteCommand(
                "set_loop",
                JSONObject().put("mixerId", mixerId).put("action", "toggle"),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).toggleSoundcheckLoopButton() }
    }

    fun setSoundcheckLoopIn(mixerId: String) {
        if (isRemoteClient()) {
            remoteCommand(
                "set_loop",
                JSONObject().put("mixerId", mixerId).put("action", "in"),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).setSoundcheckLoopIn() }
    }

    fun setSoundcheckLoopOut(mixerId: String) {
        if (isRemoteClient()) {
            remoteCommand(
                "set_loop",
                JSONObject().put("mixerId", mixerId).put("action", "out"),
            )
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).setSoundcheckLoopOut() }
    }

    fun setPlaybackWaveformWindowSec(sec: Float) {
        val rounded = sec.coerceIn(30f, 600f).let { kotlin.math.round(it) }
        if (isRemoteClient()) {
            remoteCommand("set_settings", JSONObject().put("playbackWaveformWindowSec", rounded.toDouble()))
            _uiState.update { it.copy(playbackWaveformWindowSec = rounded) }
            return
        }
        settings.playbackWaveformWindowSec = rounded
        _uiState.update { it.copy(playbackWaveformWindowSec = rounded) }
        sessionClient.withManager { mgr ->
            mgr.mixerIds().forEach { mgr.getOrCreate(it).updateSoundcheckViewConfig() }
        }
    }

    fun toggleArm(mixerId: String, index: Int) {
        if (isRemoteClient()) {
            remoteCommand(
                "toggle_arm",
                JSONObject().put("mixerId", mixerId).put("index", index),
            )
            return
        }
        sessionClient.withManager { mgr ->
            mgr.getOrCreate(mixerId).updateChannelStrip(index) { it.copy(armed = !it.armed) }
        }
    }

    fun toggleMonitor(mixerId: String, index: Int) {
        if (isRemoteClient()) {
            remoteCommand(
                "toggle_monitor",
                JSONObject().put("mixerId", mixerId).put("index", index),
            )
            return
        }
        sessionClient.withManager { mgr ->
            mgr.getOrCreate(mixerId).updateChannelStrip(index) { it.copy(monitoring = !it.monitoring) }
        }
    }

    fun toggleSolo(mixerId: String, index: Int) {
        if (isRemoteClient()) {
            remoteCommand(
                "toggle_solo",
                JSONObject().put("mixerId", mixerId).put("index", index),
            )
            return
        }
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

    fun toggleMute(mixerId: String, index: Int) {
        if (isRemoteClient()) {
            remoteCommand(
                "toggle_mute",
                JSONObject().put("mixerId", mixerId).put("index", index),
            )
            return
        }
        sessionClient.withManager { mgr ->
            mgr.getOrCreate(mixerId).updateChannelStrip(index) { it.copy(muted = !it.muted) }
        }
    }

    fun setMonitorOutput(mixerId: String, deviceId: Int) {
        sessionClient.withManager { it.getOrCreate(mixerId).setMonitorOutputDevice(deviceId) }
    }

    fun startMonitor(mixerId: String) {
        if (isRemoteClient()) {
            remoteCommand("start_monitor", JSONObject().put("mixerId", mixerId))
            return
        }
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
        if (isRemoteClient()) {
            remoteCommand("stop_monitor", JSONObject().put("mixerId", mixerId))
            return
        }
        sessionClient.withManager { it.getOrCreate(mixerId).stopMonitoring() }
    }

    fun startRecord(mixerId: String) {
        if (isRemoteClient()) {
            remoteCommand("start_record", JSONObject().put("mixerId", mixerId))
            return
        }
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
        if (isRemoteClient()) {
            soundcheckPromptPending.add(mixerId)
            remoteCommand("stop_record", JSONObject().put("mixerId", mixerId))
            return
        }
        soundcheckPromptPending.add(mixerId)
        sessionClient.withManager { it.getOrCreate(mixerId).stopRecording() }
        clearInterruptedRecordingIfNeeded(mixerId)
    }

    fun finalizeIncompleteRecording(mixerId: String) {
        cancelInterruptedRecordingRecovery(mixerId)
        val sessionDir = _uiState.value.interruptedRecordings[mixerId]?.let { java.io.File(it) }
            ?: IncompleteRecordingStore.recoverableSession(appContext, settings, mixerId)
            ?: IncompleteRecordingStore.latestIncompleteSession(appContext, settings, mixerId)
            ?: return
        sessionClient.withManager { it.getOrCreate(mixerId).finalizeIncompleteRecording(sessionDir) }
        _uiState.update { it.copy(interruptedRecordings = it.interruptedRecordings - mixerId) }
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
        cancelInterruptedRecordingRecovery(mixerId)
        _uiState.update { it.copy(interruptedRecordings = it.interruptedRecordings - mixerId) }
        showStatus("Recording resumed.", mixerId)
        if (!cachedLabels.isNullOrEmpty()) {
            AppLogBuffer.append("I", "Scribble", "Applied cached strip labels on recording resume (${cachedLabels.size} channels)")
        }
        AppLogBuffer.append("I", "Record", "Resumed incomplete session at ${sessionDir.absolutePath}")
    }

    private fun refreshInterruptedRecording(
        mixerId: String,
        manager: org.openmultitrack.app.service.MultiMixerSessionManager? = null,
    ) {
        val activelyRecording = manager?.getOrCreate(mixerId)?.state?.value?.isRecording == true ||
            _uiState.value.sessionByMixer[mixerId]?.isRecording == true
        val sessionDir = if (activelyRecording) {
            null
        } else {
            IncompleteRecordingStore.recoverableSession(appContext, settings, mixerId)
        }
        _uiState.update { ui ->
            val updated = if (sessionDir != null) {
                ui.interruptedRecordings + (mixerId to sessionDir.absolutePath)
            } else {
                ui.interruptedRecordings - mixerId
            }
            ui.copy(interruptedRecordings = updated)
        }
        if (sessionDir != null) {
            scheduleInterruptedRecordingRecovery(mixerId)
        } else {
            cancelInterruptedRecordingRecovery(mixerId)
        }
    }

    private fun updateInterruptedRecoveryStatus(mixerId: String, message: String) {
        _uiState.update {
            it.copy(interruptedRecordingRecovery = it.interruptedRecordingRecovery + (mixerId to message))
        }
    }

    private fun cancelInterruptedRecordingRecovery(mixerId: String) {
        interruptedRecoveryJobs.remove(mixerId)?.cancel()
        _uiState.update {
            it.copy(interruptedRecordingRecovery = it.interruptedRecordingRecovery - mixerId)
        }
    }

    private fun scheduleInterruptedRecordingRecovery(mixerId: String) {
        if (isRemoteClient()) return
        if (IncompleteRecordingStore.recoverableSession(appContext, settings, mixerId) == null) return
        if (interruptedRecoveryJobs[mixerId]?.isActive == true) return
        interruptedRecoveryJobs[mixerId] = viewModelScope.launch {
            val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId }
                ?: mixerStore.listMixers().firstOrNull { it.id == mixerId }
                ?: return@launch
            updateInterruptedRecoveryStatus(
                mixerId,
                "Recording was interrupted. Resuming automatically…",
            )
            AppLogBuffer.append("I", "Record", "Auto-recovery started for interrupted recording ($mixerId)")
            while (isActive) {
                val sessionDir = IncompleteRecordingStore.recoverableSession(appContext, settings, mixerId)
                if (sessionDir == null) {
                    cancelInterruptedRecordingRecovery(mixerId)
                    return@launch
                }
                val session = _uiState.value.sessionByMixer[mixerId]
                if (session?.isRecording == true) {
                    cancelInterruptedRecordingRecovery(mixerId)
                    return@launch
                }
                val usbDevices = withContext(Dispatchers.IO) {
                    enumerator.listDevicesForProfiles(listOf(profile))
                }
                _uiState.update { it.copy(availableUsbDevices = usbDevices) }
                syncMixerUsbNames(usbDevices)
                val usb = resolveUsbDevice(profile, usbDevices)
                if (usb == null) {
                    updateInterruptedRecoveryStatus(
                        mixerId,
                        "Recording was interrupted. Waiting for USB mixer — reconnect the cable.",
                    )
                    delay(2_000)
                    continue
                }
                if (!enumerator.hasUsbPermission(usb.deviceName)) {
                    updateInterruptedRecoveryStatus(
                        mixerId,
                        "Recording was interrupted. Allow USB access when prompted.",
                    )
                    _usbPermissionRequests.tryEmit(usb.deviceName)
                    delay(2_000)
                    continue
                }
                if (!RecordAudioPermissions.hasPermission(appContext)) {
                    updateInterruptedRecoveryStatus(
                        mixerId,
                        "Recording was interrupted. Allow microphone access to resume.",
                    )
                    ensureAudioPermission(PendingAudioAction.Resume(mixerId, sessionDir))
                    delay(2_000)
                    continue
                }
                val probeReady = session?.probe != null && session.probing != true
                if (!probeReady) {
                    updateInterruptedRecoveryStatus(
                        mixerId,
                        "Recording was interrupted. Reconnecting to mixer…",
                    )
                    sessionClient.withManager { mgr ->
                        autoProbeMixer(profile, mgr, usbDevices)
                    }
                    var probed = false
                    repeat(20) {
                        if (!isActive) return@launch
                        delay(500)
                        val s = _uiState.value.sessionByMixer[mixerId]
                        if (s?.probe != null && s.probing != true) {
                            probed = true
                            return@repeat
                        }
                    }
                    if (!probed) {
                        delay(2_000)
                        continue
                    }
                }
                updateInterruptedRecoveryStatus(mixerId, "Recording was interrupted. Resuming…")
                if (scribbleStripCache.hasCache(mixerId)) {
                    applyCachedScribble(mixerId)
                }
                resumeRecordingInternal(mixerId, sessionDir)
                delay(1_000)
                if (_uiState.value.sessionByMixer[mixerId]?.isRecording == true) {
                    cancelInterruptedRecordingRecovery(mixerId)
                    return@launch
                }
                delay(2_000)
            }
        }
    }

    private fun isAnyMixerRecording(
        manager: org.openmultitrack.app.service.MultiMixerSessionManager? = null,
    ): Boolean {
        if (manager != null) {
            return mixerStore.listMixers().any { profile ->
                manager.getOrCreate(profile.id).state.value.isRecording
            }
        }
        return _uiState.value.sessionByMixer.values.any { it.isRecording }
    }

    private fun clearInterruptedRecordingIfNeeded(mixerId: String) {
        if (IncompleteRecordingStore.recoverableSession(appContext, settings, mixerId) == null) {
            _uiState.update { it.copy(interruptedRecordings = it.interruptedRecordings - mixerId) }
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
        refreshUsbAndOutputs(recordingSafe = isAnyMixerRecording())
        scheduleUsbRecoveryIfNeeded()
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
        if (isRemoteClient()) {
            remoteCommand("set_settings", JSONObject().put("monitorGainLinear", gain.toDouble()))
            _uiState.update { it.copy(monitorGainLinear = gain) }
            return
        }
        settings.monitorGainLinear = gain
        _uiState.update { it.copy(monitorGainLinear = gain) }
        sessionClient.withManager { mgr ->
            _uiState.value.mixers.forEach { m ->
                mgr.getOrCreate(m.id).setMonitorGain(gain)
            }
        }
    }

    fun setHideArmButton(hide: Boolean) {
        if (isRemoteClient()) {
            remoteCommand("set_settings", JSONObject().put("hideArmButton", hide))
        }
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

    fun setHideRoutingBadges(hide: Boolean) {
        settings.hideRoutingBadges = hide
        _uiState.update { it.copy(hideRoutingBadges = hide) }
    }

    fun setShowWaveforms(show: Boolean) {
        settings.showWaveforms = show
        _uiState.update { it.copy(showWaveforms = show) }
    }

    fun setShowVuMeters(show: Boolean) {
        settings.showVuMeters = show
        _uiState.update { it.copy(showVuMeters = show) }
        sessionClient.withManager { mgr ->
            mgr.scheduleVuMeterSync()
        }
    }

    fun setRecordWaveformWindowSec(sec: Float) {
        val rounded = sec.coerceIn(5f, 120f).let { kotlin.math.round(it) }
        if (isRemoteClient()) {
            remoteCommand("set_settings", JSONObject().put("recordWaveformWindowSec", rounded.toDouble()))
            _uiState.update { it.copy(recordWaveformWindowSec = rounded) }
            return
        }
        settings.recordWaveformWindowSec = rounded
        _uiState.update { it.copy(recordWaveformWindowSec = rounded) }
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
        interruptedRecoveryJobs.values.forEach { it.cancel() }
        interruptedRecoveryJobs.clear()
        sessionClient.unbind()
        super.onCleared()
    }

    private fun loadMixers() {
        val mixers = mixerStore.listMixers()
        val lastActive = settings.lastActiveMixerId
        val activeId = lastActive?.takeIf { id -> mixers.any { it.id == id } }
            ?: mixers.firstOrNull()?.id
        _uiState.update {
            it.copy(
                mixers = mixers,
                activeMixerId = activeId,
                mixerRoutingById = routingStore.loadAll(),
            )
        }
        refreshPrerequisites()
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
            ctrl.setRouting(routingStore.get(profile.id))
            ctrl.setAppMode(mode)
            if (mode.isPlaybackMode) {
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
                if (isRemoteClient()) return@collect
                wasRecordingByMixer[mixerId] = session.isRecording
                _uiState.update { ui ->
                    ui.copy(sessionByMixer = ui.sessionByMixer + (mixerId to session))
                }
                maybeShowSoundcheckLoadPrompt(mixerId, session)?.let { prompt ->
                    _uiState.update { it.copy(soundcheckLoadPrompt = prompt) }
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
        if (manager.getOrCreate(profile.id).state.value.isRecording) {
            val usb = resolveUsbDevice(profile, usbDevices)
            if (usb != null && enumerator.hasUsbPermission(usb.deviceName)) {
                OmtLog.i("ViewModel", "USB back while recording — reconnecting ${profile.displayName}")
                manager.onUsbAttached(usb)
            }
            refreshInterruptedRecording(profile.id, manager)
            return
        }
        val usb = resolveUsbDevice(profile, usbDevices)
        if (usb == null) {
            OmtLog.w("ViewModel", "USB device not found for ${profile.displayName} (vid=${profile.vendorId} pid=${profile.productId})")
            showStatus("Not on USB — reconnecting automatically…", profile.id)
            refreshInterruptedRecording(profile.id, manager)
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
            refreshInterruptedRecording(profile.id, manager)
            return
        }
        viewModelScope.launch {
            manager.getOrCreate(resolvedProfile.id).setProbing(true)
            AppLogBuffer.append("I", "Probe", "Auto-probing ${resolvedProfile.displayName} on ${usb.deviceName}")
            val result = withContext(Dispatchers.IO) { probeService.probe(usb) }
            manager.onProbeComplete(resolvedProfile.id, usb, result)
            OmtLog.i("ViewModel", "auto-probed ${resolvedProfile.displayName}")
            usbRecoveryJob?.cancel()
            refreshInterruptedRecording(resolvedProfile.id, manager)
            val resumePending = IncompleteRecordingStore.recoverableSession(
                appContext,
                settings,
                resolvedProfile.id,
            ) != null
            if (!resumePending) {
                if (ScribbleImportSupport.supportsOsc(resolvedProfile)) {
                    onOscMixerReady(resolvedProfile.id)
                } else if (
                    ScribbleImportSupport.supportsFlow8(resolvedProfile) &&
                    resolvedProfile.id == _uiState.value.activeMixerId
                ) {
                    onFlow8MixerReady(resolvedProfile.id)
                }
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
        if (IncompleteRecordingStore.recoverableSession(appContext, settings, mixerId) != null) {
            AppLogBuffer.append("I", "Scribble", "Skipped OSC scribble refresh — interrupted recording pending")
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
        if (Flow8BlePermissions.isBleReady(appContext)) {
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
        refreshPrerequisites()
        return false
    }

    private fun maybeAutoImportFlow8Scribble(mixerId: String) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!ScribbleImportSupport.supportsFlow8(profile)) return
        if (scribbleStripCache.hasCache(mixerId)) return
        if (!ensureFlow8BluetoothPermission(PendingFlow8Action.ShowPairingDialog(mixerId))) {
            return
        }
        if (IncompleteRecordingStore.recoverableSession(appContext, settings, mixerId) != null) {
            AppLogBuffer.append("I", "Scribble", "Skipped FLOW 8 auto-import — interrupted recording pending")
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
        if (session.appMode.isPlaybackMode) {
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
        val recordingActive = isAnyMixerRecording()
        val needsRecovery = mixerStore.listMixers().any { profile ->
            val session = _uiState.value.sessionByMixer[profile.id]
            val stripsEmpty = session?.channelStrips.isNullOrEmpty()
            val usbMissing = resolveUsbDevice(profile, _uiState.value.availableUsbDevices) == null
            val usbDegraded = session?.isUsbDegraded == true
            stripsEmpty || usbMissing || (recordingActive && usbDegraded)
        }
        if (!needsRecovery) {
            usbRecoveryJob?.cancel()
            return
        }
        if (usbRecoveryJob?.isActive == true) return
        usbRecoveryJob = viewModelScope.launch {
            val attempts = if (recordingActive) 30 else 10
            val delayMs = if (recordingActive) 1_000L else 2_000L
            repeat(attempts) { attempt ->
                if (!isActive) return@launch
                delay(delayMs)
                OmtLog.i("ViewModel", "USB auto-recovery attempt ${attempt + 1}/$attempts")
                val profiles = mixerStore.listMixers()
                val usb = withContext(Dispatchers.IO) {
                    if (recordingActive) {
                        enumerator.listDevicesForProfiles(profiles)
                    } else {
                        enumerator.listUsbDevices()
                    }
                }
                _uiState.update { it.copy(availableUsbDevices = usb) }
                syncMixerUsbNames(usb)
                sessionClient.withManager { mgr ->
                    mixerStore.listMixers().forEach { autoProbeMixer(it, mgr, usb) }
                }
                val recovered = mixerStore.listMixers().all { profile ->
                    val session = _uiState.value.sessionByMixer[profile.id]
                    if (recordingActive) {
                        session?.isUsbDegraded != true && !session?.channelStrips.isNullOrEmpty()
                    } else {
                        !session?.channelStrips.isNullOrEmpty()
                    }
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
                        MixerRoutingStore(appContext),
                        AudioSessionClient(appContext),
                    ) as T
                }
            }
    }
}
