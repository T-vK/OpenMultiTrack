package org.openmultitrack.app.e2e

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import org.openmultitrack.app.MainActivity
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.domain.remote.RemoteRole

/**
 * Launches [MainActivity] in the app process and supports relaunch after force-stop.
 */
class E2eAppRule(
    private val enableWaveformsAndVu: Boolean = false,
) : ExternalResource() {
    private var scenario: ActivityScenario<MainActivity>? = null
    lateinit var appContext: Context
        private set

    override fun before() {
        launchFresh()
    }

    override fun after() {
        scenario?.close()
        scenario = null
    }

    fun launchFresh() {
        scenario?.close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AppSettingsStore(context)
        settings.remoteRole = RemoteRole.OFF
        settings.showVuMeters = enableWaveformsAndVu
        settings.showWaveforms = enableWaveformsAndVu
        MixerDeviceStore(context).listMixers()
            .filter { it.vendorId != E2eConfig.XR18_VENDOR_ID || it.productId != E2eConfig.XR18_PRODUCT_ID }
            .forEach { MixerDeviceStore(context).removeMixer(it.id) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            appContext = activity.applicationContext
        }
        Thread.sleep(1_500)
    }

    fun forceStopApp() {
        scenario?.close()
        scenario = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("am force-stop org.openmultitrack")
        Thread.sleep(2_000)
    }

    fun <T> runOnActivity(block: (MainActivity) -> T): T {
        val active = scenario ?: error("Activity not launched")
        var result: T? = null
        active.onActivity { activity ->
            result = block(activity)
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
