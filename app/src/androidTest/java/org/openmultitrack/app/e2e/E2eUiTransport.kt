package org.openmultitrack.app.e2e

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import org.openmultitrack.app.ui.daw.DawTransportSemantics
import org.openmultitrack.app.ui.daw.parseSoundcheckTransportLabel
import org.openmultitrack.app.ui.daw.parseTransportTime

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

    /** Recording timer from accessibility label ("Recording elapsed M:SS"). */
    fun readRecordingElapsedSec(timeoutMs: Long = 3_000): Float {
        val label = readTransportLabel(
            testTag = DawTransportSemantics.RECORDING_ELAPSED_TEST_TAG,
            descriptionPrefix = DawTransportSemantics.RECORDING_ELAPSED_PREFIX,
            timeoutMs = timeoutMs,
        )
        if (label.startsWith(DawTransportSemantics.RECORDING_ELAPSED_PREFIX)) {
            val timeText = label.removePrefix(DawTransportSemantics.RECORDING_ELAPSED_PREFIX).trim()
            parseTransportTime(timeText)?.let { return it }
        }
        parseTransportTime(label)?.let { return it }
        error("Could not parse recording elapsed from UI label: $label")
    }

    /** Soundcheck position/duration from accessibility label ("Soundcheck transport M:SS of M:SS"). */
    fun readSoundcheckTransport(timeoutMs: Long = 3_000): Pair<Float, Float> {
        val label = readTransportLabel(
            testTag = DawTransportSemantics.SOUNDCHECK_TRANSPORT_TEST_TAG,
            descriptionPrefix = DawTransportSemantics.SOUNDCHECK_TRANSPORT_PREFIX,
            timeoutMs = timeoutMs,
        )
        parseSoundcheckTransportLabel(label)?.let { return it }
        val slashSplit = label.split(" / ", limit = 2)
        if (slashSplit.size == 2) {
            val position = parseTransportTime(slashSplit[0].trim())
            val duration = parseTransportTime(slashSplit[1].trim())
            if (position != null && duration != null) return position to duration
        }
        error("Could not parse soundcheck transport from UI label: $label")
    }

    private fun readTransportLabel(
        testTag: String,
        descriptionPrefix: String,
        timeoutMs: Long,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        ui.wakeUp()
        while (System.currentTimeMillis() < deadline) {
            findByTestTag(testTag)?.let { obj ->
                obj.contentDescription?.takeIf { it.isNotBlank() }?.let { return it }
                obj.text?.takeIf { it.isNotBlank() }?.let { return it }
            }
            findByDescriptionPrefix(descriptionPrefix)?.let { return it }
            Thread.sleep(200)
        }
        error("UI transport label not found (tag=$testTag prefix=$descriptionPrefix)")
    }

    private fun findByTestTag(testTag: String): UiObject2? {
        val byExact = ui.findObject(androidx.test.uiautomator.By.res(testTag))
        if (byExact != null) return byExact
        val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        return ui.findObject(androidx.test.uiautomator.By.res(pkg, testTag))
    }

    private fun findByDescriptionPrefix(prefix: String): String? {
        val obj = ui.findObject(UiSelector().descriptionStartsWith(prefix))
        if (obj.waitForExists(200)) {
            val description = obj.contentDescription
            if (!description.isNullOrBlank()) return description
        }
        return null
    }
}
