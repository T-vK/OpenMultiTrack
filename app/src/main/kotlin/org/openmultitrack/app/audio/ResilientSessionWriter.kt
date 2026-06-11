package org.openmultitrack.app.audio

import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.wav.PerChannelWavWriter
import java.io.File

/**
 * Writes session WAVs to a primary location, optional mirror roots, and a local spill copy.
 * Recording continues on spill when external media is unavailable or below min free space.
 */
class ResilientSessionWriter private constructor(
    private val primary: PerChannelWavWriter,
    private val mirrors: List<PerChannelWavWriter>,
    private val spill: PerChannelWavWriter?,
    private val minFreeBytes: Long,
    private val primaryRoot: File,
) : AutoCloseable {
    private var primaryHealthy = true

    constructor(
        primarySessionDir: File,
        mirrorSessionDirs: List<File>,
        spillSessionDir: File?,
        channelStrips: List<ChannelStripState>,
        sampleRate: Int,
        minFreeBytes: Long,
        primaryRoot: File,
    ) : this(
        primary = PerChannelWavWriter(primarySessionDir, channelStrips, sampleRate),
        mirrors = mirrorSessionDirs.map { PerChannelWavWriter(it, channelStrips, sampleRate) },
        spill = spillSessionDir?.let { PerChannelWavWriter(it, channelStrips, sampleRate) },
        minFreeBytes = minFreeBytes,
        primaryRoot = primaryRoot,
    )

    fun writeInterleavedMultiChannel(samples: FloatArray, frames: Int, sourceChannelCount: Int) {
        if (primaryHealthy) {
            if (minFreeBytes > 0 && primaryRoot.usableSpace < minFreeBytes) {
                primaryHealthy = false
            } else {
                runCatching {
                    primary.writeInterleavedMultiChannel(samples, frames, sourceChannelCount)
                }.onFailure {
                    primaryHealthy = false
                }
            }
        }
        for (mirror in mirrors) {
            runCatching { mirror.writeInterleavedMultiChannel(samples, frames, sourceChannelCount) }
        }
        spill?.writeInterleavedMultiChannel(samples, frames, sourceChannelCount)
    }

    fun writeSilence(frames: Int) {
        if (primaryHealthy) {
            runCatching { primary.writeSilence(frames) }.onFailure { primaryHealthy = false }
        }
        for (mirror in mirrors) {
            runCatching { mirror.writeSilence(frames) }
        }
        spill?.writeSilence(frames)
    }

    fun channelStrips(): List<ChannelStripState> = primary.channelStrips()

    fun totalFramesWritten(): Long = primary.totalFramesWritten()

    fun isPrimaryHealthy(): Boolean = primaryHealthy

    fun primarySessionDir(): File = primary.filePaths().firstOrNull()?.let { File(it).parentFile }
        ?: error("no primary session files")

    override fun close() {
        primary.close()
        mirrors.forEach { it.close() }
        spill?.close()
    }

    companion object {
        fun openForResume(
            primarySessionDir: File,
            metadata: SessionMetadata,
            mirrorSessionDirs: List<File> = emptyList(),
            spillSessionDir: File? = null,
            minFreeBytes: Long = 0L,
            primaryRoot: File = primarySessionDir.parentFile?.parentFile ?: primarySessionDir,
        ): ResilientSessionWriter = ResilientSessionWriter(
            primary = PerChannelWavWriter.openForResume(primarySessionDir, metadata),
            mirrors = mirrorSessionDirs.map { PerChannelWavWriter.openForResume(it, metadata) },
            spill = spillSessionDir?.let { PerChannelWavWriter.openForResume(it, metadata) },
            minFreeBytes = minFreeBytes,
            primaryRoot = primaryRoot,
        )
    }
}
