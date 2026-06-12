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
)

data class MixerHealthSnapshot(
    val mixerId: String,
    val updatedAtMs: Long,
    val overall: HealthLevel,
    val usb: UsbHealth,
    val issues: List<HealthIssue>,
) {
    val primaryIssue: HealthIssue? = issues.firstOrNull()
}
