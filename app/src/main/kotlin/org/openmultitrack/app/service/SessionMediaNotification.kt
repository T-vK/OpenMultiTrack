package org.openmultitrack.app.service

import android.content.Context
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.openmultitrack.app.R
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode
import kotlin.math.max

/**
 * [MediaSessionCompat] backing the foreground notification. Timers and seek position live here
 * only — notification [androidx.core.app.NotificationCompat] text must stay static so the system
 * media template does not re-animate on every tick.
 */
class SessionMediaNotification(
    private val context: Context,
) {
    private val mediaSession = MediaSessionCompat(context, "OpenMultiTrack")
    private var lastActions: Long = 0L
    private var lastPlaybackState: Int = PlaybackStateCompat.STATE_NONE

    init {
        mediaSession.setCallback(
            object : MediaSessionCompat.Callback() {
                override fun onPlay() = dispatchPlaybackToggle()

                override fun onPause() {
                    val session = activeSession() ?: return
                    when {
                        session.isRecording -> dispatchRecordToggle()
                        session.appMode.isPlaybackMode -> dispatchPlaybackToggle()
                    }
                }

                override fun onStop() {
                    val session = activeSession() ?: return
                    when {
                        session.isRecording -> dispatchRecordToggle()
                        session.appMode.isPlaybackMode -> dispatchStopPlayback()
                        else -> dispatchStopAll()
                    }
                }

                override fun onSkipToNext() = dispatchNext()

                override fun onSkipToPrevious() = dispatchPrevious()

                override fun onSeekTo(pos: Long) {
                    val session = activeSession() ?: return
                    if (session.isRecording || !session.appMode.isPlaybackMode) return
                    if (session.playbackDurationSec <= 0f) return
                    controller()?.seekSoundcheck(pos / 1000f)
                    AudioSessionBridge.refreshNotification()
                }
            },
        )
        mediaSession.isActive = true
    }

    fun sessionToken() = mediaSession.sessionToken

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
    }

    /** Stable key — when this changes, the posted [NotificationCompat] must be rebuilt. */
    fun layoutSignature(session: MixerSessionUiState?, mixerName: String?): Int {
        val title = mixerName ?: context.getString(R.string.notification_title)
        val mode = transportMode(session)
        val playbackSession = session?.selectedSoundcheckDir
        val playing = session?.isPlaying == true
        val recording = session?.isRecording == true
        val trackmarks = session?.trackmarks?.size ?: 0
        return listOf(title, mode, playbackSession, playing, recording, trackmarks).hashCode()
    }

    /** Metadata, actions, and initial playback state — call before posting or rebuilding notification. */
    fun applySession(session: MixerSessionUiState?, mixerName: String?) {
        val title = mixerName ?: context.getString(R.string.notification_title)
        val subtitle = SessionNotificationBuilder.statusLine(session)
        val (positionMs, durationMs, actions, state) = transportState(session)

        lastActions = actions
        lastPlaybackState = state

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
        if (durationMs > 0L) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        }
        mediaSession.setMetadata(metadata.build())
        publishPlaybackState(state, positionMs)
    }

    /** Position/duration tick — MediaSession only; do not re-post the notification. */
    fun updateProgress(session: MixerSessionUiState?) {
        if (session == null) return
        val snap = transportState(session)
        if (snap.state != lastPlaybackState) {
            applySession(session, session.mixerProfile?.displayName)
            return
        }
        if (session.isRecording) {
            refreshRecordingDuration(snap.durationMs)
        }
        publishPlaybackState(snap.state, snap.positionMs)
    }

    private fun refreshRecordingDuration(durationMs: Long) {
        val current = mediaSession.controller.metadata ?: return
        if (current.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) == durationMs) return
        val metadata = MediaMetadataCompat.Builder(current)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun publishPlaybackState(state: Int, positionMs: Long) {
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(lastActions)
                .setState(state, positionMs, 1f, SystemClock.elapsedRealtime())
                .build(),
        )
    }

    private fun transportMode(session: MixerSessionUiState?): String = when {
        session?.isRecording == true -> "recording"
        session?.isPlaying == true -> "playing"
        session?.isMonitoring == true -> "monitoring"
        session?.appMode == AppMode.MULTITRACK_RECORD -> "ready_record"
        session?.appMode?.isPlaybackMode == true -> "soundcheck"
        else -> "idle"
    }

    private fun transportState(session: MixerSessionUiState?): TransportSnapshot {
        when {
            session?.isRecording == true -> {
                val elapsedMs = (session.recordElapsedSec * 1000f).toLong().coerceAtLeast(0L)
                // Keep the playhead at the end of the bar; duration grows with elapsed time.
                val durationMs = max(elapsedMs, 1000L)
                return TransportSnapshot(
                    positionMs = durationMs,
                    durationMs = durationMs,
                    actions = PlaybackStateCompat.ACTION_STOP,
                    state = PlaybackStateCompat.STATE_PLAYING,
                )
            }
            session?.isPlaying == true -> {
                val positionMs = (session.playbackPositionSec * 1000f).toLong().coerceAtLeast(0L)
                val durationMs = (session.playbackDurationSec * 1000f).toLong().coerceAtLeast(0L)
                return TransportSnapshot(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    actions = playbackActions(session, includePause = true),
                    state = PlaybackStateCompat.STATE_PLAYING,
                )
            }
            session?.appMode == AppMode.MULTITRACK_RECORD -> {
                return TransportSnapshot(
                    positionMs = 0L,
                    durationMs = 0L,
                    actions = PlaybackStateCompat.ACTION_PLAY,
                    state = PlaybackStateCompat.STATE_PAUSED,
                )
            }
            session?.appMode?.isPlaybackMode == true -> {
                val positionMs = (session.playbackPositionSec * 1000f).toLong().coerceAtLeast(0L)
                val durationMs = (session.playbackDurationSec * 1000f).toLong().coerceAtLeast(0L)
                return TransportSnapshot(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    actions = playbackActions(session, includePause = false),
                    state = PlaybackStateCompat.STATE_PAUSED,
                )
            }
            else -> {
                return TransportSnapshot(
                    positionMs = 0L,
                    durationMs = 0L,
                    actions = 0L,
                    state = PlaybackStateCompat.STATE_NONE,
                )
            }
        }
    }

    private fun playbackActions(session: MixerSessionUiState, includePause: Boolean): Long {
        var actions = PlaybackStateCompat.ACTION_SEEK_TO or PlaybackStateCompat.ACTION_STOP
        actions = if (includePause) {
            actions or PlaybackStateCompat.ACTION_PAUSE
        } else {
            actions or PlaybackStateCompat.ACTION_PLAY
        }
        if (session.appMode != AppMode.SIMPLE_PLAY && session.trackmarks.isNotEmpty()) {
            actions = actions or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        }
        return actions
    }

    private fun activeSession(): MixerSessionUiState? {
        val manager = AudioSessionBridge.mixerManager ?: return null
        val mixerId = AudioSessionBridge.activeMixerId() ?: manager.activeMixerId.value ?: return null
        return manager.getOrCreate(mixerId).state.value
    }

    private fun controller(): MixerSessionController? {
        val manager = AudioSessionBridge.mixerManager ?: return null
        val mixerId = AudioSessionBridge.activeMixerId() ?: manager.activeMixerId.value ?: return null
        return manager.getOrCreate(mixerId)
    }

    private fun dispatchPlaybackToggle() {
        controller()?.toggleSoundcheckPlayback()
        AudioSessionBridge.refreshNotification()
    }

    private fun dispatchRecordToggle() {
        val ctrl = controller() ?: return
        val session = ctrl.state.value
        if (session.isRecording) ctrl.stopRecording() else ctrl.startRecording()
        AudioSessionBridge.refreshNotification()
    }

    private fun dispatchStopPlayback() {
        controller()?.stopSoundcheck()
        AudioSessionBridge.refreshNotification()
    }

    private fun dispatchNext() {
        controller()?.seekToNextTrackmark()
        AudioSessionBridge.refreshNotification()
    }

    private fun dispatchPrevious() {
        controller()?.seekToPreviousTrackmark()
        AudioSessionBridge.refreshNotification()
    }

    private fun dispatchStopAll() {
        AudioSessionBridge.mixerManager?.shutdownAll()
        AudioSessionBridge.refreshNotification()
    }

    private data class TransportSnapshot(
        val positionMs: Long,
        val durationMs: Long,
        val actions: Long,
        val state: Int,
    )
}
