package org.openmultitrack.app.e2e

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector

/** UiAutomator taps on DAW transport controls (content descriptions from [DawTopBar]). */
object E2eUiTransport {
    private val ui: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun waitForContentDescription(description: String, timeoutMs: Long = 30_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        ui.wakeUp()
        while (System.currentTimeMillis() < deadline) {
            if (ui.findObject(UiSelector().description(description)).waitForExists(500)) {
                return
            }
            Thread.sleep(250)
        }
        error("UI element not found: contentDescription=$description")
    }

    fun clickContentDescription(description: String, timeoutMs: Long = 20_000) {
        waitForContentDescription(description, timeoutMs)
        val obj = ui.findObject(UiSelector().description(description))
        check(obj.isEnabled) { "UI control disabled: $description" }
        obj.click()
    }

    /** Opens the mode menu and picks [modeLabel] (e.g. "Virtual Soundcheck"). */
    fun selectAppMode(modeLabel: String) {
        clickContentDescription("Change mode")
        Thread.sleep(400)
        clickText(modeLabel)
        Thread.sleep(800)
    }

    fun clickText(text: String, timeoutMs: Long = 10_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        ui.wakeUp()
        while (System.currentTimeMillis() < deadline) {
            val obj = ui.findObject(UiSelector().text(text))
            if (obj.waitForExists(500)) {
                obj.click()
                return
            }
            Thread.sleep(250)
        }
        error("UI element not found: text=$text")
    }
}
