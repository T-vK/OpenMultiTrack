package org.openmultitrack.audio

/** JNI bridge for Oboe record/playback engines. */
object NativeAudioEngine {
    init {
        System.loadLibrary("openmultitrack_audio")
    }

    fun startRecording(deviceId: Int, channelCount: Int, sampleRate: Int = 48_000): NativeEngineStatus =
        nativeStartRecording(deviceId, channelCount, sampleRate)

    fun stopRecording() {
        nativeStopRecording()
    }

    fun readRecordedFrames(dest: FloatArray, maxFrames: Int): Int =
        nativeReadRecordedFrames(dest, maxFrames)

    fun recordingDroppedFrames(): Long = nativeRecordingDroppedFrames()

    fun startPlayback(deviceId: Int, channelCount: Int, sampleRate: Int = 48_000): NativeEngineStatus =
        nativeStartPlayback(deviceId, channelCount, sampleRate)

    fun stopPlayback() {
        nativeStopPlayback()
    }

    fun writePlaybackFrames(src: FloatArray, frameCount: Int): Int =
        nativeWritePlaybackFrames(src, frameCount)

    fun playbackUnderrunFrames(): Long = nativePlaybackUnderrunFrames()

    private external fun nativeStartRecording(
        deviceId: Int,
        channelCount: Int,
        sampleRate: Int,
    ): NativeEngineStatus

    private external fun nativeStopRecording()

    private external fun nativeReadRecordedFrames(dest: FloatArray, maxFrames: Int): Int

    private external fun nativeRecordingDroppedFrames(): Long

    private external fun nativeStartPlayback(
        deviceId: Int,
        channelCount: Int,
        sampleRate: Int,
    ): NativeEngineStatus

    private external fun nativeStopPlayback()

    private external fun nativeWritePlaybackFrames(src: FloatArray, frameCount: Int): Int

    private external fun nativePlaybackUnderrunFrames(): Long
}
