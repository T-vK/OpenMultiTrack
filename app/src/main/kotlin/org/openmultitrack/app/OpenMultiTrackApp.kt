package org.openmultitrack.app

import android.app.Application
import org.openmultitrack.audio.OmtLog

class OpenMultiTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OmtLog.i("App", "OpenMultiTrack ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) starting")
    }
}
