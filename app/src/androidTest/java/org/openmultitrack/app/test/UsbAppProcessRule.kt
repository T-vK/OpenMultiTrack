package org.openmultitrack.app.test

import android.content.Context
import androidx.test.core.app.ActivityScenario
import org.junit.rules.ExternalResource
import org.openmultitrack.app.MainActivity

/**
 * Hardware USB IPC uses the **calling process UID**, not just [Context.getPackageName].
 * Instrumentation runs in org.openmultitrack.test; use [runOnActivity] so UsbManager calls
 * execute in org.openmultitrack (same as a user opening the app).
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

    fun <T> runOnActivity(block: (MainActivity) -> T): T {
        var result: T? = null
        scenario.onActivity { activity ->
            result = block(activity)
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
