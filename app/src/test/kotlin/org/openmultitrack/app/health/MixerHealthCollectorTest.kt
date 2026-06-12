package org.openmultitrack.app.health

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.app.service.MixerSessionUiState
import org.openmultitrack.domain.audio.UsbAudioDeviceDescriptor
import org.openmultitrack.domain.mixer.HealthLevel
import org.openmultitrack.domain.mixer.MixerProfile
import org.openmultitrack.domain.mixer.ProbeState
import org.openmultitrack.domain.session.AppMode

class MixerHealthCollectorTest {
    private val flow8Usb = UsbAudioDeviceDescriptor(
        deviceName = "/dev/bus/usb/001/002",
        vendorId = 0x1397,
        productId = 0x050c,
        manufacturerName = "Behringer",
        productName = "FLOW 8",
        serialNumber = "abc",
        isLikelyBehringerMixer = true,
        guessedModel = "FLOW8",
        androidAudioDeviceId = null,
    )
    private val flow8Profile = MixerProfile(
        id = "flow8",
        usbDeviceName = flow8Usb.deviceName,
        vendorId = flow8Usb.vendorId,
        productId = flow8Usb.productId,
        serialNumber = "abc",
        productName = "FLOW 8",
        displayName = "FLOW 8",
    )

    @Test
    fun playbackBlockedWhenSessionLoadedButProbeMissing() {
        val session = MixerSessionUiState(
            mixerId = flow8Profile.id,
            appMode = AppMode.VIRTUAL_SOUNDCHECK,
            selectedSoundcheckDir = "/data/session",
        )
        val snapshot = MixerHealthCollector.collect(
            profile = flow8Profile,
            session = session,
            availableUsb = listOf(flow8Usb),
            usbPermissionGranted = true,
        )
        assertThat(snapshot.usb.probeState).isEqualTo(ProbeState.NONE)
        assertThat(snapshot.primaryIssue?.code).isEqualTo("probe_missing")
        assertThat(snapshot.overall).isEqualTo(HealthLevel.BLOCKED)
    }

    @Test
    fun playbackReadyWhenProbeComplete() {
        val session = MixerSessionUiState(
            mixerId = flow8Profile.id,
            appMode = AppMode.VIRTUAL_SOUNDCHECK,
            selectedSoundcheckDir = "/data/session",
            probe = org.openmultitrack.usb.FullUsbProbeResult(
                usb = flow8Usb,
                input = null,
                output = null,
                uac2Caps = null,
            ),
        )
        val snapshot = MixerHealthCollector.collect(
            profile = flow8Profile,
            session = session,
            availableUsb = listOf(flow8Usb),
            usbPermissionGranted = true,
        )
        assertThat(snapshot.usb.probeState).isEqualTo(ProbeState.OK)
        assertThat(snapshot.primaryIssue).isNull()
        assertThat(snapshot.overall).isEqualTo(HealthLevel.OK)
    }
}
