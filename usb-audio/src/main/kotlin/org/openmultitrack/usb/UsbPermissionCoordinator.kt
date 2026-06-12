package org.openmultitrack.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.SystemClock
import org.openmultitrack.audio.OmtLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Prevents stacked [UsbManager.requestPermission] dialogs for the same device.
 *
 * Android shows one system dialog per concurrent request; the app previously fired
 * several at once (onCreate, onResume, attach intent, ViewModel probe, IO open).
 */
object UsbPermissionCoordinator {
    private const val GRANT_GRACE_MS = 5_000L

    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()
    private val grantedAtByKey = ConcurrentHashMap<String, Long>()

    fun stableKey(device: UsbDevice): String {
        val serial = runCatching { device.serialNumber }.getOrNull()?.takeIf { it.isNotBlank() }
        return if (serial != null) {
            "${device.vendorId}:${device.productId}:$serial"
        } else {
            "${device.vendorId}:${device.productId}:${device.deviceName}"
        }
    }

    fun hasPermission(usbManager: UsbManager, device: UsbDevice): Boolean =
        usbManager.hasPermission(device)

    fun shouldRequest(usbManager: UsbManager, device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) {
            clearInFlight(device)
            return false
        }
        val key = stableKey(device)
        if (key in inFlightKeys) {
            OmtLog.d("UsbPerm", "skip request — already in flight for $key")
            return false
        }
        val grantedAt = grantedAtByKey[key]
        if (grantedAt != null && SystemClock.elapsedRealtime() - grantedAt < GRANT_GRACE_MS) {
            OmtLog.d("UsbPerm", "skip request — recently granted for $key")
            return false
        }
        return true
    }

    fun markRequestStarted(device: UsbDevice): Boolean {
        val key = stableKey(device)
        val added = inFlightKeys.add(key)
        if (added) {
            OmtLog.i("UsbPerm", "request started for ${device.productName} ($key)")
        }
        return added
    }

    fun markRequestFinished(device: UsbDevice, granted: Boolean) {
        val key = stableKey(device)
        inFlightKeys.remove(key)
        if (granted) {
            grantedAtByKey[key] = SystemClock.elapsedRealtime()
            OmtLog.i("UsbPerm", "granted for ${device.productName} ($key)")
        } else {
            OmtLog.i("UsbPerm", "denied for ${device.productName} ($key)")
        }
    }

    fun markGranted(usbManager: UsbManager, device: UsbDevice) {
        if (!usbManager.hasPermission(device)) return
        val key = stableKey(device)
        inFlightKeys.remove(key)
        grantedAtByKey[key] = SystemClock.elapsedRealtime()
    }

    fun isInFlight(device: UsbDevice): Boolean = stableKey(device) in inFlightKeys

    private fun clearInFlight(device: UsbDevice) {
        inFlightKeys.remove(stableKey(device))
    }

    /** Test-only reset. */
    internal fun resetForTest() {
        inFlightKeys.clear()
        grantedAtByKey.clear()
    }
}
