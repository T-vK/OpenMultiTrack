package org.openmultitrack.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import org.openmultitrack.audio.OmtLog
import java.util.ArrayDeque

/**
 * Serializes [UsbManager.requestPermission] across multiple saved mixers.
 *
 * Android shows one dialog at a time; concurrent requests caused duplicate prompts
 * when XR18 and Flow 8 both reconnected after clearing default-handler prefs.
 */
class UsbPermissionQueue {
    private val queue = ArrayDeque<String>()
    private val queuedKeys = mutableSetOf<String>()
    private var inFlightKey: String? = null

    val hasPending: Boolean
        get() = inFlightKey != null || queue.isNotEmpty()

    fun enqueueMissing(devices: Collection<UsbDevice>, usbManager: UsbManager) {
        devices
            .distinctBy { UsbPermissionCoordinator.stableKey(it) }
            .forEach { device ->
                UsbPermissionCoordinator.registerDevice(usbManager, device)
                if (usbManager.hasPermission(device)) {
                    UsbPermissionCoordinator.markGranted(usbManager, device)
                    return@forEach
                }
                val key = UsbPermissionCoordinator.stableKey(device)
                if (key == inFlightKey || key in queuedKeys) {
                    return@forEach
                }
                if (!UsbPermissionCoordinator.shouldRequest(usbManager, device)) {
                    return@forEach
                }
                queuedKeys.add(key)
                queue.addLast(device.deviceName)
                OmtLog.d("UsbPerm", "queued permission for ${device.productName} ($key)")
            }
    }

    /**
     * Returns the next device that needs a permission dialog, or null when idle/done.
     * Only one in-flight request is allowed at a time.
     */
    fun pollNext(usbManager: UsbManager, connected: Collection<UsbDevice>): UsbDevice? {
        if (inFlightKey != null) {
            return null
        }
        while (queue.isNotEmpty()) {
            val requestedName = queue.removeFirst()
            val device = connected.firstOrNull { it.deviceName == requestedName }
                ?: UsbPermissionCoordinator.findDeviceByNameOrAlias(requestedName, connected)
            if (device == null) {
                OmtLog.d("UsbPerm", "skip queued $requestedName — device no longer connected")
                continue
            }
            val key = UsbPermissionCoordinator.stableKey(device)
            queuedKeys.remove(key)
            UsbPermissionCoordinator.registerDevice(usbManager, device)
            if (usbManager.hasPermission(device)) {
                UsbPermissionCoordinator.markGranted(usbManager, device)
                continue
            }
            if (!UsbPermissionCoordinator.shouldRequest(usbManager, device)) {
                continue
            }
            inFlightKey = key
            OmtLog.i("UsbPerm", "polling ${device.productName} ($key)")
            return device
        }
        return null
    }

    fun onRequestStarted(device: UsbDevice) {
        inFlightKey = UsbPermissionCoordinator.stableKey(device)
    }

    fun onRequestFinished(device: UsbDevice) {
        val key = UsbPermissionCoordinator.stableKey(device)
        if (inFlightKey == key) {
            inFlightKey = null
        }
        queuedKeys.remove(key)
        queue.remove(device.deviceName)
    }

    internal fun resetForTest() {
        queue.clear()
        queuedKeys.clear()
        inFlightKey = null
    }
}
