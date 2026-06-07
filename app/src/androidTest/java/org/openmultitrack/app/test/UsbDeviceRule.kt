package org.openmultitrack.app.test

import android.hardware.usb.UsbManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class UsbDeviceRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val annotation = description.getAnnotation(RequiresUsbDevice::class.java)
                    ?: description.testClass?.getAnnotation(RequiresUsbDevice::class.java)
                if (annotation != null) {
                    val context = InstrumentationRegistry.getInstrumentation().targetContext
                    val usbManager = context.getSystemService(UsbManager::class.java)
                    val match = usbManager.deviceList.values.firstOrNull { device ->
                        val vendorOk = annotation.vendorId == 0 || device.vendorId == annotation.vendorId
                        val productOk = annotation.productId == 0 || device.productId == annotation.productId
                        vendorOk && productOk
                    }
                    assumeTrue(
                        "USB device vid=0x${annotation.vendorId.toString(16)} " +
                            "pid=0x${annotation.productId.toString(16)} not attached",
                        match != null,
                    )
                }
                base.evaluate()
            }
        }
}
