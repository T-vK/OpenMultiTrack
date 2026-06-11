package org.openmultitrack.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openmultitrack.app.R
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.app.data.MixerDeviceStore
import org.openmultitrack.app.data.MixerRoutingStore
import org.openmultitrack.app.remote.RemoteControlManager
import org.openmultitrack.usb.UsbAudioEnumerator

class AudioSessionService : Service() {
    private val binder = LocalBinder()
    private lateinit var mixerManager: MultiMixerSessionManager
    private lateinit var remoteControl: RemoteControlManager
    private lateinit var playbackMedia: PlaybackMediaNotification
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastPlaybackSignature: Int? = null
    private var activeNotificationMode: SessionNotificationMode? = null
    private var mediaProgressJobActive = false

    @Volatile
    var isInForeground: Boolean = false
        private set

    inner class LocalBinder : Binder() {
        fun getService(): AudioSessionService = this@AudioSessionService

        fun getMixerManager(): MultiMixerSessionManager = mixerManager

        fun getRemoteControl(): RemoteControlManager = remoteControl
    }

    override fun onCreate() {
        super.onCreate()
        val enumerator = UsbAudioEnumerator(applicationContext)
        val settings = AppSettingsStore(applicationContext)
        val mixerStore = MixerDeviceStore(applicationContext)
        val routingStore = MixerRoutingStore(applicationContext)
        mixerManager = MultiMixerSessionManager(applicationContext, enumerator, settings)
        remoteControl = RemoteControlManager(
            appContext = applicationContext,
            settings = settings,
            mixerStore = mixerStore,
            routingStore = routingStore,
            getManager = { mixerManager },
            promoteForeground = { status -> promoteToForeground(status) },
        )
        remoteControl.applyRole(settings.remoteRole)
        AudioSessionBridge.mixerManager = mixerManager
        AudioSessionBridge.activeMixerId = { mixerManager.activeMixerId.value }
        AudioSessionBridge.rebuildNotification = { refreshForegroundNotification(forceRebuild = true) }
        AudioSessionBridge.refreshPlaybackNotification = { session ->
            refreshPlaybackNotificationOnly(session)
        }
        AudioSessionBridge.tickMediaProgress = { session ->
            if (isInForeground && notificationMode(session) == SessionNotificationMode.PLAYBACK) {
                playbackMedia.updateProgress(session)
            }
        }
        playbackMedia = PlaybackMediaNotification(this)
        startPlaybackProgressTicker()
        createNotificationChannels()
        OmtLog.i("Service", "AudioSessionService created")
    }

    override fun onDestroy() {
        notificationScope.cancel()
        if (::playbackMedia.isInitialized) {
            playbackMedia.release()
        }
        isInForeground = false
        AudioSessionBridge.mixerManager = null
        remoteControl.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                mixerManager.shutdownAll()
                stopForegroundAndSelf()
            }
            else -> {
                intent?.getStringExtra(EXTRA_STATUS)?.let { promoteToForeground(it) }
                    ?: run {
                        OmtLog.d("Service", "onStartCommand without status — skip foreground promote")
                    }
            }
        }
        return START_STICKY
    }

    fun promoteToForeground(status: String? = null): Boolean {
        return try {
            syncNotifications(forceRebuild = true, statusHint = status, attachForeground = true)
        } catch (e: Exception) {
            OmtLog.e("Service", "startForeground failed", e)
            false
        }
    }

    fun updateNotification(status: String) {
        refreshForegroundNotification(forceRebuild = true)
    }

    fun refreshForegroundNotification(forceRebuild: Boolean = false) {
        syncNotifications(
            forceRebuild = forceRebuild,
            statusHint = null,
            attachForeground = isInForeground,
        )
    }

    /** Updates only the playback media notification — used on stop/seek/end without full sync cost. */
    private fun refreshPlaybackNotificationOnly(session: MixerSessionUiState?) {
        if (!isInForeground || session == null) return
        if (SessionNotificationPolicy.mode(session) != SessionNotificationMode.PLAYBACK) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val mixerName = session.mixerProfile?.displayName
        playbackMedia.applySession(session, mixerName)
        lastPlaybackSignature = playbackMedia.layoutSignature(session, mixerName)
        val notification = PlaybackNotificationBuilder
            .build(this, session, mixerName, playbackMedia)
            .build()
        manager.notify(RecordingNotificationIds.PLAYBACK_ID, notification)
    }

    private fun syncNotifications(
        forceRebuild: Boolean,
        statusHint: String?,
        attachForeground: Boolean,
    ): Boolean {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val recordings = recordingSessions(statusHint)
        val activeSession = activeSession()
        val wantsPlayback = SessionNotificationPolicy.mode(activeSession, statusHint) ==
            SessionNotificationMode.PLAYBACK

        if (recordings.isEmpty() && !wantsPlayback) {
            if (isInForeground || activeNotificationMode != null) {
                dismissAllNotifications(manager)
            }
            return false
        }

        cancelStaleRecordingNotifications(manager, recordings.map { it.first }.toSet())
        val recordingForeground = postRecordingNotifications(recordings)

        if (wantsPlayback && activeSession != null) {
            val playbackNotification = postPlaybackNotification(manager, activeSession, forceRebuild)
            if (recordingForeground == null && attachForeground) {
                attachForeground(RecordingNotificationIds.PLAYBACK_ID, playbackNotification)
                activeNotificationMode = SessionNotificationMode.PLAYBACK
            } else {
                if (recordingForeground != null && attachForeground) {
                    attachForeground(recordingForeground.first, recordingForeground.second)
                    activeNotificationMode = SessionNotificationMode.RECORDING
                }
            }
            return true
        }

        if (recordingForeground != null && attachForeground) {
            playbackMedia.setActive(false)
            manager.cancel(RecordingNotificationIds.PLAYBACK_ID)
            lastPlaybackSignature = null
            attachForeground(recordingForeground.first, recordingForeground.second)
            activeNotificationMode = SessionNotificationMode.RECORDING
            return true
        }

        return recordings.isNotEmpty() || wantsPlayback
    }

    private fun recordingSessions(
        statusHint: String? = null,
    ): List<Pair<String, MixerSessionUiState>> {
        val fromState = mixerManager.mixerIds().mapNotNull { id ->
            val state = mixerManager.getOrCreate(id).state.value
            if (state.isRecording || state.isMonitoring) id to state else null
        }
        if (fromState.isNotEmpty()) return fromState
        if (SessionNotificationPolicy.mode(activeSession(), statusHint) != SessionNotificationMode.RECORDING) {
            return emptyList()
        }
        val activeId = mixerManager.activeMixerId.value ?: return emptyList()
        val session = mixerManager.getOrCreate(activeId).state.value
        return listOf(activeId to session)
    }

    private fun cancelStaleRecordingNotifications(
        manager: NotificationManager,
        activeMixerIds: Set<String>,
    ) {
        RecordingNotificationIds.trackedMixerIds()
            .filter { it !in activeMixerIds }
            .forEach { mixerId ->
                RecordingNotificationIds.cancelFor(manager, mixerId)
            }
    }

    private fun postRecordingNotifications(
        recordings: List<Pair<String, MixerSessionUiState>>,
    ): Pair<Int, Notification>? {
        if (recordings.isEmpty()) return null
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeMixerId = mixerManager.activeMixerId.value
        var foreground: Pair<Int, Notification>? = null
        for ((mixerId, session) in recordings) {
            val notificationId = RecordingNotificationIds.idFor(mixerId)
            val mixerName = session.mixerProfile?.displayName
            val notification = RecordingNotificationBuilder
                .build(this, mixerId, session, mixerName)
                .build()
            manager.notify(notificationId, notification)
            if (foreground == null || mixerId == activeMixerId) {
                foreground = notificationId to notification
            }
        }
        return foreground
    }

    private fun postPlaybackNotification(
        manager: NotificationManager,
        session: MixerSessionUiState,
        forceRebuild: Boolean,
    ): Notification {
        playbackMedia.setActive(true)
        val mixerName = session.mixerProfile?.displayName
        val signature = playbackMedia.layoutSignature(session, mixerName)
        if (forceRebuild || signature != lastPlaybackSignature) {
            playbackMedia.applySession(session, mixerName)
            lastPlaybackSignature = signature
        } else {
            playbackMedia.updateProgress(session)
        }
        val notification = PlaybackNotificationBuilder
            .build(this, session, mixerName, playbackMedia)
            .build()
        manager.notify(RecordingNotificationIds.PLAYBACK_ID, notification)
        return notification
    }

    private fun notificationMode(session: MixerSessionUiState?): SessionNotificationMode =
        SessionNotificationPolicy.mode(session)

    private fun attachForeground(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, foregroundServiceType())
        } else {
            @Suppress("DEPRECATION")
            startForeground(notificationId, notification)
        }
        isInForeground = true
    }

    private fun dismissAllNotifications(manager: NotificationManager) {
        playbackMedia.setActive(false)
        RecordingNotificationIds.allIds().forEach { manager.cancel(it) }
        RecordingNotificationIds.trackedMixerIds().toList().forEach(RecordingNotificationIds::release)
        manager.cancel(RecordingNotificationIds.PLAYBACK_ID)
        lastPlaybackSignature = null
        activeNotificationMode = null
        if (!isInForeground) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isInForeground = false
    }

    private fun activeSession(): MixerSessionUiState? {
        val activeId = mixerManager.activeMixerId.value ?: return null
        return mixerManager.getOrCreate(activeId).state.value
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }

    private fun startPlaybackProgressTicker() {
        if (mediaProgressJobActive) return
        mediaProgressJobActive = true
        notificationScope.launch {
            while (isActive) {
                delay(1_000)
                if (!isInForeground) continue
                val session = activeSession() ?: continue
                if (notificationMode(session) == SessionNotificationMode.PLAYBACK && session.isPlaying) {
                    playbackMedia.updateProgress(session)
                }
            }
        }
    }

    fun stopForegroundAndSelf() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        dismissAllNotifications(manager)
        stopSelf()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val recordingColor = getColor(R.color.notification_recording)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_RECORDING,
                    getString(R.string.notification_channel_recording),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    setShowBadge(false)
                    enableLights(true)
                    lightColor = recordingColor
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PLAYBACK,
                    getString(R.string.notification_channel_playback),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { setShowBadge(false) },
            )
        }
    }

    companion object {
        const val CHANNEL_RECORDING = "recording_transport"
        const val CHANNEL_PLAYBACK = "playback_transport"
        const val ACTION_STOP_ALL = "org.openmultitrack.STOP_AUDIO_SESSION"
        const val EXTRA_STATUS = "status"

        fun start(context: Context, status: String) {
            val intent = Intent(context, AudioSessionService::class.java)
                .putExtra(EXTRA_STATUS, status)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

/** Binds to [AudioSessionService] and exposes [MultiMixerSessionManager]. */
class AudioSessionClient(private val context: Context) {
    private var service: AudioSessionService? = null
    private var mixerManager: MultiMixerSessionManager? = null
    private var remoteControl: RemoteControlManager? = null
    private var pendingReady: ((MultiMixerSessionManager) -> Unit)? = null
    private var onManagerReady: ((MultiMixerSessionManager) -> Unit)? = null
    private var onManagerLost: (() -> Unit)? = null
    private var pendingForegroundStatus: String? = null

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, binder: IBinder?) {
            val local = binder as AudioSessionService.LocalBinder
            service = local.getService()
            mixerManager = local.getMixerManager()
            remoteControl = local.getRemoteControl()
            OmtLog.d("ServiceClient", "bound to AudioSessionService")
            val manager = local.getMixerManager()
            pendingReady?.invoke(manager)
            pendingReady = null
            onManagerReady?.invoke(manager)
            pendingForegroundStatus?.let { status ->
                local.getService().promoteToForeground(status)
                pendingForegroundStatus = null
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            OmtLog.w("ServiceClient", "AudioSessionService disconnected")
            service = null
            mixerManager = null
            remoteControl = null
            onManagerLost?.invoke()
        }
    }

    fun bind() {
        val intent = Intent(context, AudioSessionService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        runCatching { context.unbindService(connection) }
        service = null
        mixerManager = null
        remoteControl = null
        pendingReady = null
    }

    fun whenReady(block: (MultiMixerSessionManager) -> Unit) {
        onManagerReady = block
        val m = mixerManager
        if (m != null) block(m) else pendingReady = block
    }

    fun onManagerLost(block: () -> Unit) {
        onManagerLost = block
    }

    fun withManager(block: (MultiMixerSessionManager) -> Unit) {
        mixerManager?.let(block)
    }

    fun withRemoteControl(block: (RemoteControlManager) -> Unit) {
        remoteControl?.let(block)
    }

    fun getRemoteControl(): RemoteControlManager? = remoteControl

    fun isForeground(): Boolean = service?.isInForeground == true

    fun promoteForeground(status: String): Boolean {
        val service = service
        if (service?.isInForeground == true) {
            service.updateNotification(status)
            return true
        }
        AudioSessionService.start(context, status)
        return if (service != null) {
            service.promoteToForeground(status)
        } else {
            pendingForegroundStatus = status
            true
        }
    }
}
