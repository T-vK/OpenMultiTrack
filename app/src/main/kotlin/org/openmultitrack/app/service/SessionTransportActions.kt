package org.openmultitrack.app.service

import android.content.Context
import android.content.Intent
import org.openmultitrack.app.MainActivity
import org.openmultitrack.domain.session.isPlaybackMode

/** Handles notification transport commands for recording and playback. */
object SessionTransportActions {
    const val EXTRA_MIXER_ID = "org.openmultitrack.extra.MIXER_ID"

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
    fun handle(
        context: Context,
        action: String,
        fromActivity: Boolean = false,
        mixerId: String? = null,
    ): Boolean {
        val manager = AudioSessionBridge.mixerManager ?: return false
        val resolvedMixerId = mixerId
            ?: AudioSessionBridge.activeMixerId()
            ?: manager.activeMixerId.value
            ?: return false
        val ctrl = manager.getOrCreate(resolvedMixerId)
        return when (action) {
            SessionTransportReceiver.ACTION_PAUSE_RECORD -> {
                ctrl.pauseRecording()
                false
            }
            SessionTransportReceiver.ACTION_STOP_RECORD -> {
                if (!fromActivity) {
                    context.startActivity(
                        openMainActivityIntent(context, resolvedMixerId, action),
                    )
                }
                AudioSessionBridge.onNotificationStopRecord?.invoke(resolvedMixerId)
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

    fun openMainActivityIntent(
        context: Context,
        mixerId: String? = null,
        action: String? = null,
    ): Intent =
        Intent(context, MainActivity::class.java).apply {
            action?.let { setAction(it) }
            mixerId?.let { putExtra(EXTRA_MIXER_ID, it) }
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
}
