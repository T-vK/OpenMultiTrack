package org.openmultitrack.app.audio

import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.wav.PerChannelWavWriter
import java.io.File

/**
 * Writes session WAVs to a primary location, optional mirror roots, and a local spill copy.
 * During capture only the primary (or spill fallback) is written live; mirrors are synced on [close].
 */
class ResilientSessionWriter private constructor(
    private val primary: PerChannelWavWriter,
    private val spill: PerChannelWavWriter?,
    private val mirrorSessionDirs: List<File>,
    private val spillSessionDir: File?,
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
        spill = spillSessionDir?.let { PerChannelWavWriter(it, channelStrips, sampleRate) },
        mirrorSessionDirs = mirrorSessionDirs,
        spillSessionDir = spillSessionDir,
        minFreeBytes = minFreeBytes,
        primaryRoot = primaryRoot,
    )

    fun writeInterleavedMultiChannel(samples: FloatArray, frames: Int, sourceChannelCount: Int) {
        if (primaryHealthy) {
            if (minFreeBytes > 0 && primaryRoot.usableSpace < minFreeBytes) {
                primaryHealthy = false
            } else {
                val wrote = runCatching {
                    primary.writeInterleavedMultiChannel(samples, frames, sourceChannelCount)
                }
                if (wrote.isSuccess) {
                    return
                }
                primaryHealthy = false
            }
        }
        spill?.writeInterleavedMultiChannel(samples, frames, sourceChannelCount)
    }

    fun writeSilence(frames: Int) {
        if (primaryHealthy) {
            val wrote = runCatching { primary.writeSilence(frames) }
            if (wrote.isSuccess) {
                return
            }
            primaryHealthy = false
        }
        spill?.writeSilence(frames)
    }

    fun channelStrips(): List<ChannelStripState> = primary.channelStrips()

    fun totalFramesWritten(): Long {
        val primaryFrames = primary.totalFramesWritten()
        if (primaryHealthy && primaryFrames > 0L) return primaryFrames
        return spill?.totalFramesWritten()?.takeIf { it > 0L } ?: primaryFrames
    }

    fun isPrimaryHealthy(): Boolean = primaryHealthy

    fun primarySessionDir(): File = primary.filePaths().firstOrNull()?.let { File(it).parentFile }
        ?: error("no primary session files")

    override fun close() {
        primary.close()
        spill?.close()
        syncRedundantCopies()
    }

    private fun syncRedundantCopies() {
        val spillDir = spillSessionDir
        val sourceDir = when {
            primaryHealthy -> primarySessionDir()
            spillDir != null && spillDir.isDirectory -> spillDir
            else -> primarySessionDir()
        }
        val wavFiles = sourceDir.listFiles { f -> f.isFile && f.extension.equals("wav", ignoreCase = true) }
            ?: return
        if (wavFiles.isEmpty()) return
        val meta = SessionMetadata.read(sourceDir)
        for (targetDir in mirrorSessionDirs) {
            runCatching {
                targetDir.mkdirs()
                for (wav in wavFiles) {
                    wav.copyTo(File(targetDir, wav.name), overwrite = true)
                }
                meta?.writeTo(targetDir)
            }.onFailure { e ->
                OmtLog.w("ResilientWriter", "mirror sync failed ${targetDir.name}: ${e.message}")
            }
        }
        if (primaryHealthy && spillDir != null && spillDir != sourceDir) {
            runCatching {
                spillDir.mkdirs()
                for (wav in wavFiles) {
                    wav.copyTo(File(spillDir, wav.name), overwrite = true)
                }
                meta?.writeTo(spillDir)
            }.onFailure { e ->
                OmtLog.w("ResilientWriter", "spill sync failed: ${e.message}")
            }
        }
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
            spill = spillSessionDir?.let { PerChannelWavWriter.openForResume(it, metadata) },
            mirrorSessionDirs = mirrorSessionDirs,
            spillSessionDir = spillSessionDir,
            minFreeBytes = minFreeBytes,
            primaryRoot = primaryRoot,
        )
    }
}
