package org.openmultitrack.app.service

import android.content.Context
import java.io.File
import org.openmultitrack.app.R
import org.openmultitrack.domain.session.isPlaybackMode
/** Shared copy for the playback notification and its [android.support.v4.media.session.MediaSessionCompat]. */
object PlaybackNotificationContent {
    data class Display(
        val sessionTitle: String,
        val mixerLine: String,
        val totalTimeLine: String?,
        val progressLine: String?,
    )

    fun resolve(
        context: Context,
        session: MixerSessionUiState?,
        mixerName: String?,
    ): Display {
        if (session?.appMode?.isPlaybackMode != true) {
            val fallback = mixerName ?: context.getString(R.string.notification_title)
            return Display(
                sessionTitle = fallback,
                mixerLine = PlaybackNotificationBuilder.statusLine(session),
                totalTimeLine = null,
                progressLine = null,
            )
        }
        val sessionTitle = sessionTitle(session)
        val mixerLine = mixerName ?: context.getString(R.string.notification_title)
        val durationSec = session.playbackDurationSec.takeIf { it > 0f }
        val totalTimeLine = durationSec?.let {
            context.getString(
                R.string.notification_playback_total,
                PlaybackNotificationTransport.formatClock(it),
            )
        }
        val progressLine = if (durationSec != null) {
            PlaybackNotificationTransport.formatTimestamp(
                session.playbackPositionSec,
                durationSec,
            )
        } else {
            null
        }
        return Display(
            sessionTitle = sessionTitle,
            mixerLine = mixerLine,
            totalTimeLine = totalTimeLine,
            progressLine = progressLine,
        )
    }

    fun sessionTitle(session: MixerSessionUiState): String {
        val dir = session.selectedSoundcheckDir
        if (dir != null) {
            session.soundcheckSessions.firstOrNull { it.sessionDir == dir }?.title?.let { title ->
                if (title.isNotBlank()) return title
            }
            File(dir).name.takeIf { it.isNotBlank() }?.let { return it }
        }
        session.lastRecordingPath?.let { path ->
            File(path).name.takeIf { it.isNotBlank() }?.let { return it }
        }
        return session.mixerProfile?.displayName ?: "Session"
    }
}
