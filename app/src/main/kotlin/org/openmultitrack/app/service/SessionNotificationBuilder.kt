package org.openmultitrack.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import org.openmultitrack.app.MainActivity
import org.openmultitrack.app.R
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode

object SessionNotificationBuilder {
    fun build(
        context: Context,
        session: MixerSessionUiState?,
        mixerName: String?,
        media: SessionMediaNotification,
    ): NotificationCompat.Builder {
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (session?.isRecording == true) {
            val startedAt = session.recordStartedAtEpochMs.takeIf { it > 0L }
                ?: (System.currentTimeMillis() - (session.recordElapsedSec * 1000f).toLong())
            builder.setWhen(startedAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
        } else {
            builder.setShowWhen(false)
        }
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
        session.isRecording -> "Recording"
        session.isPlaying -> "Playing"
        session.isMonitoring -> "Monitoring"
        session.appMode == AppMode.MULTITRACK_RECORD -> "Ready to record"
        session.appMode.isPlaybackMode -> "Soundcheck"
        else -> "Audio session active"
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
        when {
            session?.isRecording == true -> {
                addTracked(
                    broadcastAction(
                        10,
                        SessionTransportReceiver.ACTION_PAUSE_RECORD,
                        "Pause",
                        android.R.drawable.ic_media_pause,
                    ),
                )
                addTracked(
                    broadcastAction(
                        11,
                        SessionTransportReceiver.ACTION_STOP_RECORD,
                        "Stop",
                        R.drawable.ic_media_stop,
                    ),
                )
            }
            session?.appMode == AppMode.MULTITRACK_RECORD -> {
                addTracked(
                    broadcastAction(
                        12,
                        SessionTransportReceiver.ACTION_TOGGLE_RECORD,
                        "Record",
                        android.R.drawable.ic_media_play,
                    ),
                )
            }
            session?.appMode?.isPlaybackMode == true -> {
                val showChapters = session.appMode != AppMode.SIMPLE_PLAY && session.trackmarks.isNotEmpty()
                if (showChapters) {
                    addTracked(
                        broadcastAction(
                            13,
                            SessionTransportReceiver.ACTION_PREVIOUS,
                            "Previous",
                            android.R.drawable.ic_media_previous,
                        ),
                    )
                }
                val playLabel = if (session.isPlaying) "Pause" else "Play"
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
                            "Next",
                            android.R.drawable.ic_media_next,
                        ),
                    )
                }
                if (compact.size < 3) {
                    addTracked(
                        broadcastAction(
                            16,
                            SessionTransportReceiver.ACTION_STOP_PLAYBACK,
                            "Stop",
                            R.drawable.ic_media_stop,
                        ),
                    )
                } else {
                    addSilent(
                        broadcastAction(
                            16,
                            SessionTransportReceiver.ACTION_STOP_PLAYBACK,
                            "Stop",
                            R.drawable.ic_media_stop,
                        ),
                    )
                }
            }
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
