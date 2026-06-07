package org.openmultitrack.app.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.openmultitrack.audio.NativeAudioEngine
import org.openmultitrack.domain.audio.AudioConstants
import org.openmultitrack.domain.session.RecordingSession
import org.openmultitrack.sessionio.wav.WavWriter
import java.io.File
import kotlin.math.max
import kotlin.math.min

class SessionRecorder {
    private var writerJob: Job? = null
    private var outputFile: File? = null
    private var channelCount: Int = 2
    private var sampleRate: Int = AudioConstants.DEFAULT_SAMPLE_RATE

    @Volatile
    private var framesWritten: Long = 0
    @Volatile
    private var droppedFrames: Long = 0

    fun start(
        scope: CoroutineScope,
        deviceId: Int,
        channels: Int,
        outputDir: File,
        sampleRateHz: Int = AudioConstants.DEFAULT_SAMPLE_RATE,
    ): Result<RecordingSession> {
        writerJob?.cancel()
        NativeAudioEngine.stopRecording()

        val requestedChannels = channels.coerceIn(AudioConstants.MIN_CHANNELS, AudioConstants.MAX_CHANNELS)
        sampleRate = sampleRateHz

        val status = NativeAudioEngine.startRecording(deviceId, requestedChannels, sampleRate)
        if (!status.active) {
            return Result.failure(IllegalStateException(status.errorMessage ?: "Recording failed"))
        }

        channelCount = status.channelCount.coerceIn(AudioConstants.MIN_CHANNELS, AudioConstants.MAX_CHANNELS)
        sampleRate = status.sampleRate

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

        writerJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val frames = NativeAudioEngine.readRecordedFrames(scratch, framesPerChunk)
                    if (frames > 0) {
                        wav.writeInterleavedFloat(scratch, frames)
                        framesWritten += frames
                        droppedFrames = NativeAudioEngine.recordingDroppedFrames()
                    } else {
                        Thread.sleep(2)
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

    fun droppedFrameCount(): Long = droppedFrames

    companion object {
        /** Keep writer buffer under ~512 KiB of floats regardless of channel count. */
        fun chunkFramesForChannels(channelCount: Int): Int {
            val targetSamples = 131_072
            return max(256, min(4096, targetSamples / max(1, channelCount)))
        }
    }
}
