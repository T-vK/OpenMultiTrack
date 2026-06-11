package org.openmultitrack.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SessionTransportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val mixerId = intent.getStringExtra(SessionTransportActions.EXTRA_MIXER_ID)
        if (SessionTransportActions.handle(context, action, fromActivity = false, mixerId = mixerId)) {
            AudioSessionBridge.rebuildNotification()
        }
    }

    companion object {
        const val ACTION_PAUSE_RECORD = "org.openmultitrack.NOTIFICATION_PAUSE_RECORD"
        const val ACTION_STOP_RECORD = "org.openmultitrack.NOTIFICATION_STOP_RECORD"
        const val ACTION_TOGGLE_RECORD = "org.openmultitrack.NOTIFICATION_TOGGLE_RECORD"
        const val ACTION_TOGGLE_PLAYBACK = "org.openmultitrack.NOTIFICATION_TOGGLE_PLAYBACK"
        const val ACTION_STOP_PLAYBACK = "org.openmultitrack.NOTIFICATION_STOP_PLAYBACK"
        const val ACTION_PREVIOUS = "org.openmultitrack.NOTIFICATION_PREVIOUS"
        const val ACTION_NEXT = "org.openmultitrack.NOTIFICATION_NEXT"
        const val ACTION_STOP_ALL = AudioSessionService.ACTION_STOP_ALL
    }
}

/** Service ↔ notification action bridge (process-local). */
object AudioSessionBridge {
    var mixerManager: MultiMixerSessionManager? = null
    var activeMixerId: () -> String? = { mixerManager?.activeMixerId?.value }
    var rebuildNotification: () -> Unit = {}
    /** Fast playback-only notification refresh (skips full multi-mixer sync). */
    var refreshPlaybackNotification: (MixerSessionUiState?) -> Unit = {}
    var tickMediaProgress: (MixerSessionUiState?) -> Unit = {}
    /** Invoked when the user stops recording from the notification (after bringing the app forward). */
    var onNotificationStopRecord: ((mixerId: String) -> Unit)? = null
}
