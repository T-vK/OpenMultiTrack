package org.openmultitrack.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.openmultitrack.app.R

/**
 * Ongoing recorder-style notification (chronometer + stop) — one per recording mixer.
 *
 * Does not use [androidx.media.app.NotificationCompat.MediaStyle] — elapsed time is shown via
 * the platform chronometer, which is reliable across OEMs.
 */
object RecordingNotificationBuilder {
    fun build(
        context: Context,
        mixerId: String,
        session: MixerSessionUiState?,
        mixerName: String?,
    ): NotificationCompat.Builder {
        val title = mixerName ?: context.getString(R.string.notification_title)
        val openIntent = PendingIntent.getActivity(
            context,
            openRequestCode(mixerId),
            SessionTransportActions.openMainActivityIntent(context, mixerId),
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
                builder.addAction(
                    stopAction(context, mixerId),
                )
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
            }
        }
        return builder
    }

    private fun stopAction(context: Context, mixerId: String): NotificationCompat.Action {
        val requestCode = stopRequestCode(mixerId)
        val pendingIntent = NotificationActionIntents.mainActivity(
            context,
            requestCode,
            SessionTransportReceiver.ACTION_STOP_RECORD,
            mixerId,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_media_stop,
            context.getString(R.string.notification_action_stop),
            pendingIntent,
        ).build()
    }

    private fun openRequestCode(mixerId: String): Int =
        2_000 + (mixerId.hashCode() and 0x7FFF)

    private fun stopRequestCode(mixerId: String): Int =
        3_000 + (mixerId.hashCode() and 0x7FFF)
}
