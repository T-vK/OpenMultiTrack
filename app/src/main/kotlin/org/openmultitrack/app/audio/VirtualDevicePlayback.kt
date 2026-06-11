package org.openmultitrack.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.openmultitrack.usb.AudioBackend
import org.openmultitrack.usb.PlaybackRoute

/** Oboe playback to the device speaker/headphones (demo mixer / no USB returns). */
object VirtualDevicePlayback {
    fun resolveRoute(context: Context, channelCount: Int, sampleRateHz: Int = 48_000): PlaybackRoute? {
        val deviceId = defaultOutputDeviceId(context) ?: return null
        return PlaybackRoute(
            backend = AudioBackend.OBOE,
            oboeDeviceId = deviceId,
            channelCount = channelCount.coerceIn(1, 32),
            sampleRate = sampleRateHz,
        )
    }

    private fun defaultOutputDeviceId(context: Context): Int? {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferred = listOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )
        for (type in preferred) {
            outputs.firstOrNull { it.type == type }?.let { return it.id }
        }
        return outputs.firstOrNull()?.id
    }
}
