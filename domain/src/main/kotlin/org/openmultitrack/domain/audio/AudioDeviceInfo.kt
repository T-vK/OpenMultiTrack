package org.openmultitrack.domain.audio

/** Result of probing a USB (or default) audio endpoint via Oboe/AAudio. */
data class AudioEndpointProbe(
    val deviceId: Int,
    val direction: AudioDirection,
    val channelCount: Int,
    val sampleRate: Int,
    val framesPerBurst: Int,
    val errorMessage: String? = null,
) {
    val isSuccess: Boolean get() = errorMessage == null && channelCount > 0
}

enum class AudioDirection {
    INPUT,
    OUTPUT,
}

data class UsbAudioDeviceDescriptor(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String?,
    val productName: String?,
    val serialNumber: String?,
    val isLikelyBehringerMixer: Boolean,
    val guessedModel: String?,
    val androidAudioDeviceId: Int?,
)
