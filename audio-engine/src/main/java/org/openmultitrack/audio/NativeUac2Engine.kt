package org.openmultitrack.audio

/** JNI bridge for UAC2 isochronous capture/playback via usbdevfs. */
object NativeUac2Engine {
    init {
        System.loadLibrary("openmultitrack_audio")
    }

    fun startCapture(
        usbFd: Int,
        alt: NativeUac2AltSetting,
        javaInterfaceClaimed: Boolean = false,
    ): NativeEngineStatus = nativeStartCapture(usbFd, alt, javaInterfaceClaimed)

    fun startIfbFeederCapture(
        usbFd: Int,
        alt: NativeUac2AltSetting,
        javaInterfaceClaimed: Boolean = false,
    ): NativeEngineStatus = nativeStartIfbFeederCapture(usbFd, alt, javaInterfaceClaimed)

    fun stopCapture() {
        nativeStopCapture()
    }

    fun readCapturedFrames(dest: FloatArray, maxFrames: Int): Int =
        nativeReadCapturedFrames(dest, maxFrames)

    fun readCapturedPcmBytes(dest: ByteArray, maxFrames: Int): Int =
        nativeReadCapturedPcmBytes(dest, maxFrames)

    fun captureBytesPerFrame(): Int = nativeCaptureBytesPerFrame()

    fun captureDroppedFrames(): Long = nativeCaptureDroppedFrames()

    fun startPcmFileRecording(path: String, resetCaptureRing: Boolean = true): Boolean =
        nativeStartPcmFileRecording(path, resetCaptureRing)

    fun switchPcmFileRecording(path: String): Boolean = nativeSwitchPcmFileRecording(path)

    fun isPcmFileRecording(): Boolean = nativeIsPcmFileRecording()

    fun stopPcmFileRecording() {
        nativeStopPcmFileRecording()
    }

    fun pcmFileFramesWritten(): Long = nativePcmFileFramesWritten()

    fun isCaptureRunning(): Boolean = nativeIsCaptureRunning()

    fun startPlayback(
        usbFd: Int,
        alt: NativeUac2AltSetting,
        javaInterfaceClaimed: Boolean = false,
    ): NativeEngineStatus = nativeStartPlayback(usbFd, alt, javaInterfaceClaimed)

    fun stopPlayback() {
        nativeStopPlayback()
    }

    /** Release userspace playback claim and reattach snd-usb-audio for Android/Oboe. */
    fun restorePlaybackInterfaceToAndroid(usbFd: Int, interfaceNumber: Int): Boolean =
        nativeRestorePlaybackInterfaceToAndroid(usbFd, interfaceNumber)

    fun setPlaybackOutputHold(hold: Boolean) {
        nativeSetPlaybackOutputHold(hold)
    }

    fun writePlaybackFrames(src: FloatArray, frameCount: Int): Int =
        nativeWritePlaybackFrames(src, frameCount)

    fun playbackUnderrunFrames(): Long = nativePlaybackUnderrunFrames()

    fun isPlaybackRunning(): Boolean = nativeIsPlaybackRunning()

    private external fun nativeIsPlaybackRunning(): Boolean

    private external fun nativeStartCapture(
        usbFd: Int,
        alt: NativeUac2AltSetting,
        javaInterfaceClaimed: Boolean,
    ): NativeEngineStatus

    private external fun nativeStartIfbFeederCapture(
        usbFd: Int,
        alt: NativeUac2AltSetting,
        javaInterfaceClaimed: Boolean,
    ): NativeEngineStatus

    private external fun nativeStopCapture()

    private external fun nativeReadCapturedFrames(dest: FloatArray, maxFrames: Int): Int

    private external fun nativeReadCapturedPcmBytes(dest: ByteArray, maxFrames: Int): Int

    private external fun nativeCaptureBytesPerFrame(): Int

    private external fun nativeCaptureDroppedFrames(): Long

    private external fun nativeStartPcmFileRecording(path: String, resetCaptureRing: Boolean): Boolean

    private external fun nativeSwitchPcmFileRecording(path: String): Boolean

    private external fun nativeIsPcmFileRecording(): Boolean

    private external fun nativeStopPcmFileRecording()

    private external fun nativePcmFileFramesWritten(): Long

    private external fun nativeIsCaptureRunning(): Boolean

    private external fun nativeStartPlayback(
        usbFd: Int,
        alt: NativeUac2AltSetting,
        javaInterfaceClaimed: Boolean,
    ): NativeEngineStatus

    private external fun nativeStopPlayback()

    private external fun nativeRestorePlaybackInterfaceToAndroid(
        usbFd: Int,
        interfaceNumber: Int,
    ): Boolean

    private external fun nativeSetPlaybackOutputHold(hold: Boolean)

    private external fun nativeWritePlaybackFrames(src: FloatArray, frameCount: Int): Int

    private external fun nativePlaybackUnderrunFrames(): Long
}
