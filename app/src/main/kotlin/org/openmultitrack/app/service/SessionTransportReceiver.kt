package org.openmultitrack.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.openmultitrack.app.MainActivity
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode

class SessionTransportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val manager = AudioSessionBridge.mixerManager ?: return
        val mixerId = AudioSessionBridge.activeMixerId() ?: manager.activeMixerId.value ?: return
        val ctrl = manager.getOrCreate(mixerId)
        when (action) {
            ACTION_PAUSE_RECORD -> ctrl.pauseRecording()
            ACTION_STOP_RECORD -> {
                AudioSessionBridge.onNotificationStopRecord?.invoke(mixerId)
                    ?: ctrl.stopRecording()
                val openApp = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(openApp)
            }
            ACTION_TOGGLE_RECORD -> {
                val session = ctrl.state.value
                if (session.isRecording) ctrl.stopRecording() else ctrl.startRecording()
            }
            ACTION_TOGGLE_PLAYBACK -> {
                if (ctrl.state.value.appMode.isPlaybackMode) {
                    ctrl.toggleSoundcheckPlayback()
                }
            }
            ACTION_STOP_PLAYBACK -> ctrl.stopSoundcheck()
            ACTION_PREVIOUS -> ctrl.seekToPreviousTrackmark()
            ACTION_NEXT -> ctrl.seekToNextTrackmark()
            ACTION_STOP_ALL -> {
                manager.shutdownAll()
                context.startService(
                    Intent(context, AudioSessionService::class.java).setAction(AudioSessionService.ACTION_STOP_ALL),
                )
            }
        }
        AudioSessionBridge.rebuildNotification()
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
    var tickMediaProgress: (MixerSessionUiState?) -> Unit = {}
    /** Invoked when the user stops recording from the notification (after bringing the app forward). */
    var onNotificationStopRecord: ((mixerId: String) -> Unit)? = null
}
