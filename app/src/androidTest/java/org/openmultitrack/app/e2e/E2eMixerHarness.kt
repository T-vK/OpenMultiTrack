package org.openmultitrack.app.e2e

import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModelProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.openmultitrack.app.MainViewModel
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.service.AudioSessionClient
import org.openmultitrack.app.service.MixerSessionController
import org.openmultitrack.app.service.MultiMixerSessionManager
import org.openmultitrack.app.test.UsbInstrumentedPermission
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService
import java.io.File
import kotlin.math.abs

class E2eMixerHarness(
    private val appHost: E2eActivityHost,
) {
    val context get() = appHost.appContext
    private val client = AudioSessionClient(context)
    private lateinit var manager: MultiMixerSessionManager
    lateinit var mixerId: String
        private set

    suspend fun bindAndRegisterXr18(preserveActiveRecording: Boolean = false): MixerSessionController {
        val ready = CompletableDeferred<MultiMixerSessionManager>()
        client.whenReady { ready.complete(it) }
        client.bind()
        manager = withTimeout(20_000) { ready.await() }
        client.promoteForeground("E2E test")

        releaseGlobalUsbCapture(manager, preserveActiveRecording)

        val (id, descriptor, probe) = appHost.runOnActivity { activity ->
            val enumerator = UsbAudioEnumerator(activity)
            val xr18 = enumerator.listUsbDevices().first {
                it.vendorId == E2eConfig.XR18_VENDOR_ID && it.productId == E2eConfig.XR18_PRODUCT_ID
            }
            val usbManager = activity.getSystemService(UsbManager::class.java)
            val device = usbManager.deviceList[xr18.deviceName]
                ?: error("XR18 not in UsbManager device list")
            val granted = UsbInstrumentedPermission.ensure(activity, usbManager, device)
            assertThat(granted).isTrue()
            val profile = MixerDeviceStore(activity).addMixer(xr18)
            val probeResult = UsbAudioProbeService(enumerator).probe(xr18)
            Triple(profile.id, xr18, probeResult)
        }

        mixerId = id
        val profile = MixerDeviceStore(context).listMixers().first { it.id == mixerId }
        manager.registerMixer(profile)
        manager.onProbeComplete(mixerId, descriptor, probe)
        manager.setActiveMixer(mixerId)
        releaseGlobalUsbCapture(manager, preserveActiveRecording)

        val ctrl = manager.getOrCreate(mixerId)
        E2eWait.untilMixerState(ctrl, 60_000) { it.probe != null && !it.probing }
        syncUiWithHarnessMixer(mixerId)
        return ctrl
    }

    fun applyOscHostOnActiveMixer(oscHost: String) {
        val store = MixerDeviceStore(context)
        val profile = store.listMixers().first { it.id == mixerId }
        val updated = profile.copy(oscHost = oscHost)
        store.saveMixer(updated)
        manager.getOrCreate(mixerId).setProfile(updated)
        syncUiWithHarnessMixer(mixerId)
    }

    /** Ensures MainActivity observes the harness-registered mixer (channels + waveforms on screen). */
    private fun syncUiWithHarnessMixer(mixerId: String) {
        appHost.runOnActivity { activity ->
            val vm = ViewModelProvider(
                activity,
                MainViewModel.factory(activity.applicationContext),
            )[MainViewModel::class.java]
            vm.reloadMixersFromStore()
            vm.setActiveMixer(mixerId)
        }
        Thread.sleep(500)
    }

    private suspend fun releaseGlobalUsbCapture(
        manager: MultiMixerSessionManager,
        preserveActiveRecording: Boolean = false,
    ) {
        val settings = AppSettingsStore(context)
        settings.showVuMeters = false
        manager.mixerIds().forEach { id ->
            val ctrl = manager.getOrCreate(id)
            runCatching { ctrl.stopMonitoring() }
            runCatching { ctrl.syncVuMeterCapture() }
            runCatching {
                if (!preserveActiveRecording && ctrl.state.value.isRecording) {
                    ctrl.stopRecording()
                    E2eWait.untilNotRecording(ctrl, timeoutMs = 30_000)
                }
            }
        }
        AudioEngineRouter.stopAllRecording()
        delay(1_000)
    }

    suspend fun recordShortSession(
        ctrl: MixerSessionController,
        seconds: Int = E2eConfig.RECORD_SECONDS,
    ): File {
        releaseGlobalUsbCapture(manager)
        ctrl.setAppMode(AppMode.MULTITRACK_RECORD)
        ctrl.startRecording()
        E2eWait.untilRecording(ctrl, timeoutMs = 60_000)
        if (seconds >= 20) {
            val minElapsed = (seconds - 8).toFloat()
            E2eWait.untilMixerState(ctrl, (seconds + 30) * 1_000L) {
                it.isRecording && it.recordElapsedSec >= minElapsed
            }
        } else {
            delay(seconds * 1_000L)
        }
        ctrl.stopRecording()
        E2eWait.untilNotRecording(ctrl, timeoutMs = 60_000)
        val path = ctrl.state.value.lastRecordingPath
        assertThat(path).isNotNull()
        val sessionDir = File(path!!)
        assertThat(sessionDir.isDirectory).isTrue()
        val meta = SessionMetadata.read(sessionDir)
        assertThat(meta).isNotNull()
        assertThat(meta!!.incomplete).isFalse()
        return sessionDir
    }

    suspend fun prepareSoundcheck(ctrl: MixerSessionController, sessionDir: File) {
        val settings = AppSettingsStore(context)
        settings.setAppModeForMixer(mixerId, AppMode.VIRTUAL_SOUNDCHECK)
        ctrl.setAppMode(AppMode.VIRTUAL_SOUNDCHECK)
        ctrl.selectSoundcheckSession(sessionDir.absolutePath)
        E2eWait.untilMixerState(ctrl, 60_000) {
            it.selectedSoundcheckDir == sessionDir.absolutePath
        }
        E2eWait.untilSoundcheckReady(ctrl, timeoutMs = 180_000)
        assertThat(ctrl.state.value.playbackDurationSec).isGreaterThan(1f)
    }

    suspend fun assertPlaybackAtPosition(
        ctrl: MixerSessionController,
        targetSec: Float,
        toleranceSec: Float = 1.5f,
        timeoutMs: Long = 15_000,
    ) {
        E2eWait.untilPlaybackNear(ctrl, targetSec, toleranceSec, timeoutMs = timeoutMs)
        val pos = ctrl.state.value.playbackPositionSec
        assertThat(abs(pos - targetSec)).isAtMost(toleranceSec)
    }

    fun clientUnbindOnly() {
        client.unbind()
    }

    fun shutdown() {
        manager.shutdownAll()
        AudioEngineRouter.stopAllRecording()
        client.unbind()
    }
}
