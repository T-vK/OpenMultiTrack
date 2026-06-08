package org.openmultitrack.app.audio

/**
 * Mixes selected interleaved capture channels into mono or stereo monitor / virtual-mic output.
 * Channel indices are 0-based.
 */
object ChannelMixdown {
    fun outputChannelCount(selectedChannels: Set<Int>, stereo: Boolean): Int =
        when {
            selectedChannels.isEmpty() -> 0
            stereo && selectedChannels.size >= 2 -> 2
            else -> 1
        }

    fun mixToOutput(
        interleaved: FloatArray,
        frames: Int,
        sourceChannelCount: Int,
        selectedChannels: Set<Int>,
        stereo: Boolean,
        dest: FloatArray,
    ): Int {
        if (frames <= 0 || selectedChannels.isEmpty()) return 0
        val ordered = selectedChannels.sorted()
        val outChannels = outputChannelCount(selectedChannels, stereo)
        if (outChannels == 0) return 0

        for (frame in 0 until frames) {
            val base = frame * sourceChannelCount
            if (outChannels == 1) {
                var sum = 0f
                for (ch in ordered) {
                    sum += interleaved[base + ch]
                }
                dest[frame] = sum / ordered.size
            } else {
                val left = interleaved[base + ordered[0]]
                val right = interleaved[base + ordered[minOf(1, ordered.lastIndex)]]
                dest[frame * 2] = left
                dest[frame * 2 + 1] = right
            }
        }
        return frames
    }
}
