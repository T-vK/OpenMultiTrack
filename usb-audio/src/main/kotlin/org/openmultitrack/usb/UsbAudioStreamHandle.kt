package org.openmultitrack.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import org.openmultitrack.audio.OmtLog
import java.io.File

/**
 * Holds an open usbfs fd for UAC2 isoch streaming.
 *
 * Tries [UsbManager.openDevice] first. On emulator passthrough, falls back to opening
 * [UsbDevice.getDeviceName] directly when SELinux is permissive (see grant script).
 */
class UsbAudioStreamHandle private constructor(
    private val connection: UsbDeviceConnection?,
    private val ownedPfd: ParcelFileDescriptor?,
) : AutoCloseable {
    val fd: Int
        get() = connection?.fileDescriptor
            ?: ownedPfd?.fd
            ?: -1

    fun claimInterface(device: UsbDevice, interfaceNumber: Int): Boolean {
        val conn = connection ?: return false
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.id == interfaceNumber) {
                val ok = conn.claimInterface(iface, true)
                OmtLog.i("UsbStream", "claimInterface $interfaceNumber → $ok")
                return ok
            }
        }
        OmtLog.w("UsbStream", "interface $interfaceNumber not found on ${device.deviceName}")
        return false
    }

    override fun close() {
        runCatching { connection?.close() }
        runCatching { ownedPfd?.close() }
    }

    companion object {
        fun open(context: Context, usbManager: UsbManager, device: UsbDevice): UsbAudioStreamHandle? {
            UsbPermissionHelper.ensurePermission(context, usbManager, device)
            val hasPermission = usbManager.hasPermission(device)
            OmtLog.d(
                "UsbStream",
                "open ${device.deviceName} vid=${device.vendorId} pid=${device.productId} " +
                    "hasPermission=$hasPermission",
            )

            val connection = runCatching { usbManager.openDevice(device) }
                .onFailure { OmtLog.w("UsbStream", "openDevice exception for ${device.deviceName}", it) }
                .getOrNull()
            if (connection != null) {
                OmtLog.i("UsbStream", "openDevice ok fd=${connection.fileDescriptor}")
                return UsbAudioStreamHandle(connection, null)
            }

            val nodePath = device.deviceName.takeIf { it.startsWith("/dev/bus/usb/") }
            if (nodePath != null) {
                openDeviceNode(nodePath)?.let { return it }
            }

            OmtLog.w("UsbStream", "all open paths failed for ${device.deviceName}")
            return null
        }

        /** Direct usbfs open (emulator passthrough with permissive SELinux). */
        fun openDeviceNode(path: String): UsbAudioStreamHandle? {
            return runCatching {
                val pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_WRITE)
                OmtLog.i("UsbStream", "opened device node $path fd=${pfd.fd}")
                UsbAudioStreamHandle(null, pfd)
            }.onFailure {
                OmtLog.w("UsbStream", "openDeviceNode failed for $path", it)
            }.getOrNull()
        }
    }
}
