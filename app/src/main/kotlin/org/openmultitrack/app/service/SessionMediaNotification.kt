package org.openmultitrack.app.service

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.openmultitrack.app.R
import org.openmultitrack.domain.session.AppMode
import org.openmultitrack.domain.session.isPlaybackMode

/**
 * [MediaSessionCompat] backing the system media notification on Android 13+.
 */
class SessionMediaNotification(
    private val context: Context,
) {
    private val mediaSession = MediaSessionCompat(context, "OpenMultiTrack")
    private var lastPublishedPositionMs: Long = -1L
    private var lastPublishedDurationMs: Long? = null
    private var lastPublishedTimeSubtitle: String? = null
    private var lastSnapshot: SessionNotificationTransport.Snapshot? = null

    init {
        mediaSession.setCallback(
            object : MediaSessionCompat.Callback() {
                override fun onPlay() = dispatchPlaybackToggle()

                override fun onPause() {
                    val session = activeSession() ?: return
                    when {
                        session.isRecording -> dispatchPauseRecording()
                        session.appMode.isPlaybackMode -> dispatchPlaybackToggle()
                    }
                }

                override fun onStop() {
                    dispatchCustomStop()
                }

                override fun onSkipToNext() = dispatchNext()

                override fun onSkipToPrevious() = dispatchPrevious()

                override fun onSeekTo(pos: Long) {
                    val session = activeSession() ?: return
                    if (session.isRecording || !session.appMode.isPlaybackMode) return
                    if (session.playbackDurationSec <= 0f) return
                    controller()?.seekSoundcheck(pos / 1000f)
                    AudioSessionBridge.rebuildNotification()
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    when (action) {
                        SessionNotificationTransport.CUSTOM_ACTION_STOP -> dispatchCustomStop()
                        SessionNotificationTransport.CUSTOM_ACTION_PREVIOUS -> dispatchPrevious()
                        SessionNotificationTransport.CUSTOM_ACTION_NEXT -> dispatchNext()
                    }
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

    fun layoutSignature(session: MixerSessionUiState?, mixerName: String?): Int {
        val snap = SessionNotificationTransport.snapshot(session)
        val title = mixerName ?: context.getString(R.string.notification_title)
        return listOf(
            title,
            snap.state,
            snap.standardActions,
            snap.customActionIds,
            snap.showTime,
            session?.isRecording,
            session?.isPlaying,
            session?.selectedSoundcheckDir,
        ).hashCode()
    }

    fun applySession(session: MixerSessionUiState?, mixerName: String?) {
        lastPublishedPositionMs = -1L
        lastPublishedDurationMs = null
        lastPublishedTimeSubtitle = null
        lastSnapshot = null
        publish(session, mixerName, force = true)
    }

    fun updateProgress(session: MixerSessionUiState?) {
        if (session == null) return
        publish(session, session.mixerProfile?.displayName, force = false)
    }

    private fun publish(session: MixerSessionUiState?, mixerName: String?, force: Boolean) {
        val snap = SessionNotificationTransport.snapshot(session)
        val structureChanged = force ||
            lastSnapshot?.state != snap.state ||
            lastSnapshot?.standardActions != snap.standardActions ||
            lastSnapshot?.customActionIds != snap.customActionIds
        val positionChanged = snap.positionMs != lastPublishedPositionMs
        val durationChanged = snap.durationMs != lastPublishedDurationMs
        val timeSubtitle = timeSubtitle(session, snap)
        val timeChanged = timeSubtitle != lastPublishedTimeSubtitle

        if (structureChanged) {
            publishMetadata(session, mixerName, snap, timeSubtitle)
            publishPlaybackState(snap)
            lastSnapshot = snap
            lastPublishedPositionMs = snap.positionMs
            lastPublishedDurationMs = snap.durationMs
            lastPublishedTimeSubtitle = timeSubtitle
            return
        }

        if (!positionChanged && !durationChanged && !timeChanged) return

        if (durationChanged || timeChanged) {
            publishMetadata(session, mixerName, snap, timeSubtitle)
            lastPublishedDurationMs = snap.durationMs
            lastPublishedTimeSubtitle = timeSubtitle
        }
        if (positionChanged) {
            publishPlaybackState(snap)
            lastPublishedPositionMs = snap.positionMs
        }
    }

    private fun timeSubtitle(
        session: MixerSessionUiState?,
        snap: SessionNotificationTransport.Snapshot,
    ): String? {
        if (!snap.showTime || session == null) return null
        return when {
            session.isRecording ->
                SessionNotificationTransport.formatTimestamp(session.recordElapsedSec, null, unknownTotal = true)
            session.appMode.isPlaybackMode ->
                SessionNotificationTransport.formatTimestamp(
                    session.playbackPositionSec,
                    session.playbackDurationSec.takeIf { it > 0f },
                    unknownTotal = session.playbackDurationSec <= 0f,
                )
            else -> null
        }
    }

    private fun publishMetadata(
        session: MixerSessionUiState?,
        mixerName: String?,
        snap: SessionNotificationTransport.Snapshot,
        timeSubtitle: String?,
    ) {
        val title = mixerName ?: context.getString(R.string.notification_title)
        val modeLine = SessionNotificationBuilder.statusLine(session)
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, modeLine)
        if (timeSubtitle != null) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, timeSubtitle)
        }
        snap.durationMs?.let { metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it) }
        mediaSession.setMetadata(metadata.build())
    }

    private fun publishPlaybackState(snap: SessionNotificationTransport.Snapshot) {
        val speed = if (snap.state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f
        val builder = PlaybackStateCompat.Builder()
            .setActions(snap.standardActions)
            .setState(snap.state, snap.positionMs, speed, SystemClock.elapsedRealtime())
        snap.customActionIds.forEach { actionId ->
            when (actionId) {
                SessionNotificationTransport.CUSTOM_ACTION_STOP -> {
                    builder.addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            actionId,
                            context.getString(R.string.notification_action_stop),
                            R.drawable.ic_media_stop,
                        ).build(),
                    )
                }
                SessionNotificationTransport.CUSTOM_ACTION_PREVIOUS -> {
                    builder.addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            actionId,
                            context.getString(R.string.notification_action_previous),
                            android.R.drawable.ic_media_previous,
                        ).build(),
                    )
                }
                SessionNotificationTransport.CUSTOM_ACTION_NEXT -> {
                    builder.addCustomAction(
                        PlaybackStateCompat.CustomAction.Builder(
                            actionId,
                            context.getString(R.string.notification_action_next),
                            android.R.drawable.ic_media_next,
                        ).build(),
                    )
                }
            }
        }
        mediaSession.setPlaybackState(builder.build())
    }

    private fun dispatchCustomStop() {
        val session = activeSession() ?: return
        when {
            session.isRecording -> dispatchStopRecording()
            session.appMode.isPlaybackMode -> dispatchStopPlayback()
            else -> dispatchStopAll()
        }
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
        AudioSessionBridge.rebuildNotification()
    }

    private fun dispatchPauseRecording() {
        controller()?.pauseRecording()
        AudioSessionBridge.rebuildNotification()
    }

    private fun dispatchStopRecording() {
        controller()?.stopRecording()
        AudioSessionBridge.rebuildNotification()
    }

    private fun dispatchStopPlayback() {
        controller()?.stopSoundcheck()
        AudioSessionBridge.rebuildNotification()
    }

    private fun dispatchNext() {
        controller()?.seekToNextTrackmark()
        AudioSessionBridge.rebuildNotification()
    }

    private fun dispatchPrevious() {
        controller()?.seekToPreviousTrackmark()
        AudioSessionBridge.rebuildNotification()
    }

    private fun dispatchStopAll() {
        AudioSessionBridge.mixerManager?.shutdownAll()
        AudioSessionBridge.rebuildNotification()
    }
}
