package org.openmultitrack.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openmultitrack.audio.OmtLog
import org.openmultitrack.domain.audio.AudioConstants
import org.openmultitrack.domain.session.RecordingSession
import org.openmultitrack.sessionio.wav.WavWriter
import android.hardware.usb.UsbDevice
import org.openmultitrack.usb.AudioBackend
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.CaptureRoute
import java.io.File
import kotlin.math.max
import kotlin.math.min

class SessionRecorder {
    private var writerJob: Job? = null
    private var outputFile: File? = null
    private var channelCount: Int = 2
    private var sampleRate: Int = AudioConstants.DEFAULT_SAMPLE_RATE
    private var activeBackend: AudioBackend? = null

    @Volatile
    private var framesWritten: Long = 0
    @Volatile
    private var droppedFrames: Long = 0

    fun start(
        scope: CoroutineScope,
        route: CaptureRoute,
        outputDir: File,
        usbDevice: UsbDevice? = null,
    ): Result<RecordingSession> {
        writerJob?.cancel()
        AudioEngineRouter.stopRecording()
        activeBackend = route.backend

        OmtLog.i(
            "Recorder",
            "start backend=${route.backend} channels=${route.channelCount} sampleRate=${route.sampleRate}",
        )
        val status = AudioEngineRouter.startRecording(route, usbDevice)
        if (!status.active) {
            OmtLog.e("Recorder", "native start failed: ${status.errorMessage}")
            activeBackend = null
            return Result.failure(IllegalStateException(status.errorMessage ?: "Recording failed"))
        }

        channelCount = status.channelCount.coerceIn(AudioConstants.MIN_CHANNELS, AudioConstants.MAX_CHANNELS)
        sampleRate = status.sampleRate
        OmtLog.i("Recorder", "native running ${channelCount}ch @ ${sampleRate}Hz (${route.backend})")

        val file = File(
            outputDir,
            "session_${channelCount}ch_${sampleRate}hz_${System.currentTimeMillis()}.wav",
        )
        outputFile = file
        framesWritten = 0
        droppedFrames = 0

        val wav = WavWriter(file, channelCount, sampleRate)
        val framesPerChunk = chunkFramesForChannels(channelCount)
        val scratch = FloatArray(framesPerChunk * channelCount)
        val backend = route.backend

        writerJob = scope.launch(Dispatchers.IO) {
            try {
                var lastLogFrames = 0L
                while (isActive) {
                    val frames = AudioEngineRouter.readRecordedFrames(scratch, framesPerChunk, backend)
                    if (frames > 0) {
                        wav.writeInterleavedFloat(scratch, frames)
                        framesWritten += frames
                        droppedFrames = AudioEngineRouter.recordingDroppedFrames(backend)
                        if (framesWritten - lastLogFrames >= sampleRate) {
                            OmtLog.d(
                                "Recorder",
                                "written $framesWritten frames, dropped=$droppedFrames",
                            )
                            lastLogFrames = framesWritten
                        }
                    } else {
                        Thread.sleep(2)
                    }
                }
            } catch (e: Exception) {
                OmtLog.e("Recorder", "writer loop failed", e)
                throw e
            } finally {
                OmtLog.i("Recorder", "writer closing file=${file.absolutePath} frames=$framesWritten")
                wav.close()
            }
        }

        return Result.success(
            RecordingSession(
                filePath = file.absolutePath,
                channelCount = channelCount,
                sampleRate = sampleRate,
                framesRecorded = 0,
            ),
        )
    }

    suspend fun stop(): RecordingSession? {
        OmtLog.i("Recorder", "stop requested framesWritten=$framesWritten")
        writerJob?.cancelAndJoin()
        writerJob = null
        AudioEngineRouter.stopRecording()
        activeBackend = null
        val file = outputFile ?: return null
        OmtLog.i(
            "Recorder",
            "stopped path=${file.absolutePath} frames=$framesWritten dropped=$droppedFrames",
        )
        return RecordingSession(
            filePath = file.absolutePath,
            channelCount = channelCount,
            sampleRate = sampleRate,
            framesRecorded = framesWritten,
        )
    }

    fun droppedFrameCount(): Long = droppedFrames

    companion object {
        /** Keep writer buffer under ~512 KiB of floats regardless of channel count. */
        fun chunkFramesForChannels(channelCount: Int): Int {
            val targetSamples = 131_072
            return max(256, min(4096, targetSamples / max(1, channelCount)))
        }
    }
}
