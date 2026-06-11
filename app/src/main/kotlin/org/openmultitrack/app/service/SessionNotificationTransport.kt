package org.openmultitrack.app.service

import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode

/**
 * Maps app session state to [android.support.v4.media.session.PlaybackStateCompat] fields.
 *
 * On Android 13+ the system media notification ignores [androidx.core.app.NotificationCompat]
 * actions and derives buttons from playback state. Standard ACTION_STOP is not shown in any
 * slot — use [CUSTOM_ACTION_STOP] instead. When chapter prev/next are needed, they are also
 * exposed as custom actions so stop stays in the compact three-button row (slot 3).
 */
object SessionNotificationTransport {
    const val CUSTOM_ACTION_STOP = "org.openmultitrack.action.STOP"
    const val CUSTOM_ACTION_PREVIOUS = "org.openmultitrack.action.PREVIOUS"
    const val CUSTOM_ACTION_NEXT = "org.openmultitrack.action.NEXT"

    data class Snapshot(
        val positionMs: Long,
        val durationMs: Long?,
        val state: Int,
        val standardActions: Long,
        val customActionIds: List<String>,
        val showTime: Boolean,
    )

    fun snapshot(session: MixerSessionUiState?): Snapshot {
        when {
            session?.isRecording == true -> {
                val elapsedMs = (session.recordElapsedSec * 1000f).toLong().coerceAtLeast(0L)
                return Snapshot(
                    positionMs = elapsedMs,
                    durationMs = null,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING,
                    standardActions = pauseActions(),
                    customActionIds = listOf(CUSTOM_ACTION_STOP),
                    showTime = true,
                )
            }
            session?.isPlaying == true -> {
                val positionMs = (session.playbackPositionSec * 1000f).toLong().coerceAtLeast(0L)
                val durationMs = playbackDurationMs(session)
                return Snapshot(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING,
                    standardActions = playbackStandardActions(playing = true),
                    customActionIds = playbackCustomActions(session),
                    showTime = true,
                )
            }
            session?.appMode == AppMode.MULTITRACK_RECORD -> {
                return Snapshot(
                    positionMs = 0L,
                    durationMs = null,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                    standardActions = android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY,
                    customActionIds = emptyList(),
                    showTime = false,
                )
            }
            session?.appMode?.isPlaybackMode == true -> {
                val positionMs = (session.playbackPositionSec * 1000f).toLong().coerceAtLeast(0L)
                val durationMs = playbackDurationMs(session)
                return Snapshot(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                    standardActions = playbackStandardActions(playing = false),
                    customActionIds = playbackCustomActions(session),
                    showTime = true,
                )
            }
            else -> {
                return Snapshot(
                    positionMs = 0L,
                    durationMs = null,
                    state = android.support.v4.media.session.PlaybackStateCompat.STATE_NONE,
                    standardActions = 0L,
                    customActionIds = emptyList(),
                    showTime = false,
                )
            }
        }
    }

    fun formatTimestamp(positionSec: Float, durationSec: Float?, unknownTotal: Boolean = false): String {
        val pos = formatClock(positionSec)
        val total = durationSec?.takeIf { it > 0f }
        return when {
            total != null -> "$pos / ${formatClock(total)}"
            unknownTotal -> "$pos / ${UNKNOWN_TOTAL}"
            else -> pos
        }
    }

    fun formatClock(sec: Float): String {
        val total = sec.coerceAtLeast(0f).toInt()
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }

    private const val UNKNOWN_TOTAL = "--:--"

    private fun playbackDurationMs(session: MixerSessionUiState): Long? =
        (session.playbackDurationSec * 1000f).toLong().takeIf { it > 0L }

    private fun pauseActions(): Long =
        android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
            android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE

    private fun playbackStandardActions(playing: Boolean): Long {
        var actions = android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO
        actions = if (playing) {
            actions or pauseActions()
        } else {
            actions or android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE
        }
        return actions
    }

    /**
     * Chapter transport uses custom prev/next so [CUSTOM_ACTION_STOP] stays in compact view
     * (slots 1 pause, 2 previous, 3 stop on Android 13+).
     */
    private fun playbackCustomActions(session: MixerSessionUiState): List<String> {
        val showChapters = session.appMode != AppMode.SIMPLE_PLAY && session.trackmarks.isNotEmpty()
        return buildList {
            if (showChapters) add(CUSTOM_ACTION_PREVIOUS)
            add(CUSTOM_ACTION_STOP)
            if (showChapters) add(CUSTOM_ACTION_NEXT)
        }
    }
}
