package org.openmultitrack.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.domain.session.RecordingSession
import org.openmultitrack.sessionio.wav.WavWriter
import java.io.File

class SessionRecorder {
    private var writerJob: Job? = null
    private var outputFile: File? = null
    private var channelCount: Int = 2
    private var sampleRate: Int = 48_000

    @Volatile
    private var framesWritten: Long = 0

    fun start(
        scope: CoroutineScope,
        deviceId: Int,
        channels: Int,
        outputDir: File,
        sampleRateHz: Int = 48_000,
    ): Result<RecordingSession> {
        writerJob?.cancel()
        NativeAudioEngine.stopRecording()

        channelCount = channels.coerceIn(1, 32)
        sampleRate = sampleRateHz
        val file = File(outputDir, "session_${System.currentTimeMillis()}.wav")
        outputFile = file

        val status = NativeAudioEngine.startRecording(deviceId, channelCount, sampleRate)
        if (!status.active) {
            return Result.failure(IllegalStateException(status.errorMessage ?: "Recording failed"))
        }

        framesWritten = 0
        val wav = WavWriter(file, channelCount, sampleRate)
        val scratch = FloatArray(4096 * channelCount)

        writerJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val frames = NativeAudioEngine.readRecordedFrames(scratch, 4096)
                    if (frames > 0) {
                        wav.writeInterleavedFloat(scratch, frames)
                        framesWritten += frames
                    } else {
                        Thread.sleep(5)
                    }
                }
            } finally {
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
        writerJob?.cancelAndJoin()
        writerJob = null
        NativeAudioEngine.stopRecording()
        val file = outputFile ?: return null
        return RecordingSession(
            filePath = file.absolutePath,
            channelCount = channelCount,
            sampleRate = sampleRate,
            framesRecorded = framesWritten,
        )
    }
}
