package org.openmultitrack.app.service

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.openmultitrack.app.R
import org.openmultitrack.domain.session.isPlaybackMode

/** [MediaSessionCompat] for the playback / soundcheck player notification only. */
class PlaybackMediaNotification(
    private val context: Context,
) {
    private val mediaSession = MediaSessionCompat(context, "OpenMultiTrackPlayback")
    private var lastPublishedPositionMs: Long = -1L
    private var lastPublishedDurationMs: Long = -1L
    private var lastPublishedProgressLine: String? = null
    private var lastSnapshot: PlaybackNotificationTransport.Snapshot? = null
    private var albumArt: Bitmap? = null

    init {
        mediaSession.setCallback(
            object : MediaSessionCompat.Callback() {
                override fun onPlay() = dispatchPlaybackToggle()

                override fun onPause() {
                    val session = activeSession() ?: return
                    if (session.appMode.isPlaybackMode) dispatchPlaybackToggle()
                }

                override fun onStop() {
                    dispatchCustomStop()
                }

                override fun onSkipToNext() = dispatchNext()

                override fun onSkipToPrevious() = dispatchPrevious()

                override fun onSeekTo(pos: Long) {
                    val session = activeSession() ?: return
                    if (!session.appMode.isPlaybackMode || session.playbackDurationSec <= 0f) return
                    controller()?.seekSoundcheck(pos / 1000f)
                    AudioSessionBridge.rebuildNotification()
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    if (action == PlaybackNotificationTransport.CUSTOM_ACTION_STOP) {
                        dispatchCustomStop()
                    }
                }
            },
        )
        mediaSession.isActive = false
    }

    fun sessionToken() = mediaSession.sessionToken

    fun setActive(active: Boolean) {
        mediaSession.isActive = active
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
        albumArt = null
        PlaybackNotificationArt.invalidateCache()
    }

    fun layoutSignature(session: MixerSessionUiState?, mixerName: String?): Int {
        val snap = PlaybackNotificationTransport.snapshot(session)
        val display = PlaybackNotificationContent.resolve(context, session, mixerName)
        val chapters = session?.trackmarks?.isNotEmpty() == true
        val positionBucket = (snap.positionMs / 1_000L).toInt()
        return listOf(
            display.sessionTitle,
            display.totalTimeLine,
            snap.state,
            snap.standardActions,
            snap.customActionIds,
            chapters,
            session?.isPlaying,
            session?.selectedSoundcheckDir,
            positionBucket,
        ).hashCode()
    }

    fun applySession(session: MixerSessionUiState?, mixerName: String?) {
        lastPublishedPositionMs = -1L
        lastPublishedDurationMs = -1L
        lastPublishedProgressLine = null
        lastSnapshot = null
        publish(session, mixerName, force = true)
    }

    fun updateProgress(session: MixerSessionUiState?) {
        if (session == null || !mediaSession.isActive) return
        publish(session, session.mixerProfile?.displayName, force = false)
    }

    private fun publish(session: MixerSessionUiState?, mixerName: String?, force: Boolean) {
        val snap = PlaybackNotificationTransport.snapshot(session)
        val display = PlaybackNotificationContent.resolve(context, session, mixerName)
        val structureChanged = force ||
            lastSnapshot?.state != snap.state ||
            lastSnapshot?.standardActions != snap.standardActions ||
            lastSnapshot?.customActionIds != snap.customActionIds
        val positionChanged = snap.positionMs != lastPublishedPositionMs
        val durationChanged = snap.durationMs != lastPublishedDurationMs && snap.durationMs > 0L
        val progressChanged = display.progressLine != lastPublishedProgressLine

        if (structureChanged) {
            publishMetadata(session, mixerName, snap.durationMs, display)
            publishPlaybackState(snap)
            lastSnapshot = snap
            lastPublishedPositionMs = snap.positionMs
            lastPublishedDurationMs = snap.durationMs
            lastPublishedProgressLine = display.progressLine
            return
        }

        if (!positionChanged && !durationChanged && !progressChanged) return

        if (durationChanged || progressChanged) {
            publishMetadata(session, mixerName, snap.durationMs, display)
            lastPublishedDurationMs = snap.durationMs
            lastPublishedProgressLine = display.progressLine
        }
        if (positionChanged) {
            publishPlaybackState(snap)
            lastPublishedPositionMs = snap.positionMs
        }
    }

    private fun publishMetadata(
        session: MixerSessionUiState?,
        mixerName: String?,
        durationMs: Long,
        display: PlaybackNotificationContent.Display,
    ) {
        val art = albumArt ?: PlaybackNotificationArt.albumArt(context).also { albumArt = it }
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, display.sessionTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, display.sessionTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, display.mixerLine)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
        display.totalTimeLine?.let {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it)
        }
        display.progressLine?.let {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, it)
        }
        if (durationMs > 0L) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        }
        mediaSession.setMetadata(metadata.build())
    }

    private fun publishPlaybackState(snap: PlaybackNotificationTransport.Snapshot) {
        val speed = if (snap.state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f
        val builder = PlaybackStateCompat.Builder()
            .setActions(snap.standardActions)
            .setState(snap.state, snap.positionMs, speed, SystemClock.elapsedRealtime())
        snap.customActionIds.forEach { actionId ->
            if (actionId == PlaybackNotificationTransport.CUSTOM_ACTION_STOP) {
                builder.addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                        actionId,
                        context.getString(R.string.notification_action_stop),
                        R.drawable.ic_media_stop,
                    ).build(),
                )
            }
        }
        mediaSession.setPlaybackState(builder.build())
    }

    private fun dispatchCustomStop() {
        val session = activeSession() ?: return
        if (session.appMode.isPlaybackMode) {
            dispatchStopPlayback()
        } else {
            dispatchStopAll()
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

    private fun dispatchStopPlayback() {
        controller()?.stopSoundcheck()
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
