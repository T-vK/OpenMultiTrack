package org.openmultitrack.app.service

import kotlin.math.max
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode

/**
 * Maps app session state to [android.support.v4.media.session.PlaybackStateCompat] fields.
 *
 * System media controls (Android 13+) read position and [MediaMetadataCompat.METADATA_KEY_DURATION]
 * for elapsed/total clocks and the progress bar. [androidx.core.app.NotificationCompat] subtitles
 * are not shown there.
 *
 * Recording keeps the playhead at the end of a growing duration bar (live / squiggly progress).
 * Standard [android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP] is not shown on
 * Android 13+ — use [CUSTOM_ACTION_STOP] instead. Prev/next use the standard skip actions.
 */
object SessionNotificationTransport {
    const val CUSTOM_ACTION_STOP = "org.openmultitrack.action.STOP"

    data class Snapshot(
        val positionMs: Long,
        val durationMs: Long,
        val state: Int,
        val standardActions: Long,
        val customActionIds: List<String>,
    )

    fun snapshot(session: MixerSessionUiState?): Snapshot {
        when {
            session?.isRecording == true -> {
                val elapsedMs = (session.recordElapsedSec * 1000f).toLong().coerceAtLeast(0L)
                val durationMs = max(elapsedMs, 1_000L)
                return Snapshot(
                    positionMs = durationMs,
                    durationMs = durationMs,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING,
                    standardActions = recordingActions(),
                    customActionIds = listOf(CUSTOM_ACTION_STOP),
                )
            }
            session?.isPlaying == true -> {
                val positionMs = (session.playbackPositionSec * 1000f).toLong().coerceAtLeast(0L)
                val durationMs = (session.playbackDurationSec * 1000f).toLong().coerceAtLeast(0L)
                return Snapshot(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING,
                    standardActions = playbackStandardActions(session, playing = true),
                    customActionIds = listOf(CUSTOM_ACTION_STOP),
                )
            }
            session?.appMode == AppMode.MULTITRACK_RECORD -> {
                return Snapshot(
                    positionMs = 0L,
                    durationMs = 0L,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                    standardActions = android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY,
                    customActionIds = emptyList(),
                )
            }
            session?.appMode?.isPlaybackMode == true -> {
                val positionMs = (session.playbackPositionSec * 1000f).toLong().coerceAtLeast(0L)
                val durationMs = (session.playbackDurationSec * 1000f).toLong().coerceAtLeast(0L)
                return Snapshot(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                    standardActions = playbackStandardActions(session, playing = false),
                    customActionIds = listOf(CUSTOM_ACTION_STOP),
                )
            }
            else -> {
                return Snapshot(
                    positionMs = 0L,
                    durationMs = 0L,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_NONE,
                    standardActions = 0L,
                    customActionIds = emptyList(),
                )
            }
        }
    }

    fun formatTimestamp(positionSec: Float, durationSec: Float?): String {
        val pos = formatClock(positionSec)
        val total = durationSec?.takeIf { it > 0f }
        return if (total != null) "$pos / ${formatClock(total)}" else pos
    }

    fun formatClock(sec: Float): String {
        val total = sec.coerceAtLeast(0f).toInt()
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }

    private fun recordingActions(): Long =
        android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
            android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE

    private fun playbackStandardActions(session: MixerSessionUiState, playing: Boolean): Long {
        var actions = android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO
        actions = if (playing) {
            actions or recordingActions()
        } else {
            actions or android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE
        }
        if (session.appMode != AppMode.SIMPLE_PLAY && session.trackmarks.isNotEmpty()) {
            actions = actions or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }
        return actions
    }
}
