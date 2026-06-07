package org.openmultitrack.audio

/** Parsed USB Audio Class capabilities from the configuration descriptor. */
data class NativeUac2DeviceCaps(
    val uacVersion: Int,
    val captureAlts: Array<NativeUac2AltSetting>,
    val playbackAlts: Array<NativeUac2AltSetting>,
    val parseOk: Boolean,
) {
    val maxCaptureChannels: Int
        get() = captureAlts.maxOfOrNull { it.maxChannels } ?: 0

    val maxPlaybackChannels: Int
        get() = playbackAlts.maxOfOrNull { it.maxChannels } ?: 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NativeUac2DeviceCaps
        return uacVersion == other.uacVersion &&
            captureAlts.contentEquals(other.captureAlts) &&
            playbackAlts.contentEquals(other.playbackAlts) &&
            parseOk == other.parseOk
    }

    override fun hashCode(): Int {
        var result = uacVersion
        result = 31 * result + captureAlts.contentHashCode()
        result = 31 * result + playbackAlts.contentHashCode()
        result = 31 * result + parseOk.hashCode()
        return result
    }
}
