package org.openmultitrack.audio

/** JNI bridge for UAC2 isochronous capture/playback via usbdevfs. */
object NativeUac2Engine {
    init {
        System.loadLibrary("openmultitrack_audio")
    }

    fun startCapture(usbFd: Int, alt: NativeUac2AltSetting): NativeEngineStatus =
        nativeStartCapture(usbFd, alt)

    fun stopCapture() {
        nativeStopCapture()
    }

    fun readCapturedFrames(dest: FloatArray, maxFrames: Int): Int =
        nativeReadCapturedFrames(dest, maxFrames)

    fun captureDroppedFrames(): Long = nativeCaptureDroppedFrames()

    fun startPlayback(usbFd: Int, alt: NativeUac2AltSetting): NativeEngineStatus =
        nativeStartPlayback(usbFd, alt)

    fun stopPlayback() {
        nativeStopPlayback()
    }

    fun writePlaybackFrames(src: FloatArray, frameCount: Int): Int =
        nativeWritePlaybackFrames(src, frameCount)

    fun playbackUnderrunFrames(): Long = nativePlaybackUnderrunFrames()

    private external fun nativeStartCapture(usbFd: Int, alt: NativeUac2AltSetting): NativeEngineStatus

    private external fun nativeStopCapture()

    private external fun nativeReadCapturedFrames(dest: FloatArray, maxFrames: Int): Int

    private external fun nativeCaptureDroppedFrames(): Long

    private external fun nativeStartPlayback(usbFd: Int, alt: NativeUac2AltSetting): NativeEngineStatus

    private external fun nativeStopPlayback()

    private external fun nativeWritePlaybackFrames(src: FloatArray, frameCount: Int): Int

    private external fun nativePlaybackUnderrunFrames(): Long
}
