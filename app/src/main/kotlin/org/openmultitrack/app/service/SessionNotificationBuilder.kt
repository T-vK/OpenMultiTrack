package org.openmultitrack.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.app.NotificationCompat
import androidx.core.graphics.createBitmap
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
            .setSubText(session?.statusMessage)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLargeIcon(waveformBitmap(session))
        addTransportActions(context, builder, session)
        return builder
    }

    private fun statusLine(session: MixerSessionUiState?): String = when {
        session == null -> "Audio session active"
        session.isRecording -> "Recording · ${formatSec(session.recordElapsedSec)}"
        session.isPlaying -> "Playing · ${formatSec(session.playbackPositionSec)}"
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

    private fun waveformBitmap(session: MixerSessionUiState?): Bitmap {
        val width = 256
        val height = 64
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawARGB(255, 24, 24, 28)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4FC3F7.toInt()
            strokeWidth = 2f
        }
        val peaks = session?.waveformPeaks?.values?.firstOrNull()?.peaks ?: FloatArray(0)
        if (peaks.isEmpty()) {
            val mid = height / 2f
            canvas.drawLine(0f, mid, width.toFloat(), mid, paint.apply { alpha = 80 })
            return bitmap
        }
        val step = peaks.size.toFloat() / width
        for (x in 0 until width) {
            val idx = (x * step).toInt().coerceIn(0, peaks.lastIndex)
            val amp = peaks[idx].coerceIn(0f, 1f)
            val h = amp * (height * 0.45f)
            val mid = height / 2f
            canvas.drawLine(x.toFloat(), mid - h, x.toFloat(), mid + h, paint)
        }
        return bitmap
    }

    private fun addTransportActions(
        context: Context,
        builder: NotificationCompat.Builder,
        session: MixerSessionUiState?,
    ) {
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
        when {
            session?.isRecording == true -> {
                builder.addAction(action(10, SessionTransportReceiver.ACTION_TOGGLE_RECORD, "Stop"))
            }
            session?.appMode == AppMode.MULTITRACK_RECORD -> {
                builder.addAction(action(11, SessionTransportReceiver.ACTION_TOGGLE_RECORD, "Record"))
            }
            session?.appMode?.isPlaybackMode == true -> {
                val playLabel = if (session.isPlaying) "Pause" else "Play"
                builder.addAction(
                    action(12, SessionTransportReceiver.ACTION_TOGGLE_PLAYBACK, playLabel),
                )
                if (session.appMode != AppMode.SIMPLE_PLAY) {
                    builder.addAction(action(13, SessionTransportReceiver.ACTION_PREVIOUS, "Prev"))
                    builder.addAction(action(14, SessionTransportReceiver.ACTION_NEXT, "Next"))
                }
                builder.addAction(action(15, SessionTransportReceiver.ACTION_STOP_PLAYBACK, "Stop"))
            }
        }
        builder.addAction(
            action(16, SessionTransportReceiver.ACTION_STOP_ALL, context.getString(R.string.notification_stop)),
        )
    }
}
