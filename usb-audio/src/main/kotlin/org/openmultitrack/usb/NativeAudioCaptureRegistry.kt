package org.openmultitrack.usb

import android.hardware.usb.UsbDevice
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.audio.NativeEngineStatus
import org.openmultitrack.audio.NativeUac2Engine
import org.openmultitrack.audio.OmtLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which mixer session owns each native capture stream.
 * Different USB devices may share a backend type; only one UAC2 stream can run at a time
 * in native code, but we avoid tearing down an active owner when another mixer probes VU meters.
 */
object NativeAudioCaptureRegistry {
    private data class CaptureSlot(val backend: AudioBackend, val deviceKey: String)

    private val owners = ConcurrentHashMap<CaptureSlot, String>()
    private val activeRoutes = ConcurrentHashMap<CaptureSlot, CaptureRoute>()

    fun start(ownerId: String, route: CaptureRoute, usbDevice: UsbDevice? = null): NativeEngineStatus {
        val slot = slotFor(route)
        val holder = owners[slot]
        if (holder != null && holder != ownerId) {
            return failed("${route.backend} capture in use by $holder")
        }
        if (holder == ownerId && activeRoutes[slot] == route) {
            return NativeEngineStatus(
                active = true,
                channelCount = route.channelCount,
                sampleRate = route.sampleRate,
                errorMessage = null,
            )
        }
        if (route.backend == AudioBackend.UAC2) {
            val other = owners.entries.firstOrNull { (key, id) ->
                key.backend == AudioBackend.UAC2 && key != slot && id != ownerId
            }
            if (other != null) {
                return failed("UAC2 capture in use by ${other.value}")
            }
        }
        stopSlot(slot)
        val status = startBackend(route, usbDevice)
        if (status.active) {
            owners[slot] = ownerId
            activeRoutes[slot] = route
            OmtLog.i("CaptureRegistry", "start $ownerId on ${route.backend} key=${slot.deviceKey}")
        }
        return status
    }

    fun release(backend: AudioBackend, ownerId: String) {
        val slots = owners.entries.filter { it.key.backend == backend && it.value == ownerId }
        if (slots.isEmpty()) {
            OmtLog.d("CaptureRegistry", "skip release $ownerId on $backend — not holder")
            return
        }
        slots.forEach { (slot, _) ->
            stopSlot(slot)
            owners.remove(slot)
            activeRoutes.remove(slot)
        }
        OmtLog.i("CaptureRegistry", "released $ownerId on $backend")
    }

    fun releaseAll() {
        activeRoutes.keys.toList().forEach(::stopSlot)
        owners.clear()
        activeRoutes.clear()
        OmtLog.i("CaptureRegistry", "released all backends")
    }

    fun holder(backend: AudioBackend): String? =
        owners.entries.firstOrNull { it.key.backend == backend }?.value

    fun isOwner(ownerId: String, backend: AudioBackend): Boolean =
        owners.entries.any { it.key.backend == backend && it.value == ownerId }

    private fun slotFor(route: CaptureRoute): CaptureSlot {
        val deviceKey = when (route.backend) {
            AudioBackend.OBOE -> "oboe:${route.oboeDeviceId}"
            AudioBackend.UAC2 -> "uac2:${route.usbStream?.fd ?: route.uac2Alt?.interfaceNumber ?: -1}"
        }
        return CaptureSlot(route.backend, deviceKey)
    }

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

    private fun stopSlot(slot: CaptureSlot) {
        when (slot.backend) {
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
