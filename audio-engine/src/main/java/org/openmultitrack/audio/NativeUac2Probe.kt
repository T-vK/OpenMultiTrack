package org.openmultitrack.audio

/** JNI bridge for UAC2 configuration descriptor parsing (Phase 1 — no streaming). */
object NativeUac2Probe {
    init {
        System.loadLibrary("openmultitrack_audio")
    }

    fun parseConfigDescriptor(raw: ByteArray): NativeUac2DeviceCaps? {
        if (raw.isEmpty()) return null
        return nativeParseConfigDescriptor(raw)
    }

    fun selectBestCaptureAlt(
        caps: NativeUac2DeviceCaps,
        minChannels: Int,
        sampleRateHz: Int = 48_000,
    ): NativeUac2AltSetting? = nativeSelectBestAlt(caps.captureAlts, minChannels, sampleRateHz)

    fun selectBestPlaybackAlt(
        caps: NativeUac2DeviceCaps,
        minChannels: Int,
        sampleRateHz: Int = 48_000,
    ): NativeUac2AltSetting? = nativeSelectBestAlt(caps.playbackAlts, minChannels, sampleRateHz)

    private external fun nativeParseConfigDescriptor(raw: ByteArray): NativeUac2DeviceCaps?

    private external fun nativeSelectBestAlt(
        alts: Array<NativeUac2AltSetting>,
        minChannels: Int,
        sampleRateHz: Int,
    ): NativeUac2AltSetting?
}
