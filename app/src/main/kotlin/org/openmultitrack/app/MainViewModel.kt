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
import org.openmultitrack.app.scribble.Flow8BlePermissions
import org.openmultitrack.app.scribble.Flow8BleScribbleImporter
import org.openmultitrack.app.scribble.IncompleteRecordingStore
import org.openmultitrack.app.scribble.OscLanDiscovery
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
    val globalStatus: String? = null,
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
    private var pendingFlow8Action: PendingFlow8Action? = null

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
            _uiState.update {
                it.copy(globalStatus = "Bluetooth permission is required for FLOW 8 scribble import.")
            }
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
            _uiState.update { it.copy(globalStatus = "Not a supported audio interface.") }
            return
        }
        val existing = mixerStore.findMatchingMixer(descriptor)
        if (existing != null) {
            _uiState.update {
                it.copy(
                    showAddMixerDialog = false,
                    activeMixerId = existing.id,
                    globalStatus = "${existing.displayName} is already added.",
                )
            }
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
                globalStatus = if (remaining.isEmpty()) null else "Removed ${removed.displayName}.",
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
        sessionClient.promoteForeground("Monitor")
        sessionClient.withManager { it.getOrCreate(mixerId).startMonitoring() }
    }

    fun stopMonitor(mixerId: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).stopMonitoring() }
    }

    fun startRecord(mixerId: String) {
        sessionClient.promoteForeground("Recording")
        sessionClient.withManager { it.getOrCreate(mixerId).startRecording() }
    }

    fun stopRecord(mixerId: String) {
        sessionClient.withManager { it.getOrCreate(mixerId).stopRecording() }
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

    fun setStripNumberMode(mode: StripNumberMode) {
        settings.stripNumberMode = mode
        _uiState.update { it.copy(stripNumberMode = mode) }
    }

    fun setStripIconMode(mode: StripIconMode) {
        settings.stripIconMode = mode
        _uiState.update { it.copy(stripIconMode = mode) }
    }

    fun refreshScribble(mixerId: String) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!supportsScribbleImport(profile)) {
            _uiState.update { it.copy(globalStatus = "Scribble import is not available for this mixer yet.") }
            return
        }
        if (supportsFlow8Scribble(profile)) {
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
        val flow8 = supportsFlow8Scribble(profile)
        val previousFingerprint = if (backgroundRefresh) scribbleStripCache.loadFingerprint(profile.id) else null
        if (!backgroundRefresh) {
            _uiState.update {
                it.copy(
                    globalStatus = if (flow8) {
                        Flow8BleScribbleImporter.PAIRING_SCANNING_MESSAGE
                    } else {
                        "Importing scribble strip…"
                    },
                )
            }
        }
        try {
            val result: Result<Pair<MixerProfile, List<org.openmultitrack.mixer.behringer.UsbChannelScribble>>> = when {
                flow8 -> withContext(Dispatchers.IO) {
                    Flow8BleScribbleImporter(
                        context = appContext,
                        onStatus = { status -> _uiState.update { ui -> ui.copy(globalStatus = status) } },
                    ).fetchChannelLabels().map { profile to it }
                }
                supportsOscScribble(profile) -> {
                    if (!backgroundRefresh) {
                        _uiState.update { it.copy(globalStatus = "Searching for mixer on LAN…") }
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
                        if (!backgroundRefresh) {
                            _uiState.update {
                                it.copy(
                                    globalStatus = "Mixer not found on LAN (OSC). Same Wi‑Fi as mixer? " +
                                        "Check Menu → Log viewer for OscDiscovery lines.",
                                )
                            }
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
                    if (!backgroundRefresh) {
                        _uiState.update { it.copy(globalStatus = "Scribble: $named channel labels") }
                    }
                    OmtLog.i("ViewModel", "scribble imported $named labels for ${profile.id}")
                }
            }.onFailure { e ->
                OmtLog.e("ViewModel", "scribble import failed", e)
                if (!backgroundRefresh) {
                    _uiState.update {
                        it.copy(globalStatus = flow8ScribbleFailureMessage(e))
                    }
                }
            }
        } catch (e: Exception) {
            OmtLog.e("ViewModel", "scribble import failed", e)
            if (!backgroundRefresh) {
                _uiState.update {
                    it.copy(globalStatus = if (flow8) flow8ScribbleFailureMessage(e) else "Scribble import failed: ${e.message}")
                }
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
            manager.getOrCreate(profile.id).setAppMode(settings.appModeForMixer(profile.id))
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
            _uiState.update {
                it.copy(
                    globalStatus = "${profile.displayName} not on USB — reconnecting automatically…",
                )
            }
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
            _uiState.update {
                it.copy(globalStatus = "Allow USB access for ${resolvedProfile.displayName} when prompted.")
            }
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
            _uiState.update { it.copy(pendingRecordingResume = resumePending, globalStatus = null) }
            if (resumePending) {
                AppLogBuffer.append("I", "Scribble", "Skipped auto-import — incomplete recording to resume")
            } else if (supportsOscScribble(resolvedProfile)) {
                onOscMixerReady(resolvedProfile.id)
            } else if (
                supportsFlow8Scribble(resolvedProfile) &&
                resolvedProfile.id == _uiState.value.activeMixerId
            ) {
                onFlow8MixerReady(resolvedProfile.id)
            }
        }
    }

    private fun onFlow8MixerReady(mixerId: String) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!supportsFlow8Scribble(profile)) return
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
        if (!supportsOscScribble(profile)) return
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
        if (!supportsOscScribble(profile)) return
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
        _uiState.update {
            it.copy(globalStatus = "Allow Bluetooth access for FLOW 8 scribble import.")
        }
        return false
    }

    private fun maybeAutoImportFlow8Scribble(mixerId: String) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!supportsFlow8Scribble(profile)) return
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
                    _uiState.update { it.copy(globalStatus = null) }
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    globalStatus = "USB mixer still not ready. Unplug and replug the USB cable, then wait a few seconds.",
                )
            }
        }
    }

    private fun supportsScribbleImport(profile: MixerProfile): Boolean = supportsOscScribble(profile)

    private fun supportsOscScribble(profile: MixerProfile): Boolean {
        if (profile.productId == XR18_PRODUCT_ID) return true
        val name = "${profile.productName} ${profile.displayName}"
        return name.contains("XR18", ignoreCase = true) ||
            name.contains("X18", ignoreCase = true) ||
            name.contains("X32", ignoreCase = true)
    }

    private fun supportsFlow8Scribble(profile: MixerProfile): Boolean {
        if (profile.productId == FLOW8_PRODUCT_ID) return true
        val name = "${profile.productName} ${profile.displayName}"
        return name.contains("FLOW 8", ignoreCase = true) || name.contains("FLOW8", ignoreCase = true)
    }

    companion object {
        private const val XR18_PRODUCT_ID = 0x00d4
        private const val FLOW8_PRODUCT_ID = 0x050c
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
