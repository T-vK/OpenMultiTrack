package org.openmultitrack.app.test

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.openmultitrack.audio.OmtLog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Grants USB permission during instrumented tests by auto-accepting the system dialog.
 */
object UsbInstrumentedPermission {
    private const val ACTION = "org.openmultitrack.test.USB_PERMISSION"

    fun ensure(activity: Activity, usbManager: UsbManager, device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true

        val granted = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION) return
                granted.set(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                latch.countDown()
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(
            activity,
            device.deviceId,
            Intent(ACTION).setPackage(activity.packageName),
            flags,
        )

        val filter = IntentFilter(ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            activity.registerReceiver(receiver, filter)
        }

        val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val clickDone = AtomicBoolean(false)
        val clickThread = Thread {
            val deadline = System.currentTimeMillis() + 10_000
            while (!clickDone.get() && System.currentTimeMillis() < deadline) {
                clickAllowIfVisible(ui)
                Thread.sleep(200)
            }
        }

        return try {
            ui.wakeUp()
            if (!ui.isScreenOn) {
                ui.wakeUp()
            }
            clickThread.start()
            activity.runOnUiThread {
                OmtLog.i("UsbTestPerm", "requestPermission ${device.deviceName}")
                usbManager.requestPermission(device, pending)
            }
            latch.await(10, TimeUnit.SECONDS)
            val ok = granted.get() || usbManager.hasPermission(device)
            OmtLog.i("UsbTestPerm", "result ${device.deviceName}: $ok")
            ok
        } finally {
            clickDone.set(true)
            clickThread.interrupt()
            runCatching { activity.unregisterReceiver(receiver) }
        }
    }

    private fun clickAllowIfVisible(ui: UiDevice) {
        val selectors = listOf(
            UiSelector().text("OK"),
            UiSelector().text("Allow"),
            UiSelector().textContains("Allow"),
            UiSelector().resourceId("android:id/button1"),
            UiSelector().resourceId("com.android.systemui:id/button_allow"),
        )
        for (selector in selectors) {
            val obj = ui.findObject(selector)
            if (obj.exists() && obj.isEnabled) {
                obj.click()
                return
            }
        }
    }
}
