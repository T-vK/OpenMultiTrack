package org.openmultitrack.domain.mixer

enum class HealthLevel {
    OK,
    DEGRADED,
    BLOCKED,
    UNKNOWN,
}

enum class ProbeState {
    NONE,
    PROBING,
    OK,
    FAILED,
}

data class HealthIssue(
    val code: String,
    val severity: HealthLevel,
    val title: String,
    val detail: String,
)

data class UsbHealth(
    val attached: Boolean,
    val permissionGranted: Boolean,
    val probeState: ProbeState,
    val probeSummary: String?,
    val deviceName: String? = null,
    val stableId: String? = null,
)

/** LAN OSC desk status (X-Air family). Null when mixer has no OSC host configured. */
data class OscHealth(
    val supported: Boolean,
    val host: String?,
    val configured: Boolean,
)

/** Live USB audio transport state from the session controller. */
data class AudioTransportHealth(
    val captureChannels: Int,
    val playbackChannels: Int,
    val isRecording: Boolean,
    val isPlaying: Boolean,
    val isMonitoring: Boolean,
    val isUsbDegraded: Boolean,
    val activityLabel: String? = null,
)

data class MixerHealthSnapshot(
    val mixerId: String,
    val updatedAtMs: Long,
    val overall: HealthLevel,
    val usb: UsbHealth,
    val osc: OscHealth?,
    val audio: AudioTransportHealth?,
    val issues: List<HealthIssue>,
) {
    val primaryIssue: HealthIssue? = issues.firstOrNull()
}
