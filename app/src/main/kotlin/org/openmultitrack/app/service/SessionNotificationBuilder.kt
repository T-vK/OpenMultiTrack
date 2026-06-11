package org.openmultitrack.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import org.openmultitrack.app.MainActivity
import org.openmultitrack.app.R
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode
import kotlin.math.max

object SessionNotificationBuilder {
    fun build(
        context: Context,
        session: MixerSessionUiState?,
        mixerName: String?,
        media: SessionMediaNotification,
    ): NotificationCompat.Builder {
        media.update(session, mixerName)
        val title = mixerName ?: context.getString(R.string.notification_title)
        val subtitle = statusLine(session)
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, AudioSessionService.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText(session?.statusMessage)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        val compactActions = addTransportActions(context, builder, session)
        builder.setStyle(
            MediaStyle()
                .setMediaSession(media.sessionToken())
                .setShowActionsInCompactView(*compactActions),
        )
        return builder
    }

    fun statusLine(session: MixerSessionUiState?): String = when {
        session == null -> "Audio session active"
        session.isRecording -> "Recording · ${formatSec(session.recordElapsedSec)}"
        session.isPlaying -> {
            val pos = formatSec(session.playbackPositionSec)
            val dur = session.playbackDurationSec.takeIf { it > 0f }?.let { formatSec(it) }
            if (dur != null) "Playing · $pos / $dur" else "Playing · $pos"
        }
        session.isMonitoring -> "Monitoring"
        session.appMode == AppMode.MULTITRACK_RECORD -> "Ready to record"
        session.appMode.isPlaybackMode -> "Soundcheck"
        else -> "Audio session active"
    }

    private fun formatSec(sec: Float): String {
        val total = max(0, sec.toInt())
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }

    private fun addTransportActions(
        context: Context,
        builder: NotificationCompat.Builder,
        session: MixerSessionUiState?,
    ): IntArray {
        val compact = mutableListOf<Int>()
        var actionIndex = 0
        fun action(requestCode: Int, action: String, label: String): NotificationCompat.Action {
            val intent = Intent(context, SessionTransportReceiver::class.java).setAction(action)
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }
        fun addTracked(act: NotificationCompat.Action) {
            builder.addAction(act)
            compact += actionIndex
            actionIndex++
        }
        fun addSilent(act: NotificationCompat.Action) {
            builder.addAction(act)
            actionIndex++
        }
        when {
            session?.isRecording == true -> {
                addTracked(action(10, SessionTransportReceiver.ACTION_TOGGLE_RECORD, "Stop"))
            }
            session?.appMode == AppMode.MULTITRACK_RECORD -> {
                addTracked(action(11, SessionTransportReceiver.ACTION_TOGGLE_RECORD, "Record"))
            }
            session?.appMode?.isPlaybackMode == true -> {
                val playLabel = if (session.isPlaying) "Pause" else "Play"
                addTracked(
                    action(12, SessionTransportReceiver.ACTION_TOGGLE_PLAYBACK, playLabel),
                )
                if (session.appMode != AppMode.SIMPLE_PLAY) {
                    addSilent(action(13, SessionTransportReceiver.ACTION_PREVIOUS, "Prev"))
                    addSilent(action(14, SessionTransportReceiver.ACTION_NEXT, "Next"))
                }
                if (compact.size < 3) {
                    addTracked(action(15, SessionTransportReceiver.ACTION_STOP_PLAYBACK, "Stop"))
                } else {
                    addSilent(action(15, SessionTransportReceiver.ACTION_STOP_PLAYBACK, "Stop"))
                }
            }
        }
        addSilent(
            action(16, SessionTransportReceiver.ACTION_STOP_ALL, context.getString(R.string.notification_stop)),
        )
        return compact.toIntArray()
    }
}
