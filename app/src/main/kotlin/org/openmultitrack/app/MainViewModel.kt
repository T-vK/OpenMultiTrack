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
import org.openmultitrack.app.scribble.OscLanDiscovery
import org.openmultitrack.mixer.behringer.Xr18ScribbleImporter
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService

data class DawUiState(
    val mixers: List<MixerProfile> = emptyList(),
    val activeMixerId: String? = null,
    val sessionByMixer: Map<String, MixerSessionUiState> = emptyMap(),
    val outputDevices: List<LabeledAudioDevice> = emptyList(),
    val availableUsbDevices: List<UsbAudioDeviceDescriptor> = emptyList(),
    val showAddMixerDialog: Boolean = false,
    val showSettings: Boolean = false,
    val showLogViewer: Boolean = false,
    val globalStatus: String? = null,
    val hideArmButton: Boolean = false,
    val hideMonitorButton: Boolean = false,
    val hideSoloButton: Boolean = false,
    val showWaveforms: Boolean = true,
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
        ),
    )
    val uiState: StateFlow<DawUiState> = _uiState.asStateFlow()

    private val observedMixerIds = mutableSetOf<String>()

    init {
        sessionClient.bind()
        loadMixers()
        refreshUsbAndOutputs()
        sessionClient.whenReady { manager ->
            viewModelScope.launch {
                manager.activeMixerId.collect { id ->
                    _uiState.update { it.copy(activeMixerId = id) }
                }
            }
            _uiState.value.mixers.forEach { observeMixerSession(it.id, manager) }
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

    fun refreshScribble(mixerId: String) {
        val profile = _uiState.value.mixers.firstOrNull { it.id == mixerId } ?: return
        if (!supportsOscScribble(profile)) {
            _uiState.update { it.copy(globalStatus = "Scribble import is not available for this mixer yet.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(globalStatus = "Importing scribble strip…") }
            try {
                val importer = Xr18ScribbleImporter()
                val host = profile.oscHost
                    ?: OscLanDiscovery.discoverMixerIp(appContext)
                if (host == null) {
                    _uiState.update {
                        it.copy(globalStatus = "Mixer not found on network. Connect tablet and XR18 to the same LAN.")
                    }
                    return@launch
                }
                val result = withContext(Dispatchers.IO) { importer.fetchUsbLabels(host) }
                result.onSuccess { labels ->
                    sessionClient.withManager { mgr ->
                        mgr.getOrCreate(mixerId).applyScribbleLabels(labels)
                    }
                    val updated = profile.copy(oscHost = host, scribbleImported = true)
                    mixerStore.saveMixer(updated)
                    loadMixers()
                    val named = labels.count { !it.name.isNullOrBlank() }
                    AppLogBuffer.append("I", "Scribble", "Imported $named channel labels from $host")
                    _uiState.update { it.copy(globalStatus = "Scribble: $named channel labels from $host") }
                    OmtLog.i("ViewModel", "scribble imported $named labels from $host")
                }.onFailure { e ->
                    OmtLog.e("ViewModel", "scribble import failed", e)
                    _uiState.update { it.copy(globalStatus = "Scribble import failed: ${e.message}") }
                }
            } catch (e: Exception) {
                OmtLog.e("ViewModel", "scribble import failed", e)
                _uiState.update { it.copy(globalStatus = "Scribble import failed: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        sessionClient.unbind()
        super.onCleared()
    }

    private fun loadMixers() {
        val mixers = mixerStore.listMixers()
        _uiState.update { it.copy(mixers = mixers, activeMixerId = it.activeMixerId ?: mixers.firstOrNull()?.id) }
        sessionClient.withManager { mgr ->
            mixers.forEach {
                mgr.registerMixer(it)
                observeMixerSession(it.id, mgr)
            }
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
        val deviceName = profile.usbDeviceName ?: return
        if (!enumerator.hasUsbPermission(deviceName)) return
        val usb = _uiState.value.availableUsbDevices.firstOrNull { it.deviceName == deviceName }
            ?: enumerator.listUsbDevices().firstOrNull { it.deviceName == deviceName }
            ?: return
        viewModelScope.launch {
            manager.getOrCreate(profile.id).setProbing(true)
            AppLogBuffer.append("I", "Probe", "Auto-probing ${profile.displayName}")
            val result = withContext(Dispatchers.IO) { probeService.probe(usb) }
            manager.onProbeComplete(profile.id, usb, result)
            OmtLog.i("ViewModel", "auto-probed ${profile.displayName}")
            if (supportsOscScribble(profile) && !profile.scribbleImported) {
                refreshScribble(profile.id)
            }
        }
    }

    private fun supportsOscScribble(profile: MixerProfile): Boolean {
        if (profile.productId == XR18_PRODUCT_ID) return true
        val name = "${profile.productName} ${profile.displayName}"
        return name.contains("XR18", ignoreCase = true) ||
            name.contains("X18", ignoreCase = true) ||
            name.contains("X32", ignoreCase = true)
    }

    companion object {
        private const val XR18_PRODUCT_ID = 0x00d4
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
