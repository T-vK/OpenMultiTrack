package org.openmultitrack.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
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

    fun claimInterface(device: UsbDevice, interfaceNumber: Int, alternateSetting: Int = -1): Boolean {
        val t0 = SystemClock.elapsedRealtime()
        val conn = connection ?: return false
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.id != interfaceNumber) continue
            if (alternateSetting >= 0 && iface.alternateSetting != alternateSetting) continue
            val claimed = runCatching { conn.claimInterface(iface, true) }
                .onFailure { OmtLog.w("UsbStream", "claimInterface exception iface=$interfaceNumber", it) }
                .getOrDefault(false)
            if (!claimed) {
                OmtLog.i("UsbStream", "claimInterface $interfaceNumber → false")
                return false
            }
            val altOk = runCatching { conn.setInterface(iface) }
                .onFailure { OmtLog.w("UsbStream", "setInterface exception iface=$interfaceNumber", it) }
                .getOrDefault(false)
            OmtLog.i(
                "UsbStream",
                "claimInterface $interfaceNumber alt=${iface.alternateSetting} → $altOk " +
                    "+${SystemClock.elapsedRealtime() - t0}ms",
            )
            return altOk
        }
        OmtLog.w("UsbStream", "interface $interfaceNumber not found on ${device.deviceName}")
        return false
    }

    override fun close() {
        val t0 = SystemClock.elapsedRealtime()
        runCatching { connection?.close() }
        runCatching { ownedPfd?.close() }
        OmtLog.i("UsbStream", "close +${SystemClock.elapsedRealtime() - t0}ms fd=$fd")
    }

    companion object {
        fun open(context: Context, usbManager: UsbManager, device: UsbDevice): UsbAudioStreamHandle? {
            val t0 = SystemClock.elapsedRealtime()
            if (!usbManager.hasPermission(device)) {
                OmtLog.w(
                    "UsbStream",
                    "open blocked — no USB permission for ${device.deviceName}; " +
                        "request from MainActivity only",
                )
                return null
            }
            OmtLog.d(
                "UsbStream",
                "open ${device.deviceName} vid=${device.vendorId} pid=${device.productId}",
            )

            val nodePath = device.deviceName.takeIf { it.startsWith("/dev/bus/usb/") }
            if (nodePath != null) {
                openDeviceNode(nodePath)?.let { return it }
            }

            val connection = runCatching { usbManager.openDevice(device) }
                .onFailure { OmtLog.w("UsbStream", "openDevice exception for ${device.deviceName}", it) }
                .getOrNull()
            if (connection != null) {
                OmtLog.i(
                    "UsbStream",
                    "openDevice ok fd=${connection.fileDescriptor} +${SystemClock.elapsedRealtime() - t0}ms",
                )
                return UsbAudioStreamHandle(connection, null)
            }

            OmtLog.w(
                "UsbStream",
                "all open paths failed for ${device.deviceName} +${SystemClock.elapsedRealtime() - t0}ms",
            )
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
