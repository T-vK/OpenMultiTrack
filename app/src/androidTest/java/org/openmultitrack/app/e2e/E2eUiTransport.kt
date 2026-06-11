package org.openmultitrack.app.e2e

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector

/** UiAutomator taps on DAW transport controls (content descriptions from [DawTopBar]). */
object E2eUiTransport {
    private val ui: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun clickContentDescription(description: String, timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        ui.wakeUp()
        while (System.currentTimeMillis() < deadline) {
            val obj = ui.findObject(UiSelector().description(description))
            if (obj.waitForExists(500)) {
                check(obj.isEnabled) { "UI control disabled: $description" }
                obj.click()
                return
            }
            Thread.sleep(250)
        }
        error("UI element not found: contentDescription=$description")
    }
}
