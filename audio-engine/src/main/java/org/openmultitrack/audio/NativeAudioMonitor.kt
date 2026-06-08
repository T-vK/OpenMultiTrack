package org.openmultitrack.audio

/** JNI bridge for local Oboe monitor output (headphones / Bluetooth). */
object NativeAudioMonitor {
    init {
        System.loadLibrary("openmultitrack_audio")
    }

    fun start(deviceId: Int, channelCount: Int, sampleRate: Int = 48_000): NativeEngineStatus =
        nativeStart(deviceId, channelCount, sampleRate)

    fun stop() {
        nativeStop()
    }

    fun writeFrames(src: FloatArray, frameCount: Int): Int =
        nativeWriteFrames(src, frameCount)

    fun underrunFrames(): Long = nativeUnderrunFrames()

    private external fun nativeStart(deviceId: Int, channelCount: Int, sampleRate: Int): NativeEngineStatus

    private external fun nativeStop()

    private external fun nativeWriteFrames(src: FloatArray, frameCount: Int): Int

    private external fun nativeUnderrunFrames(): Long
}
