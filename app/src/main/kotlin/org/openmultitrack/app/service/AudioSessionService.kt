package org.openmultitrack.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import org.openmultitrack.app.MainActivity
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
    private lateinit var mediaNotification: SessionMediaNotification
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastNotificationSignature: Int? = null

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
        AudioSessionBridge.tickMediaProgress = { session ->
            if (isInForeground) mediaNotification.updateProgress(session)
        }
        mediaNotification = SessionMediaNotification(this)
        createNotificationChannel()
        OmtLog.i("Service", "AudioSessionService created")
    }

    override fun onDestroy() {
        notificationScope.cancel()
        if (::mediaNotification.isInitialized) {
            mediaNotification.release()
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
                        // START_STICKY restart — stay bound without promoting from background.
                        OmtLog.d("Service", "onStartCommand without status — skip foreground promote")
                    }
            }
        }
        return START_STICKY
    }

    fun promoteToForeground(status: String? = null): Boolean {
        return try {
            if (!isInForeground) {
                val notification = buildAndPostNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    } else {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                    startForeground(NOTIFICATION_ID, notification, type)
                } else {
                    @Suppress("DEPRECATION")
                    startForeground(NOTIFICATION_ID, notification)
                }
                isInForeground = true
            } else {
                refreshForegroundNotification(forceRebuild = true)
            }
            true
        } catch (e: Exception) {
            OmtLog.e("Service", "startForeground failed", e)
            false
        }
    }

    fun updateNotification(status: String) {
        refreshForegroundNotification(forceRebuild = true)
    }

    fun refreshForegroundNotification(forceRebuild: Boolean = false) {
        if (!isInForeground) return
        val session = activeSession()
        val mixerName = session?.mixerProfile?.displayName
        val signature = mediaNotification.layoutSignature(session, mixerName)
        if (forceRebuild || signature != lastNotificationSignature) {
            buildAndPostNotification()
        } else {
            mediaNotification.updateProgress(session)
        }
    }

    private fun activeSession(): MixerSessionUiState? {
        val activeId = mixerManager.activeMixerId.value ?: return null
        return mixerManager.getOrCreate(activeId).state.value
    }

    private fun buildAndPostNotification(): Notification {
        val session = activeSession()
        val mixerName = session?.mixerProfile?.displayName
        mediaNotification.applySession(session, mixerName)
        lastNotificationSignature = mediaNotification.layoutSignature(session, mixerName)
        val notification = SessionNotificationBuilder
            .build(this, session, mixerName, mediaNotification)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
        return notification
    }

    fun stopForegroundAndSelf() {
        lastNotificationSignature = null
        isInForeground = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "audio_session_transport"
        const val NOTIFICATION_ID = 1001
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
