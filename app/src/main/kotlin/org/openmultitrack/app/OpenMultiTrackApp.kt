package org.openmultitrack.app

import android.app.Application
import org.openmultitrack.app.util.AppLogBuffer
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.mixer.behringer.OscDiscoveryLog
import org.openmultitrack.mixer.behringer.Xr18RoutingLog

class OpenMultiTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        OscDiscoveryLog.onSendFailed = { context, error ->
            OmtLog.w("OscDiscovery", "$context: ${error.javaClass.simpleName}: ${error.message}", error)
        }
        OscDiscoveryLog.onDebug = { message ->
            OmtLog.d("OscDiscovery", message)
        }
        Xr18RoutingLog.onInfo = { message ->
            OmtLog.i("Xr18Routing", message)
        }
        OmtLog.i("App", "OpenMultiTrack ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) starting")
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                AppLogBuffer.append(
                    "E",
                    "Crash",
                    "Uncaught on ${thread.name}: ${throwable.javaClass.name}: ${throwable.message}",
                )
                AppLogBuffer.appendThrowable("E", "Crash", throwable)
                AppLogBuffer.persistSession(this)
            } catch (_: Exception) {
                // Best-effort only — never block the default handler.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
