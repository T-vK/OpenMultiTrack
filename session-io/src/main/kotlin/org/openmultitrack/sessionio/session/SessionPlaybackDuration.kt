package org.openmultitrack.sessionio.session

import org.openmultitrack.sessionio.wav.PerChannelWavWriter
import java.io.File

/** Playback length derived from audio on disk (shortest armed channel), not timeline metadata. */
object SessionPlaybackDuration {
    fun durationFrames(sessionDir: File, metadata: SessionMetadata): Long {
        val channels = metadata.withResolvedChannels(sessionDir).channels
        if (channels.isEmpty()) return 0L
        return channels.minOfOrNull { ch ->
            PerChannelWavWriter.framesOnDisk(File(sessionDir, ch.fileName))
        } ?: 0L
    }

    fun durationSec(sessionDir: File, metadata: SessionMetadata): Float {
        val sampleRate = metadata.sampleRate.coerceAtLeast(1)
        return durationFrames(sessionDir, metadata).toFloat() / sampleRate
    }

    /** True when at least one resolved channel WAV exists on disk for playback or waveforms. */
    fun isPlayable(sessionDir: File, metadata: SessionMetadata): Boolean {
        if (metadata.incomplete) return false
        val channels = metadata.withResolvedChannels(sessionDir).channels
        if (channels.isEmpty()) return false
        return channels.any { ch -> File(sessionDir, ch.fileName).isFile }
    }
}
