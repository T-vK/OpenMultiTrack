package org.openmultitrack.app.service

import android.content.Context
import android.content.Intent
import org.openmultitrack.app.MainActivity
import org.openmultitrack.domain.session.isPlaybackMode

/** Handles notification transport commands for recording and playback. */
object SessionTransportActions {
    private val notificationActions = setOf(
        SessionTransportReceiver.ACTION_PAUSE_RECORD,
        SessionTransportReceiver.ACTION_STOP_RECORD,
        SessionTransportReceiver.ACTION_TOGGLE_RECORD,
        SessionTransportReceiver.ACTION_TOGGLE_PLAYBACK,
        SessionTransportReceiver.ACTION_STOP_PLAYBACK,
        SessionTransportReceiver.ACTION_PREVIOUS,
        SessionTransportReceiver.ACTION_NEXT,
        SessionTransportReceiver.ACTION_STOP_ALL,
    )

    fun isNotificationAction(action: String?): Boolean = action in notificationActions

    /**
     * @param fromActivity when true, the user already opened [MainActivity] (shade collapses on its own).
     * @return whether the caller should synchronously rebuild the foreground notification.
     */
    fun handle(context: Context, action: String, fromActivity: Boolean = false): Boolean {
        val manager = AudioSessionBridge.mixerManager ?: return false
        val mixerId = AudioSessionBridge.activeMixerId() ?: manager.activeMixerId.value ?: return false
        val ctrl = manager.getOrCreate(mixerId)
        return when (action) {
            SessionTransportReceiver.ACTION_PAUSE_RECORD -> {
                ctrl.pauseRecording()
                false
            }
            SessionTransportReceiver.ACTION_STOP_RECORD -> {
                if (!fromActivity) {
                    context.startActivity(openMainActivityIntent(context))
                }
                AudioSessionBridge.onNotificationStopRecord?.invoke(mixerId)
                    ?: ctrl.stopRecording()
                false
            }
            SessionTransportReceiver.ACTION_TOGGLE_RECORD -> {
                if (ctrl.state.value.isRecording) ctrl.stopRecording() else ctrl.startRecording()
                false
            }
            SessionTransportReceiver.ACTION_TOGGLE_PLAYBACK -> {
                if (ctrl.state.value.appMode.isPlaybackMode) {
                    ctrl.toggleSoundcheckPlayback()
                }
                false
            }
            SessionTransportReceiver.ACTION_STOP_PLAYBACK -> {
                ctrl.stopSoundcheck()
                false
            }
            SessionTransportReceiver.ACTION_PREVIOUS -> {
                ctrl.seekToPreviousTrackmark()
                false
            }
            SessionTransportReceiver.ACTION_NEXT -> {
                ctrl.seekToNextTrackmark()
                false
            }
            SessionTransportReceiver.ACTION_STOP_ALL -> {
                manager.shutdownAll()
                context.startService(
                    Intent(context, AudioSessionService::class.java)
                        .setAction(AudioSessionService.ACTION_STOP_ALL),
                )
                true
            }
            else -> false
        }
    }

    fun openMainActivityIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
}
