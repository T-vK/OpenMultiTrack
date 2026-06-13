package org.openmultitrack.app.audio

import android.hardware.usb.UsbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.usb.AudioBackend
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.PlaybackRoute

/** Continuous per-channel USB return test tone with UAC2 wall-clock pacing. */
class UsbTestTonePlayer {
    private var playbackJob: Job? = null
    private var activeBackend: AudioBackend? = null
    private var playbackEpoch: Int = 0
    private var paceSampleRate: Int = 0
    private var paceAnchorNs: Long = 0L
    private var paceStartFrames: Long = 0L
    private var submittedFrames: Long = 0L

    val isPlaying: Boolean
        get() = playbackJob?.isActive == true

    fun start(
        scope: CoroutineScope,
        route: PlaybackRoute,
        usbDevice: UsbDevice?,
        usbChannelIndex: Int,
    ): Result<Unit> {
        val trace = TransportTrace("usbTestTone")
        val channelCount = route.channelCount.coerceAtLeast(1)
        if (usbChannelIndex !in 0 until channelCount) {
            return Result.failure(
                IllegalArgumentException("USB channel $usbChannelIndex out of range (0..${channelCount - 1})"),
            )
        }
        val backend = route.backend
        playbackEpoch++
        val epoch = playbackEpoch
        trace.mark("starting backend=$backend ch=$usbChannelIndex/$channelCount")

        val engineStatus = AudioEngineRouter.startPlayback(route, usbDevice)
        if (!engineStatus.active) {
            activeBackend = null
            return Result.failure(
                IllegalStateException(engineStatus.errorMessage ?: "USB test tone playback failed"),
            )
        }
        activeBackend = backend
        paceSampleRate = if (backend == AudioBackend.UAC2) route.sampleRate else 0
        submittedFrames = 0L
        resetUac2PaceAnchor(0L)

        val generator = UsbPlaybackToneGenerator(
            channelCount = channelCount,
            activeChannel = usbChannelIndex,
            sampleRate = route.sampleRate,
        )
        val scratch = FloatArray(2048 * channelCount)
        val chunkScratch = FloatArray(2048 * channelCount)
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                if (epoch != playbackEpoch) return@launch
                primeUac2Ring(generator, channelCount, scratch, chunkScratch)
                while (playbackJob?.isActive == true && epoch == playbackEpoch) {
                    val frames = generator.fill(scratch, 2048)
                    if (frames <= 0) break
                    var submitted = 0
                    while (submitted < frames && playbackJob?.isActive == true && epoch == playbackEpoch) {
                        val framesLeft = frames - submitted
                        val sampleStart = submitted * channelCount
                        val sampleCount = framesLeft * channelCount
                        System.arraycopy(scratch, sampleStart, chunkScratch, 0, sampleCount)
                        val written = AudioEngineRouter.writePlaybackFrames(chunkScratch, framesLeft, backend)
                        if (written <= 0) {
                            Thread.sleep(2)
                            continue
                        }
                        if (submittedFrames == 0L) {
                            OmtLog.i(
                                "UsbTestTone",
                                "first frames written=$written backend=$backend ch=$usbChannelIndex/$channelCount",
                            )
                        }
                        submitted += written
                        submittedFrames += written
                        throttleUac2PlaybackIfAhead()
                    }
                }
            } catch (e: Exception) {
                OmtLog.e("UsbTestTone", "playback loop failed", e)
                stopNative()
            } finally {
                if (epoch == playbackEpoch) {
                    val underruns = AudioEngineRouter.playbackUnderrunFrames(backend)
                    trace.mark("loop ended frames=$submittedFrames underruns=$underruns")
                    playbackJob = null
                }
            }
        }
        trace.mark("loop launched")
        return Result.success(Unit)
    }

    suspend fun stopAndAwait() {
        playbackEpoch++
        val job = playbackJob
        playbackJob = null
        job?.cancel()
        job?.join()
        withContext(Dispatchers.IO) { stopNative() }
    }

    fun stop() {
        playbackEpoch++
        playbackJob?.cancel()
        playbackJob = null
        stopNative()
    }

    private fun primeUac2Ring(
        generator: UsbPlaybackToneGenerator,
        channelCount: Int,
        scratch: FloatArray,
        chunkScratch: FloatArray,
    ) {
        if (activeBackend != AudioBackend.UAC2 || paceSampleRate <= 0) return
        val target = (paceSampleRate / 5).coerceIn(4_800, 9_600)
        var primed = 0
        while (primed < target && playbackJob?.isActive == true) {
            val frames = generator.fill(scratch, 2048)
            if (frames <= 0) break
            var submitted = 0
            while (submitted < frames && playbackJob?.isActive == true) {
                val framesLeft = frames - submitted
                val sampleStart = submitted * channelCount
                val sampleCount = framesLeft * channelCount
                System.arraycopy(scratch, sampleStart, chunkScratch, 0, sampleCount)
                val written = AudioEngineRouter.writePlaybackFrames(chunkScratch, framesLeft, AudioBackend.UAC2)
                if (written <= 0) {
                    Thread.sleep(1)
                    continue
                }
                submitted += written
                primed += written
                submittedFrames += written
            }
        }
        if (primed > 0) {
            resetUac2PaceAnchor(submittedFrames)
        }
    }

    private fun resetUac2PaceAnchor(positionFrames: Long) {
        paceAnchorNs = System.nanoTime()
        paceStartFrames = positionFrames
    }

    private fun throttleUac2PlaybackIfAhead() {
        if (paceSampleRate <= 0) return
        val headroomFrames = (paceSampleRate / 10).coerceAtLeast(2_400).toLong()
        while (playbackJob?.isActive == true) {
            val elapsedNs = System.nanoTime() - paceAnchorNs
            val targetFrames = paceStartFrames + elapsedNs * paceSampleRate / 1_000_000_000L
            val ahead = submittedFrames - targetFrames
            if (ahead <= headroomFrames) return
            val sleepMs = ((ahead - headroomFrames) * 1_000L / paceSampleRate).coerceIn(1L, 50L)
            Thread.sleep(sleepMs)
        }
    }

    private fun stopNative() {
        val backend = activeBackend
        if (backend != null) {
            val underruns = AudioEngineRouter.playbackUnderrunFrames(backend)
            OmtLog.i("UsbTestTone", "stopped underruns=$underruns backend=$backend")
        }
        AudioEngineRouter.stopPlayback()
        activeBackend = null
        paceSampleRate = 0
        submittedFrames = 0L
    }
}
