package org.openmultitrack.sessionio.wav

import org.openmultitrack.sessionio.session.SessionMetadata
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** On-disk cache for session waveform overviews (speeds up repeat opens). */
object SessionWaveformCache {
    private const val MAGIC = "OMTW"
    private const val VERSION = 1

    fun load(sessionDir: File, metadata: SessionMetadata): SessionWaveformOverview? {
        val cacheFile = cacheFile(sessionDir)
        if (!cacheFile.isFile || !isValid(cacheFile, sessionDir, metadata)) return null
        return runCatching {
            DataInputStream(cacheFile.inputStream()).use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                require(String(magic) == MAGIC) { "bad magic" }
                require(input.readInt() == VERSION) { "bad version" }
                val peaksPerSec = input.readFloat()
                val durationSec = input.readFloat()
                val channelCount = input.readInt()
                val peaksByChannel = linkedMapOf<Int, FloatArray>()
                repeat(channelCount) {
                    val index = input.readInt()
                    val peakCount = input.readInt()
                    val bytes = ByteArray(peakCount * 4)
                    input.readFully(bytes)
                    val peaks = FloatArray(peakCount)
                    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until peakCount) {
                        peaks[i] = buf.getFloat(i * 4)
                    }
                    peaksByChannel[index] = peaks
                }
                SessionWaveformOverview(
                    peaksByChannel = peaksByChannel,
                    peaksPerSec = peaksPerSec,
                    durationSec = durationSec,
                )
            }
        }.getOrNull()
    }

    fun save(sessionDir: File, overview: SessionWaveformOverview) {
        val cacheFile = cacheFile(sessionDir)
        runCatching {
            DataOutputStream(cacheFile.outputStream()).use { output ->
                output.writeBytes(MAGIC)
                output.writeInt(VERSION)
                output.writeFloat(overview.peaksPerSec)
                output.writeFloat(overview.durationSec)
                output.writeInt(overview.peaksByChannel.size)
                overview.peaksByChannel.entries.sortedBy { it.key }.forEach { (index, peaks) ->
                    output.writeInt(index)
                    output.writeInt(peaks.size)
                    val buf = ByteBuffer.allocate(peaks.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                    peaks.forEach { buf.putFloat(it) }
                    output.write(buf.array())
                }
            }
        }
    }

    fun isValid(cacheFile: File, sessionDir: File, metadata: SessionMetadata): Boolean {
        if (!cacheFile.isFile) return false
        val cacheMtime = cacheFile.lastModified()
        val sessionJson = File(sessionDir, "session.json")
        if (sessionJson.isFile && cacheMtime < sessionJson.lastModified()) return false
        return metadata.resolvedChannels(sessionDir).all { ch ->
            val wav = File(sessionDir, ch.fileName)
            !wav.isFile || cacheMtime >= wav.lastModified()
        }
    }

    fun cacheFile(sessionDir: File): File = File(sessionDir, "waveform_overview.cache")
}
