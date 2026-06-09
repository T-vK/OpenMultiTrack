package org.openmultitrack.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import org.openmultitrack.audio.OmtLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object UsbPermissionHelper {
    fun permissionAction(packageName: String): String = "$packageName.USB_PERMISSION"

    /**
     * Ensures [UsbManager.hasPermission] is true, using [UsbManager.requestPermission] when needed.
     * On userdebug emulator builds, also tries the hidden grantDevicePermission service call.
     */
    fun ensurePermission(
        context: Context,
        usbManager: UsbManager,
        device: UsbDevice,
        permissionAction: String = permissionAction(context.packageName),
    ): Boolean {
        if (usbManager.hasPermission(device)) return true

        if (tryHiddenGrant(context, usbManager, device)) {
            OmtLog.i("UsbPerm", "hidden grant succeeded for ${device.deviceName}")
            return true
        }

        val granted = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != permissionAction) return
                if (intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)?.deviceName
                    != device.deviceName
                ) {
                    return
                }
                granted.set(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                latch.countDown()
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(permissionAction).setPackage(context.packageName),
            flags,
        )

        val filter = IntentFilter(permissionAction)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        return try {
            OmtLog.i("UsbPerm", "requestPermission for ${device.deviceName}")
            usbManager.requestPermission(device, pending)
            latch.await(8, TimeUnit.SECONDS)
            val ok = granted.get() || usbManager.hasPermission(device)
            if (!ok) {
                tryHiddenGrant(context, usbManager, device)
            }
            val finalOk = usbManager.hasPermission(device)
            OmtLog.i("UsbPerm", "permission result for ${device.deviceName}: $finalOk")
            finalOk
        } catch (e: Exception) {
            OmtLog.w("UsbPerm", "requestPermission failed for ${device.deviceName}", e)
            usbManager.hasPermission(device) || tryHiddenGrant(context, usbManager, device)
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    /** Hidden API: IUsbManager.grantDevicePermission — works on userdebug emulator when xml grant fails. */
    private fun tryHiddenGrant(context: Context, usbManager: UsbManager, device: UsbDevice): Boolean {
        return runCatching {
            val serviceField = UsbManager::class.java.getDeclaredField("mService")
            serviceField.isAccessible = true
            val service = serviceField.get(usbManager)
            val method = service.javaClass.getMethod(
                "grantDevicePermission",
                UsbDevice::class.java,
                String::class.java,
            )
            method.invoke(service, device, context.packageName)
            usbManager.hasPermission(device)
        }.onFailure {
            OmtLog.d("UsbPerm", "hidden grant unavailable: ${it.message}")
        }.getOrDefault(false)
    }
}
