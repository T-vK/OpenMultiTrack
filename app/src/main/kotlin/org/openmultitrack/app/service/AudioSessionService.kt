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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.StateFlow
import org.openmultitrack.app.MainActivity
import org.openmultitrack.app.R
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.app.data.AppSettingsStore
import org.openmultitrack.usb.UsbAudioEnumerator

class AudioSessionService : Service() {
    private val binder = LocalBinder()
    private lateinit var mixerManager: MultiMixerSessionManager

    inner class LocalBinder : Binder() {
        fun getService(): AudioSessionService = this@AudioSessionService

        fun getMixerManager(): MultiMixerSessionManager = mixerManager
    }

    override fun onCreate() {
        super.onCreate()
        val enumerator = UsbAudioEnumerator(applicationContext)
        val settings = AppSettingsStore(applicationContext)
        mixerManager = MultiMixerSessionManager(applicationContext, enumerator, settings)
        createNotificationChannel()
        OmtLog.i("Service", "AudioSessionService created")
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
            }
        }
        return START_STICKY
    }

    fun promoteToForeground(status: String? = null) {
        val notification = buildNotification(status ?: "Audio session active")
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
    }

    fun updateNotification(status: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioSessionService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_launcher, getString(R.string.notification_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "audio_session"
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
    private var pendingReady: ((MultiMixerSessionManager) -> Unit)? = null
    private var pendingForegroundStatus: String? = null

    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, binder: IBinder?) {
            val local = binder as AudioSessionService.LocalBinder
            service = local.getService()
            mixerManager = local.getMixerManager()
            OmtLog.d("ServiceClient", "bound to AudioSessionService")
            pendingReady?.invoke(local.getMixerManager())
            pendingReady = null
            pendingForegroundStatus?.let { status ->
                local.getService().promoteToForeground(status)
                pendingForegroundStatus = null
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            service = null
            mixerManager = null
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
        pendingReady = null
    }

    fun whenReady(block: (MultiMixerSessionManager) -> Unit) {
        val m = mixerManager
        if (m != null) block(m) else pendingReady = block
    }

    fun withManager(block: (MultiMixerSessionManager) -> Unit) {
        mixerManager?.let(block)
    }

    fun promoteForeground(status: String) {
        AudioSessionService.start(context, status)
        service?.let {
            it.promoteToForeground(status)
            it.updateNotification(status)
        } ?: run { pendingForegroundStatus = status }
    }
}
