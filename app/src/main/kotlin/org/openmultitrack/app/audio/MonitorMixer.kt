package org.openmultitrack.app.audio

import kotlin.math.min

/**
 * Hot-swappable monitor routing: per-channel monitor flags, solo, and Android-side gain.
 */
data class MonitorMixConfig(
    val enabled: Boolean = false,
    val channelMonitoring: Set<Int> = emptySet(),
    val soloChannel: Int? = null,
    val gainLinear: Float = 2.5f,
    val outputDeviceId: Int = -1,
)

object MonitorMixer {
    fun effectiveMonitorChannels(config: MonitorMixConfig): Set<Int> {
        if (!config.enabled) return emptySet()
        config.soloChannel?.let { return setOf(it) }
        return config.channelMonitoring
    }

    fun mixToStereo(
        interleaved: FloatArray,
        frames: Int,
        sourceChannelCount: Int,
        config: MonitorMixConfig,
        dest: FloatArray,
    ): Int {
        if (frames <= 0 || !config.enabled) return 0
        val channels = effectiveMonitorChannels(config)
        if (channels.isEmpty()) return 0
        val ordered = channels.sorted()
        val gain = config.gainLinear.coerceIn(0f, 8f)

        for (frame in 0 until frames) {
            val base = frame * sourceChannelCount
            var left = 0f
            var right = 0f
            when (ordered.size) {
                1 -> {
                    val s = interleaved[base + ordered[0]] * gain
                    left = s
                    right = s
                }
                else -> {
                    left = interleaved[base + ordered[0]] * gain
                    right = interleaved[base + ordered[min(1, ordered.lastIndex)]] * gain
                }
            }
            dest[frame * 2] = left.coerceIn(-1f, 1f)
            dest[frame * 2 + 1] = right.coerceIn(-1f, 1f)
        }
        return frames
    }
}
