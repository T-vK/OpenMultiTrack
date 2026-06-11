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

/** Media-style player notification backed by [PlaybackMediaNotification]. */
object PlaybackNotificationBuilder {
    fun build(
        context: Context,
        session: MixerSessionUiState?,
        mixerName: String?,
        media: PlaybackMediaNotification,
    ): NotificationCompat.Builder {
        val title = mixerName ?: context.getString(R.string.notification_title)
        val subtitle = statusLine(session)
        val timeSubtitle = timeSubtitle(session)
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, AudioSessionService.CHANNEL_PLAYBACK)
            .setContentTitle(title)
            .setContentText(timeSubtitle ?: subtitle)
            .setSubText(if (timeSubtitle != null) subtitle else null)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
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
        session.isPlaying -> "Playing"
        session.appMode.isPlaybackMode -> "Soundcheck"
        else -> "Audio session active"
    }

    private fun timeSubtitle(session: MixerSessionUiState?): String? {
        if (session?.appMode?.isPlaybackMode != true) return null
        if (!session.isPlaying && session.playbackDurationSec <= 0f) return null
        return PlaybackNotificationTransport.formatTimestamp(
            session.playbackPositionSec,
            session.playbackDurationSec.takeIf { it > 0f },
        )
    }

    private fun addTransportActions(
        context: Context,
        builder: NotificationCompat.Builder,
        session: MixerSessionUiState?,
    ): IntArray {
        val compact = mutableListOf<Int>()
        var actionIndex = 0
        fun broadcastAction(
            requestCode: Int,
            action: String,
            label: String,
            iconRes: Int,
        ): NotificationCompat.Action {
            val intent = Intent(context, SessionTransportReceiver::class.java).setAction(action)
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Action.Builder(iconRes, label, pi).build()
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
        if (session?.appMode?.isPlaybackMode == true) {
            val showChapters = session.appMode != AppMode.SIMPLE_PLAY && session.trackmarks.isNotEmpty()
            if (showChapters) {
                addTracked(
                    broadcastAction(
                        13,
                        SessionTransportReceiver.ACTION_PREVIOUS,
                        context.getString(R.string.notification_action_previous),
                        android.R.drawable.ic_media_previous,
                    ),
                )
            }
            val playLabel = if (session.isPlaying) {
                context.getString(R.string.notification_action_pause)
            } else {
                context.getString(R.string.notification_action_play)
            }
            val playIcon = if (session.isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            addTracked(
                broadcastAction(
                    14,
                    SessionTransportReceiver.ACTION_TOGGLE_PLAYBACK,
                    playLabel,
                    playIcon,
                ),
            )
            if (showChapters) {
                addTracked(
                    broadcastAction(
                        15,
                        SessionTransportReceiver.ACTION_NEXT,
                        context.getString(R.string.notification_action_next),
                        android.R.drawable.ic_media_next,
                    ),
                )
            }
            addTracked(
                broadcastAction(
                    16,
                    SessionTransportReceiver.ACTION_STOP_PLAYBACK,
                    context.getString(R.string.notification_action_stop),
                    R.drawable.ic_media_stop,
                ),
            )
        }
        addSilent(
            broadcastAction(
                17,
                SessionTransportReceiver.ACTION_STOP_ALL,
                context.getString(R.string.notification_stop),
                android.R.drawable.ic_menu_close_clear_cancel,
            ),
        )
        return compact.toIntArray()
    }
}
