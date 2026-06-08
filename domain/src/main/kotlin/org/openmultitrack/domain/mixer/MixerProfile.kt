package org.openmultitrack.domain.mixer

import org.openmultitrack.domain.channel.ChannelStripState

/** Persisted mixer the user added to the workspace. */
data class MixerProfile(
    val id: String,
    val usbDeviceName: String?,
    val vendorId: Int,
    val productId: Int,
    val serialNumber: String?,
    val productName: String?,
    val displayName: String,
    val oscHost: String? = null,
    val channelStrips: List<ChannelStripState> = emptyList(),
    val scribbleImported: Boolean = false,
) {
    fun storageFolderName(): String {
        val base = displayName.ifBlank { productName ?: "Mixer" }
            .replace(Regex("""[^\w\-]+"""), "_")
        val serial = serialNumber?.takeIf { it.isNotBlank() } ?: "unknown"
        return "$base-$serial"
    }
}
