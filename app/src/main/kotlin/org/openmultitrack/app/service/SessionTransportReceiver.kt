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
        val rebuildAfterAction = when (action) {
            ACTION_PAUSE_RECORD -> {
                ctrl.pauseRecording()
                false
            }
            ACTION_STOP_RECORD -> {
                val openApp = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                    )
                }
                context.startActivity(openApp)
                AudioSessionBridge.onNotificationStopRecord?.invoke(mixerId)
                    ?: ctrl.stopRecording()
                false
            }
            ACTION_TOGGLE_RECORD -> {
                val wasRecording = ctrl.state.value.isRecording
                if (wasRecording) ctrl.stopRecording() else ctrl.startRecording()
                false
            }
            ACTION_TOGGLE_PLAYBACK -> {
                if (ctrl.state.value.appMode.isPlaybackMode) {
                    ctrl.toggleSoundcheckPlayback()
                }
                false
            }
            ACTION_STOP_PLAYBACK -> {
                ctrl.stopSoundcheck()
                false
            }
            ACTION_PREVIOUS -> {
                ctrl.seekToPreviousTrackmark()
                false
            }
            ACTION_NEXT -> {
                ctrl.seekToNextTrackmark()
                false
            }
            ACTION_STOP_ALL -> {
                manager.shutdownAll()
                context.startService(
                    Intent(context, AudioSessionService::class.java).setAction(AudioSessionService.ACTION_STOP_ALL),
                )
                true
            }
            else -> false
        }
        if (rebuildAfterAction) {
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
    var tickMediaProgress: (MixerSessionUiState?) -> Unit = {}
    /** Invoked when the user stops recording from the notification (after bringing the app forward). */
    var onNotificationStopRecord: ((mixerId: String) -> Unit)? = null
}
