package org.openmultitrack.app.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import java.util.concurrent.ConcurrentHashMap

class MultiMixerSessionManager(
    private val appContext: Context,
    private val enumerator: UsbAudioEnumerator,
    private val settings: AppSettingsStore,
) {
    private val controllers = ConcurrentHashMap<String, MixerSessionController>()
    private val _activeMixerId = MutableStateFlow<String?>(null)
    val activeMixerId: StateFlow<String?> = _activeMixerId.asStateFlow()

    fun getOrCreate(mixerId: String): MixerSessionController =
        controllers.getOrPut(mixerId) {
            MixerSessionController(
                mixerId = mixerId,
                appContext = appContext,
                enumerator = enumerator,
                settings = settings,
                isActiveMixer = { _activeMixerId.value == mixerId },
            )
        }

    fun registerMixer(profile: MixerProfile) {
        getOrCreate(profile.id).setProfile(profile)
        if (_activeMixerId.value == null) _activeMixerId.value = profile.id
    }

    fun setActiveMixer(id: String) {
        _activeMixerId.value = id
        controllers.values.forEach { it.syncVuMeterCapture() }
    }

    fun unregisterMixer(id: String) {
        controllers.remove(id)?.shutdown()
        if (_activeMixerId.value == id) {
            _activeMixerId.value = controllers.keys.firstOrNull()
        }
    }

    fun mixerIds(): List<String> = controllers.keys.toList()

    fun onProbeComplete(
        mixerId: String,
        descriptor: UsbAudioDeviceDescriptor,
        probe: FullUsbProbeResult,
    ) {
        getOrCreate(mixerId).setProbeResult(descriptor, probe)
    }

    fun onUsbDetached(deviceName: String?) {
        if (deviceName == null) return
        controllers.values.forEach { it.onUsbDetached(deviceName) }
    }

    fun onUsbAttached(descriptor: UsbAudioDeviceDescriptor) {
        controllers.values.forEach { it.onUsbAttached(descriptor) }
    }

    fun shutdownAll() {
        controllers.values.forEach { it.shutdown() }
        controllers.clear()
        org.openmultitrack.usb.AudioEngineRouter.stopAllRecording()
    }
}
