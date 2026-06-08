package org.openmultitrack.app

import android.app.Application
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.mixer.behringer.OscDiscoveryLog

class OpenMultiTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OscDiscoveryLog.onSendFailed = { context, error ->
            OmtLog.w("OscDiscovery", "$context: ${error.javaClass.simpleName}: ${error.message}", error)
        }
        OscDiscoveryLog.onDebug = { message ->
            OmtLog.d("OscDiscovery", message)
        }
        OmtLog.i("App", "OpenMultiTrack ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) starting")
    }
}
