package org.openmultitrack.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.openmultitrack.app.MainActivity
import org.openmultitrack.app.R

/**
 * Ongoing recorder-style notification (chronometer + pause/stop).
 *
 * Does not use [androidx.media.app.NotificationCompat.MediaStyle] — elapsed time is shown via
 * the platform chronometer, which is reliable across OEMs.
 */
object RecordingNotificationBuilder {
    fun build(context: Context, session: MixerSessionUiState?, mixerName: String?): NotificationCompat.Builder {
        val title = mixerName ?: context.getString(R.string.notification_title)
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val recordingColor = ContextCompat.getColor(context, R.color.notification_recording)
        val builder = NotificationCompat.Builder(context, AudioSessionService.CHANNEL_RECORDING)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_notification_recording)
            .setColor(recordingColor)
            .setColorized(true)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        when {
            session?.isRecording == true -> {
                val startedAt = session.recordStartedAtEpochMs.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
                builder
                    .setContentText(context.getString(R.string.notification_recording_active))
                    .setSubText(PlaybackNotificationTransport.formatClock(session.recordElapsedSec))
                    .setShowWhen(false)
                    .setUsesChronometer(true)
                    .setWhen(startedAt)
                    .setChronometerCountDown(false)
                addRecordingActions(context, builder, recording = true)
            }
            session?.isMonitoring == true -> {
                builder
                    .setContentText(context.getString(R.string.notification_monitoring_active))
                    .setShowWhen(false)
            }
            else -> {
                builder
                    .setContentText(context.getString(R.string.notification_recording_ready))
                    .setShowWhen(false)
                addRecordingActions(context, builder, recording = false)
            }
        }
        return builder
    }

    private fun addRecordingActions(
        context: Context,
        builder: NotificationCompat.Builder,
        recording: Boolean,
    ) {
        fun action(
            requestCode: Int,
            action: String,
            label: String,
            iconRes: Int,
            openApp: Boolean = false,
        ): NotificationCompat.Action {
            val pi = if (openApp) {
                NotificationActionIntents.mainActivity(context, requestCode, action)
            } else {
                NotificationActionIntents.broadcast(context, requestCode, action)
            }
            return NotificationCompat.Action.Builder(iconRes, label, pi).build()
        }
        if (recording) {
            builder.addAction(
                action(
                    10,
                    SessionTransportReceiver.ACTION_PAUSE_RECORD,
                    context.getString(R.string.notification_action_pause),
                    android.R.drawable.ic_media_pause,
                ),
            )
            builder.addAction(
                action(
                    11,
                    SessionTransportReceiver.ACTION_STOP_RECORD,
                    context.getString(R.string.notification_action_stop),
                    R.drawable.ic_media_stop,
                    openApp = true,
                ),
            )
        } else {
            builder.addAction(
                action(
                    12,
                    SessionTransportReceiver.ACTION_TOGGLE_RECORD,
                    context.getString(R.string.notification_action_record),
                    android.R.drawable.ic_media_play,
                ),
            )
        }
        builder.addAction(
            action(
                17,
                SessionTransportReceiver.ACTION_STOP_ALL,
                context.getString(R.string.notification_stop),
                android.R.drawable.ic_menu_close_clear_cancel,
            ),
        )
    }
}
