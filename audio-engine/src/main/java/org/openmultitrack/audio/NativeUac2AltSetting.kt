package org.openmultitrack.audio

/** JNI mirror of a UAC2 audio streaming alternate setting. */
data class NativeUac2AltSetting(
    val interfaceNumber: Int,
    val alternateSetting: Int,
    val endpointAddress: Int,
    val maxPacketSize: Int,
    val isInput: Boolean,
    val channels: Int,
    val sampleRateHz: Int,
    val bitResolution: Int,
    val subframeBytes: Int,
    val formatValid: Boolean,
) {
    val maxChannels: Int get() = if (formatValid) channels else 0
}
