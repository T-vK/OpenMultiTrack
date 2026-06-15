package org.openmultitrack.app.audio

import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.channel.ChannelStripState
import org.openmultitrack.sessionio.session.ChannelFileNaming
import org.openmultitrack.sessionio.session.SessionMetadata
import org.openmultitrack.sessionio.wav.InterleavedRawPcmSplitter
import org.openmultitrack.sessionio.wav.InterleavedWavSplitter
import org.openmultitrack.sessionio.wav.PerChannelWavWriter
import org.openmultitrack.sessionio.wav.WavWriter
import java.io.File

/**
 * Writes session WAVs to a primary location, optional mirror roots, and a local spill copy.
 * During capture only the primary (or spill fallback) is written live; mirrors are synced on [close].
 *
 * For 8+ channel UAC2 capture, native code streams interleaved PCM to a staging raw file.
 * Per-channel WAVs are built incrementally during capture (and only a short tail on stop).
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
    private val liveCaptureStagingFile: File?,
    private val nativePcmBytesPerFrame: Int,
    private val stagingChannelWriters: Map<Int, WavWriter>?,
    private val mirrorSessionDirs: List<File>,
    private val spillSessionDir: File?,
    private val minFreeBytes: Long,
    private val primaryRoot: File,
) : AutoCloseable {
    private var primaryHealthy = true
    private var liveFramesWritten: Long = 0
    private var nativeStagingSkipFrames: Long = 0
    private var stagingSplitSessionFrames: Long = 0

    fun setNativeStagingSkipFrames(frames: Long) {
        nativeStagingSkipFrames = frames.coerceAtLeast(0L)
    }

    constructor(
        primarySessionDir: File,
        mirrorSessionDirs: List<File>,
        spillSessionDir: File?,
        channelStrips: List<ChannelStripState>,
        sampleRate: Int,
        minFreeBytes: Long,
        primaryRoot: File,
        captureChannelCount: Int = channelStrips.maxOfOrNull { it.index }?.plus(1) ?: 1,
        liveCaptureStagingFile: File? = null,
        nativeBytesPerFrame: Int = 0,
    ) : this(
        primarySessionDir = primarySessionDir,
        channelStrips = channelStrips,
        sampleRateHz = sampleRate,
        captureChannelCount = captureChannelCount,
        primary = if (captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD && liveCaptureStagingFile == null) {
            null
        } else if (captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD) {
            null
        } else {
            PerChannelWavWriter(primarySessionDir, channelStrips, sampleRate)
        },
        spill = if (captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD) {
            null
        } else {
            spillSessionDir?.let { PerChannelWavWriter(it, channelStrips, sampleRate) }
        },
        livePrimary = if (captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD && liveCaptureStagingFile == null) {
            WavWriter(
                File(primarySessionDir, INTERLEAVED_TMP_NAME),
                captureChannelCount,
                sampleRate,
                bitsPerSample = 32,
            )
        } else {
            null
        },
        liveSpill = if (captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD &&
            liveCaptureStagingFile == null &&
            spillSessionDir != null
        ) {
            WavWriter(
                File(spillSessionDir, INTERLEAVED_TMP_NAME),
                captureChannelCount,
                sampleRate,
                bitsPerSample = 32,
            )
        } else {
            null
        },
        liveCaptureStagingFile = liveCaptureStagingFile,
        nativePcmBytesPerFrame = if (liveCaptureStagingFile != null) {
            nativeBytesPerFrame.takeIf { it > 0 } ?: (captureChannelCount * 4)
        } else {
            0
        },
        stagingChannelWriters = if (
            liveCaptureStagingFile != null && captureChannelCount >= INTERLEAVED_LIVE_THRESHOLD
        ) {
            channelStrips.filter { it.armed }.associate { strip ->
                strip.index to WavWriter(
                    File(primarySessionDir, ChannelFileNaming.fileName(strip.index, strip.label)),
                    1,
                    sampleRate,
                )
            }
        } else {
            null
        },
        mirrorSessionDirs = mirrorSessionDirs,
        spillSessionDir = spillSessionDir,
        minFreeBytes = minFreeBytes,
        primaryRoot = primaryRoot,
    )

    fun writeInterleavedMultiChannel(samples: FloatArray, frames: Int, sourceChannelCount: Int) {
        livePrimary?.let { writer ->
            if (primaryHealthy) {
                if (minFreeBytes > 0 && primaryRoot.usableSpace < minFreeBytes) {
                    primaryHealthy = false
                } else {
                    val wrote = runCatching {
                        writeFloatInterleavedToLiveWriter(writer, samples, frames, sourceChannelCount)
                    }
                    if (wrote.isSuccess) {
                        liveFramesWritten += frames
                        return
                    }
                    primaryHealthy = false
                }
            }
            liveSpill?.let { spillWriter ->
                runCatching {
                    writeFloatInterleavedToLiveWriter(spillWriter, samples, frames, sourceChannelCount)
                }
            }
            return
        }
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

    fun setLiveFramesWritten(frames: Long) {
        liveFramesWritten = frames
    }

    fun totalFramesWritten(): Long {
        if (stagingSplitSessionFrames > 0L) return stagingSplitSessionFrames
        if (liveFramesWritten > 0L) return liveFramesWritten
        val primaryFrames = primary?.totalFramesWritten() ?: 0L
        if (primaryHealthy && primaryFrames > 0L) return primaryFrames
        return spill?.totalFramesWritten()?.takeIf { it > 0L } ?: primaryFrames
    }

    fun isPrimaryHealthy(): Boolean = primaryHealthy

    fun hasNativeStaging(): Boolean = liveCaptureStagingFile != null

    fun hasInterleavedLive(): Boolean = livePrimary != null

    /**
     * While native PCM is recording to [liveCaptureStagingFile], copy new frames into open
     * per-channel WAVs so stop only finalizes a short tail.
     */
    fun pumpNativeStagingIfNeeded(nativeFileFramesWritten: Long) {
        val raw = liveCaptureStagingFile ?: return
        val writers = stagingChannelWriters ?: return
        val bpf = nativePcmBytesPerFrame
        if (bpf <= 0) return
        val skip = nativeStagingSkipFrames
        val sessionFramesAvailable = (nativeFileFramesWritten - skip).coerceAtLeast(0L)
        val pending = sessionFramesAvailable - stagingSplitSessionFrames
        if (pending < PUMP_MIN_FRAMES) return
        val rawFramesOnDisk = raw.length() / bpf
        val maxSafe = (rawFramesOnDisk - skip - stagingSplitSessionFrames).coerceAtLeast(0L)
        val toPump = minOf(pending, maxSafe)
        if (toPump <= 0L) return
        InterleavedRawPcmSplitter.appendRangeToWriters(
            rawFile = raw,
            writers = writers,
            sourceChannelCount = captureChannelCount,
            bytesPerFrame = bpf,
            sourceFrameOffset = skip + stagingSplitSessionFrames,
            frameCount = toPump,
        )
        stagingSplitSessionFrames += toPump
    }

    fun primarySessionDir(): File = primary?.filePaths()?.firstOrNull()?.let { File(it).parentFile }
        ?: primarySessionDir

    override fun close() = close(trace = null)

    fun close(trace: RecordStopTrace?) {
        trace?.mark(
            "ResilientWriter.close nativeStaging=${liveCaptureStagingFile != null} " +
                "interleavedLive=${livePrimary != null} perChannel=${primary != null}",
        )
        trace?.timed("livePrimary.close") { livePrimary?.close() }
        trace?.timed("liveSpill.close") { liveSpill?.close() }
        when {
            liveCaptureStagingFile != null && stagingChannelWriters != null -> {
                trace?.timed("finalizeNativeStagingIncremental") {
                    finalizeNativeStagingIncremental(trace)
                }
            }
            liveCaptureStagingFile != null -> {
                trace?.timed("finalizeNativeStaging") {
                    finalizeNativeStaging(liveCaptureStagingFile, primarySessionDir)
                }
            }
            livePrimary != null -> {
                trace?.timed("finalizeInterleavedLive.primary") {
                    finalizeInterleavedLive(livePrimary, primarySessionDir)
                }
                liveSpill?.let { spillFile ->
                    if (spillSessionDir != null && !primaryHealthy) {
                        trace?.timed("finalizeInterleavedLive.spill") {
                            finalizeInterleavedLive(spillFile, spillSessionDir)
                        }
                    } else {
                        File(spillFile.outputFile.absolutePath).delete()
                    }
                }
            }
            else -> {
                trace?.timed("primaryPerChannel.close") { primary?.close() }
                trace?.timed("spillPerChannel.close") { spill?.close() }
            }
        }
        trace?.timed("syncRedundantCopies mirrors=${mirrorSessionDirs.size}") { syncRedundantCopies() }
    }

    private fun finalizeNativeStaging(raw: File, targetDir: File) {
        val bpf = nativePcmBytesPerFrame
        if (!raw.isFile || bpf <= 0) {
            raw.delete()
            return
        }
        val fileBytes = raw.length()
        if (fileBytes <= 0L) {
            OmtLog.w("ResilientWriter", "native staging raw missing or empty: ${raw.absolutePath}")
            raw.delete()
            return
        }
        val fileFrameCount = fileBytes / bpf
        if (fileFrameCount <= 0L) {
            raw.delete()
            return
        }
        if (fileBytes % bpf != 0L) {
            OmtLog.w(
                "ResilientWriter",
                "native staging raw size $fileBytes not aligned to bytesPerFrame=$bpf",
            )
        }
        val skip = nativeStagingSkipFrames.coerceIn(0L, fileFrameCount)
        val available = (fileFrameCount - skip).coerceAtLeast(0L)
        val frames = when {
            liveFramesWritten > 0L -> liveFramesWritten.coerceIn(0L, available)
            available > 0L -> available
            else -> {
                raw.delete()
                return
            }
        }
        if (skip != nativeStagingSkipFrames || frames != liveFramesWritten) {
            OmtLog.w(
                "ResilientWriter",
                "native split clamped skip=$nativeStagingSkipFrames→$skip " +
                    "frames=$liveFramesWritten→$frames fileFrames=$fileFrameCount bpf=$bpf",
            )
        }
        OmtLog.i(
            "RecordStop",
            "finalizeNativeStaging fileBytes=$fileBytes frames=$frames skip=$skip bpf=$bpf " +
                "target=${targetDir.absolutePath}",
        )
        runCatching {
            InterleavedRawPcmSplitter.splitToPerChannel(
                rawFile = raw,
                sessionDir = targetDir,
                channelStrips = channelStrips,
                sourceChannelCount = captureChannelCount,
                sampleRate = sampleRateHz,
                bytesPerFrame = bpf,
                frameCount = frames,
                sourceFrameOffset = skip,
            )
        }.onFailure { e ->
            OmtLog.e(
                "ResilientWriter",
                "native staging split failed: ${e.javaClass.simpleName}: ${e.message ?: e.toString()} " +
                    "(file=${raw.length()}B skip=$skip frames=$frames bpf=$bpf)",
            )
        }
        raw.delete()
    }

    private fun finalizeNativeStagingIncremental(trace: RecordStopTrace?) {
        val raw = liveCaptureStagingFile ?: return
        val writers = stagingChannelWriters ?: return
        val bpf = nativePcmBytesPerFrame
        val targetFrames = liveFramesWritten.coerceAtLeast(stagingSplitSessionFrames)
        val pending = targetFrames - stagingSplitSessionFrames
        trace?.mark(
            "staging incremental tail=$pending alreadySplit=$stagingSplitSessionFrames " +
                "target=$targetFrames skip=$nativeStagingSkipFrames",
        )
        if (pending > 0 && bpf > 0) {
            InterleavedRawPcmSplitter.appendRangeToWriters(
                rawFile = raw,
                writers = writers,
                sourceChannelCount = captureChannelCount,
                bytesPerFrame = bpf,
                sourceFrameOffset = nativeStagingSkipFrames + stagingSplitSessionFrames,
                frameCount = pending,
            )
            stagingSplitSessionFrames = targetFrames
        }
        trace?.timed("stagingChannelWriters.close count=${writers.size}") {
            writers.values.forEach { it.close() }
        }
        raw.delete()
    }

    private fun finalizeInterleavedLive(writer: WavWriter, targetDir: File) {
        val tmp = writer.outputFile
        if (!tmp.isFile || writer.framesWritten <= 0L) {
            tmp.delete()
            return
        }
        OmtLog.i(
            "RecordStop",
            "finalizeInterleavedLive frames=${writer.framesWritten} " +
                "fileBytes=${tmp.length()} target=${targetDir.absolutePath}",
        )
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

    private fun writeFloatInterleavedToLiveWriter(
        writer: WavWriter,
        samples: FloatArray,
        frames: Int,
        sourceChannelCount: Int,
    ) {
        val channels = captureChannelCount
        val expected = frames * sourceChannelCount
        require(samples.size >= expected) { "samples too short: ${samples.size} < $expected" }
        val bytesPerFrame = channels * 4
        val byteLen = frames * bytesPerFrame
        val packed = ByteArray(byteLen)
        var bi = 0
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                val sample = if (c < sourceChannelCount) {
                    samples[f * sourceChannelCount + c]
                } else {
                    0f
                }
                val bits = java.lang.Float.floatToIntBits(sample)
                packed[bi++] = (bits and 0xFF).toByte()
                packed[bi++] = ((bits shr 8) and 0xFF).toByte()
                packed[bi++] = ((bits shr 16) and 0xFF).toByte()
                packed[bi++] = ((bits shr 24) and 0xFF).toByte()
            }
        }
        writer.writeRawInterleavedPcm(packed, frames, bytesPerFrame)
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
        /** ~85 ms @ 48 kHz — batch enough to amortize IO without lagging far behind capture. */
        private const val PUMP_MIN_FRAMES = 4096L

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
            liveCaptureStagingFile = null,
            nativePcmBytesPerFrame = 0,
            stagingChannelWriters = null,
            mirrorSessionDirs = mirrorSessionDirs,
            spillSessionDir = spillSessionDir,
            minFreeBytes = minFreeBytes,
            primaryRoot = primaryRoot,
        )
    }
}
