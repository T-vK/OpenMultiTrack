package org.openmultitrack.app.audio

import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.domain.mixer.MixerRoutingConfig
import org.openmultitrack.domain.session.AppMode

/** Describes how multitrack session samples are mixed before USB playback. */
data class PlaybackMixContext(
    val appMode: AppMode,
    val sessionChannelCount: Int,
    val usbOutputCount: Int,
    val routing: MixerRoutingConfig,
    val strips: List<ChannelStripState>,
) {
    fun mixInterleaved(
        input: FloatArray,
        frameCount: Int,
        inputChannels: Int,
        output: FloatArray,
    ) {
        val outCh = usbOutputCount.coerceAtLeast(1)
        val anySolo = strips.any { it.solo }
        for (f in 0 until frameCount) {
            for (out in 0 until outCh) {
                output[f * outCh + out] = 0f
            }
            when (appMode) {
                AppMode.SIMPLE_PLAY -> {
                    var sum = 0f
                    var count = 0
                    for (ch in 0 until inputChannels.coerceAtMost(sessionChannelCount)) {
                        val strip = strips.getOrNull(ch) ?: continue
                        if (strip.muted) continue
                        if (anySolo && !strip.solo) continue
                        sum += input[f * inputChannels + ch]
                        count++
                    }
                    val scaled = if (count > 0) sum / count else 0f
                    output[f * outCh + 0] = scaled
                    if (outCh > 1) output[f * outCh + 1] = scaled
                }
                AppMode.VIRTUAL_SOUNDCHECK -> {
                    for (ch in 0 until inputChannels.coerceAtMost(sessionChannelCount)) {
                        val strip = strips.getOrNull(ch) ?: continue
                        if (strip.muted) continue
                        if (anySolo && !strip.solo) continue
                        val outUsb = routing.outputTarget(ch)
                        if (outUsb >= outCh) continue
                        output[f * outCh + outUsb] += input[f * inputChannels + ch]
                    }
                }
                else -> Unit
            }
        }
    }
}
