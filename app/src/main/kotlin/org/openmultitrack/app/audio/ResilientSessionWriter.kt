package org.openmultitrack.app.audio

import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.wav.InterleavedWavSplitter
import org.openmultitrack.sessionio.wav.PerChannelWavWriter
import org.openmultitrack.sessionio.wav.WavWriter
import java.io.File

/**
 * Writes session WAVs to a primary location, optional mirror roots, and a local spill copy.
 * During capture only the primary (or spill fallback) is written live; mirrors are synced on [close].
 *
 * For 8+ channel UAC2 capture, live PCM is appended to a single interleaved temp WAV and split
 * into per-channel files on [close] so the disk thread avoids per-channel deinterleave work.
 */
class ResilientSessionWriter private constructor(
    private val primarySessionDir: File,
    private val channelStrips: List<ChannelStripState>,
    private val sampleRateHz: Int,
    private val captureChannelCount: Int,
    private val primary: PerChannelWavWriter?,
    private val spill: PerChannelWavWriter?,
    private val livePrimary: WavWriter?,
    private val liveSpill: WavWriter?,
    private val mirrorSessionDirs: List<File>,
    private val spillSessionDir: File?,
    private val minFreeBytes: Long,
    private val primaryRoot: File,
) : AutoCloseable {
    private var primaryHealthy = true
    private var liveFramesWritten: Long = 0

    constructor(
        primarySessionDir: File,
        mirrorSessionDirs: List<File>,
        spillSessionDir: File?,
        channelStrips: List<ChannelStripState>,
        sampleRate: Int,
        minFreeBytes: Long,
        primaryRoot: File,
        captureChannelCount: Int = channelStrips.maxOfOrNull { it.index }?.plus(1) ?: 1,
    ) : this(
        primarySessionDir = primarySessionDir,
        channelStrips = channelStrips,
        sampleRateHz = sampleRate,
        captureChannelCount = captureChannelCount,
        primary = if (captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD) {
            null
        } else {
            PerChannelWavWriter(primarySessionDir, channelStrips, sampleRate)
        },
        spill = if (captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD) {
            null
        } else {
            spillSessionDir?.let { PerChannelWavWriter(it, channelStrips, sampleRate) }
        },
        livePrimary = if (captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD) {
            WavWriter(
                File(primarySessionDir, INTERLEAVED_TMP_NAME),
                captureChannelCount,
                sampleRate,
                bitsPerSample = 32,
            )
        } else {
            null
        },
        liveSpill = if (captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD && spillSessionDir != null) {
            WavWriter(
                File(spillSessionDir, INTERLEAVED_TMP_NAME),
                captureChannelCount,
                sampleRate,
                bitsPerSample = 32,
            )
        } else {
            null
        },
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
                    requireNotNull(primary) { "interleaved live capture uses PCM writes" }
                        .writeInterleavedMultiChannel(samples, frames, sourceChannelCount)
                }
                if (wrote.isSuccess) {
                    return
                }
                primaryHealthy = false
            }
        }
        spill?.writeInterleavedMultiChannel(samples, frames, sourceChannelCount)
    }

    fun writeInterleavedPcm24(
        samples: ByteArray,
        frames: Int,
        sourceChannelCount: Int,
        bytesPerFrame: Int,
    ) {
        if (livePrimary != null) {
            val primaryWriter = livePrimary
            if (primaryHealthy) {
                if (minFreeBytes > 0 && primaryRoot.usableSpace < minFreeBytes) {
                    primaryHealthy = false
                } else {
                    val wrote = runCatching {
                        primaryWriter.writePackedInterleavedPcmAs24(samples, frames, bytesPerFrame)
                    }
                    if (wrote.isSuccess) {
                        liveFramesWritten += frames
                        return
                    }
                    primaryHealthy = false
                }
            }
            runCatching { liveSpill?.writePackedInterleavedPcmAs24(samples, frames, bytesPerFrame) }
            return
        }
        if (primaryHealthy) {
            if (minFreeBytes > 0 && primaryRoot.usableSpace < minFreeBytes) {
                primaryHealthy = false
            } else {
                val wrote = runCatching {
                    requireNotNull(primary)
                        .writeInterleavedPcm24(samples, frames, sourceChannelCount, bytesPerFrame)
                }
                if (wrote.isSuccess) {
                    return
                }
                primaryHealthy = false
            }
        }
        spill?.writeInterleavedPcm24(samples, frames, sourceChannelCount, bytesPerFrame)
    }

    fun writeSilence(frames: Int) {
        if (livePrimary != null) {
            val silence = ByteArray(frames * captureChannelCount * 4)
            writeInterleavedPcm24(silence, frames, captureChannelCount, captureChannelCount * 4)
            return
        }
        if (primaryHealthy) {
            val wrote = runCatching { requireNotNull(primary).writeSilence(frames) }
            if (wrote.isSuccess) {
                return
            }
            primaryHealthy = false
        }
        spill?.writeSilence(frames)
    }

    fun channelStrips(): List<ChannelStripState> = channelStrips

    fun totalFramesWritten(): Long {
        if (liveFramesWritten > 0L) return liveFramesWritten
        val primaryFrames = primary?.totalFramesWritten() ?: 0L
        if (primaryHealthy && primaryFrames > 0L) return primaryFrames
        return spill?.totalFramesWritten()?.takeIf { it > 0L } ?: primaryFrames
    }

    fun isPrimaryHealthy(): Boolean = primaryHealthy

    fun primarySessionDir(): File = primary?.filePaths()?.firstOrNull()?.let { File(it).parentFile }
        ?: primarySessionDir

    override fun close() {
        livePrimary?.close()
        liveSpill?.close()
        if (livePrimary != null) {
            finalizeInterleavedLive(livePrimary, primarySessionDir)
            liveSpill?.let { spillFile ->
                if (spillSessionDir != null && !primaryHealthy) {
                    finalizeInterleavedLive(spillFile, spillSessionDir)
                } else {
                    File(spillFile.outputFile.absolutePath).delete()
                }
            }
        } else {
            primary?.close()
            spill?.close()
        }
        syncRedundantCopies()
    }

    private fun finalizeInterleavedLive(writer: WavWriter, targetDir: File) {
        val tmp = writer.outputFile
        if (!tmp.isFile || writer.framesWritten <= 0L) {
            tmp.delete()
            return
        }
        runCatching {
            InterleavedWavSplitter.splitToPerChannel(
                interleavedFile = tmp,
                sessionDir = targetDir,
                channelStrips = channelStrips,
                sourceChannelCount = captureChannelCount,
                sampleRate = sampleRateHz,
            )
        }.onFailure { e ->
            OmtLog.e("ResilientWriter", "interleaved split failed: ${e.message}")
        }
        tmp.delete()
    }

    private fun syncRedundantCopies() {
        val spillDir = spillSessionDir
        val sourceDir = when {
            primaryHealthy -> primarySessionDir()
            spillDir != null && spillDir.isDirectory -> spillDir
            else -> primarySessionDir()
        }
        val wavFiles = sourceDir.listFiles { f ->
            f.isFile && f.extension.equals("wav", ignoreCase = true) && f.name != INTERLEAVED_TMP_NAME
        } ?: return
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
        private const val INTERLEAVED_LIVE_THRESHOLD = 8
        private const val INTERLEAVED_TMP_NAME = ".capture_interleaved.tmp"

        fun openForResume(
            primarySessionDir: File,
            metadata: SessionMetadata,
            mirrorSessionDirs: List<File> = emptyList(),
            spillSessionDir: File? = null,
            minFreeBytes: Long = 0L,
            primaryRoot: File = primarySessionDir.parentFile?.parentFile ?: primarySessionDir,
        ): ResilientSessionWriter = ResilientSessionWriter(
            primarySessionDir = primarySessionDir,
            channelStrips = PerChannelWavWriter.openForResume(primarySessionDir, metadata).channelStrips(),
            sampleRateHz = metadata.sampleRate,
            captureChannelCount = metadata.channels.maxOfOrNull { it.index }?.plus(1) ?: 1,
            primary = PerChannelWavWriter.openForResume(primarySessionDir, metadata),
            spill = spillSessionDir?.let { PerChannelWavWriter.openForResume(it, metadata) },
            livePrimary = null,
            liveSpill = null,
            mirrorSessionDirs = mirrorSessionDirs,
            spillSessionDir = spillSessionDir,
            minFreeBytes = minFreeBytes,
            primaryRoot = primaryRoot,
        )
    }
}
