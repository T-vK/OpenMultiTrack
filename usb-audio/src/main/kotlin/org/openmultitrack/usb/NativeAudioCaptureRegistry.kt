package org.openmultitrack.usb

import android.hardware.usb.UsbDevice
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.audio.NativeEngineStatus
import org.openmultitrack.audio.NativeUac2Engine
import org.openmultitrack.audio.OmtLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which mixer session owns each native capture backend so unplugging or
 * stopping one mixer does not tear down another mixer's Oboe/UAC2 stream.
 */
object NativeAudioCaptureRegistry {
    private val owners = ConcurrentHashMap<AudioBackend, String>()

    fun start(ownerId: String, route: CaptureRoute, usbDevice: UsbDevice? = null): NativeEngineStatus {
        val holder = owners[route.backend]
        if (holder != null && holder != ownerId) {
            return failed("${route.backend} capture in use by $holder")
        }
        stopBackend(route.backend)
        val status = startBackend(route, usbDevice)
        if (status.active) {
            owners[route.backend] = ownerId
            OmtLog.i("CaptureRegistry", "start $ownerId on ${route.backend}")
        }
        return status
    }

    fun release(backend: AudioBackend, ownerId: String) {
        if (owners[backend] != ownerId) {
            OmtLog.d("CaptureRegistry", "skip release $ownerId on $backend — holder=${owners[backend]}")
            return
        }
        stopBackend(backend)
        owners.remove(backend)
        OmtLog.i("CaptureRegistry", "released $ownerId on $backend")
    }

    fun releaseAll() {
        AudioBackend.entries.forEach { stopBackend(it) }
        owners.clear()
        OmtLog.i("CaptureRegistry", "released all backends")
    }

    fun holder(backend: AudioBackend): String? = owners[backend]

    private fun startBackend(route: CaptureRoute, usbDevice: UsbDevice?): NativeEngineStatus =
        when (route.backend) {
            AudioBackend.OBOE ->
                NativeAudioEngine.startRecording(route.oboeDeviceId, route.channelCount, route.sampleRate)
            AudioBackend.UAC2 -> {
                val stream = route.usbStream ?: return failed("missing usb stream")
                val alt = route.uac2Alt ?: return failed("missing uac2 alt")
                val javaClaimed = claimUac2Interface(stream, usbDevice, alt)
                NativeUac2Engine.startCapture(stream.fd, alt, javaClaimed)
            }
        }

    private fun stopBackend(backend: AudioBackend) {
        when (backend) {
            AudioBackend.OBOE -> NativeAudioEngine.stopRecording()
            AudioBackend.UAC2 -> NativeUac2Engine.stopCapture()
        }
    }

    private fun claimUac2Interface(
        stream: UsbAudioStreamHandle,
        usbDevice: UsbDevice?,
        alt: org.openmultitrack.audio.NativeUac2AltSetting,
    ): Boolean {
        if (usbDevice == null) return false
        val ok = stream.claimInterface(
            usbDevice,
            alt.interfaceNumber,
            alternateSetting = alt.alternateSetting,
        )
        OmtLog.i("CaptureRegistry", "Java claimInterface ${alt.interfaceNumber} alt=${alt.alternateSetting} → $ok")
        return ok
    }

    private fun failed(message: String): NativeEngineStatus =
        NativeEngineStatus(active = false, channelCount = 0, sampleRate = 0, errorMessage = message)
}
