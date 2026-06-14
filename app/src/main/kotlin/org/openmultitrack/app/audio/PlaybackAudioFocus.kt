package org.openmultitrack.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import org.openmultitrack.audio.OmtLog

/** Routes hardware volume keys to media volume while Oboe/Android playback is active. */
object PlaybackAudioFocus {
    @Volatile
    private var held = false

    private var focusRequest: AudioFocusRequest? = null

    fun request(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { /* keep playing; user owns transport */ }
                .build()
            focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { /* no-op */ },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
            held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        OmtLog.i("PlaybackAudioFocus", "request → held=$held")
        return held
    }

    fun abandon(context: Context) {
        if (!held) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
        held = false
        OmtLog.i("PlaybackAudioFocus", "abandoned")
    }
}
