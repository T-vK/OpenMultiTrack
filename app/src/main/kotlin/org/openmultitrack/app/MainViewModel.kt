package org.openmultitrack.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.service.AudioSessionClient
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.usb.AudioOutputDeviceLabel
import org.openmultitrack.usb.LabeledAudioDevice
import org.openmultitrack.app.data.StripIconMode
import org.openmultitrack.app.data.StripNumberMode
import org.openmultitrack.app.scribble.Flow8BleScribbleImporter
import org.openmultitrack.app.scribble.IncompleteRecordingStore
import org.openmultitrack.app.scribble.OscLanDiscovery
import org.openmultitrack.mixer.behringer.Xr18ScribbleImporter
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService

data class Flow8PairingDialogState(
    val mixerId: String,
)

data class DawUiState(
    val mixers: List<MixerProfile> = emptyList(),
    val activeMixerId: String? = null,
    val sessionByMixer: Map<String, MixerSessionUiState> = emptyMap(),
    val outputDevices: List<LabeledAudioDevice> = emptyList(),
    val availableUsbDevices: List<UsbAudioDeviceDescriptor> = emptyList(),
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

    init {
        sessionClient.onManagerLost {
            observedMixerIds.clear()
            OmtLog.w("ViewModel", "session service lost — will re-attach on reconnect")
        }
        sessionClient.bind()
        loadMixers()
        refreshUsbAndOutputs()
        sessionClient.whenReady { manager ->
            attachToSessionManager(manager)
            viewModelScope.launch {
                manager.activeMixerId.collect { id ->
                    _uiState.update { it.copy(activeMixerId = id) }
                }
            }
        }
    }

    fun refreshUsbAndOutputs() {
        viewModelScope.launch {
            val usb = withContext(Dispatchers.IO) { enumerator.listUsbDevices() }
            val outputs = withContext(Dispatchers.IO) {
                AudioOutputDeviceLabel.labelAll(enumerator.listAudioOutputDevices())
            }
            _uiState.update { it.copy(availableUsbDevices = usb, outputDevices = outputs) }
            syncMixerUsbNames(usb)
            sessionClient.withManager { mgr ->
                _uiState.value.mixers.forEach { autoProbeMixer(it, mgr) }
            }
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
        val profile = mixerStore.addMixer(descriptor)
        loadMixers()
        sessionClient.withManager { mgr ->
            mgr.registerMixer(profile)
            mgr.setActiveMixer(profile.id)
            autoProbeMixer(profile, mgr)
        }
        _uiState.update { it.copy(showAddMixerDialog = false, activeMixerId = profile.id) }
    }

    fun removeMixer(id: String) {
        mixerStore.removeMixer(id)
        loadMixers()
    }

    fun setActiveMixer(id: String) {
        sessionClient.withManager { it.setActiveMixer(id) }
        _uiState.update { it.copy(activeMixerId = id) }
    }

    fun setAppMode(mixerId: String, mode: AppMode) {
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
        sessionClient.withManager { mgr ->
            _uiState.value.mixers
                .filter { it.usbDeviceName == deviceName }
                .forEach { autoProbeMixer(it, mgr) }
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
            _uiState.update { it.copy(flow8PairingDialog = Flow8PairingDialogState(mixerId)) }
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
        viewModelScope.launch {
            importScribbleForMixer(profile)
        }
    }

    fun dismissFlow8PairingDialog() {
        _uiState.update { it.copy(flow8PairingDialog = null) }
    }

    private suspend fun importScribbleForMixer(profile: MixerProfile) {
        val flow8 = supportsFlow8Scribble(profile)
        _uiState.update {
            it.copy(
                globalStatus = if (flow8) {
                    Flow8BleScribbleImporter.PAIRING_SCANNING_MESSAGE
                } else {
                    "Importing scribble strip…"
                },
            )
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
                    val host = profile.oscHost ?: OscLanDiscovery.discoverMixerIp(appContext)
                    if (host == null) {
                        _uiState.update {
                            it.copy(globalStatus = "Mixer not found on network. Connect device and mixer to the same LAN.")
                        }
                        return
                    }
                    withContext(Dispatchers.IO) {
                        Xr18ScribbleImporter().fetchUsbLabels(host).map { labels ->
                            profile.copy(oscHost = host) to labels
                        }
                    }
                }
                else -> Result.failure(IllegalStateException("Unsupported mixer"))
            }
            result.onSuccess { (updatedProfile, labels) ->
                sessionClient.withManager { mgr ->
                    mgr.getOrCreate(profile.id).applyScribbleLabels(labels)
                }
                val saved = updatedProfile.copy(scribbleImported = true)
                mixerStore.saveMixer(saved)
                loadMixers()
                val named = labels.count { !it.name.isNullOrBlank() }
                AppLogBuffer.append("I", "Scribble", "Imported $named channel labels for ${profile.displayName}")
                _uiState.update { it.copy(globalStatus = "Scribble: $named channel labels") }
                OmtLog.i("ViewModel", "scribble imported $named labels for ${profile.id}")
            }.onFailure { e ->
                OmtLog.e("ViewModel", "scribble import failed", e)
                _uiState.update {
                    it.copy(globalStatus = flow8ScribbleFailureMessage(e))
                }
            }
        } catch (e: Exception) {
            OmtLog.e("ViewModel", "scribble import failed", e)
            _uiState.update {
                it.copy(globalStatus = if (flow8) flow8ScribbleFailureMessage(e) else "Scribble import failed: ${e.message}")
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
        _uiState.update { it.copy(mixers = mixers, activeMixerId = it.activeMixerId ?: mixers.firstOrNull()?.id) }
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
            observeMixerSession(profile.id, manager)
            autoProbeMixer(profile, manager)
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

    private fun autoProbeMixer(profile: MixerProfile, manager: org.openmultitrack.app.service.MultiMixerSessionManager) {
        val deviceName = profile.usbDeviceName
        if (deviceName == null) {
            OmtLog.w("ViewModel", "skip probe for ${profile.displayName}: no USB device name")
            return
        }
        if (!enumerator.hasUsbPermission(deviceName)) {
            OmtLog.w("ViewModel", "skip probe for ${profile.displayName}: USB permission not granted for $deviceName")
            _uiState.update {
                it.copy(globalStatus = "USB permission required for ${profile.displayName} — reconnect the device.")
            }
            return
        }
        val usb = _uiState.value.availableUsbDevices.firstOrNull { it.deviceName == deviceName }
            ?: enumerator.listUsbDevices().firstOrNull { it.deviceName == deviceName }
            ?: return
        viewModelScope.launch {
            manager.getOrCreate(profile.id).setProbing(true)
            AppLogBuffer.append("I", "Probe", "Auto-probing ${profile.displayName}")
            val result = withContext(Dispatchers.IO) { probeService.probe(usb) }
            manager.onProbeComplete(profile.id, usb, result)
            OmtLog.i("ViewModel", "auto-probed ${profile.displayName}")
            val resumePending = IncompleteRecordingStore.hasIncompleteRecording(appContext, settings, profile.id)
            _uiState.update { it.copy(pendingRecordingResume = resumePending) }
            if (resumePending) {
                AppLogBuffer.append("I", "Scribble", "Skipped auto-import — incomplete recording to resume")
            } else if (supportsFlow8Scribble(profile)) {
                _uiState.update { it.copy(flow8PairingDialog = Flow8PairingDialogState(profile.id)) }
            } else if (supportsScribbleImport(profile)) {
                importScribbleForMixer(profile)
            }
        }
    }

    private fun supportsScribbleImport(profile: MixerProfile): Boolean =
        supportsOscScribble(profile) || supportsFlow8Scribble(profile)

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
                        AppSettingsStore(appContext),
                        AudioSessionClient(appContext),
                    ) as T
                }
            }
    }
}
