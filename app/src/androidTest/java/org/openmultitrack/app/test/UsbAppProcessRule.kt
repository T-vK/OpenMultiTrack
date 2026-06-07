package org.openmultitrack.app.test

import android.content.Context
import androidx.test.core.app.ActivityScenario
import org.junit.rules.ExternalResource
import org.openmultitrack.app.MainActivity

/**
 * Hardware USB calls must run in the app process (org.openmultitrack). Instrumentation tests
 * execute in org.openmultitrack.test, and [android.hardware.usb.UsbManager.hasPermission] checks
 * the calling UID — so pre-granted emulator permissions do not apply from [targetContext] alone.
 */
class UsbAppProcessRule : ExternalResource() {
    private lateinit var scenario: ActivityScenario<MainActivity>
    lateinit var appContext: Context
        private set

    override fun before() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            appContext = activity.applicationContext
        }
    }

    override fun after() {
        scenario.close()
    }
}
