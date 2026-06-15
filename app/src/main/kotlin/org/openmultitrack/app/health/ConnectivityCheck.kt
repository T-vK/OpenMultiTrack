package org.openmultitrack.app.health

import org.openmultitrack.domain.mixer.HealthLevel

enum class ConnectivityStatus {
    OK,
    WARNING,
    ERROR,
    OFF,
    PENDING,
    UNKNOWN,
    NOT_APPLICABLE,
}

enum class ConnectivityGroup {
    USB,
    NETWORK,
    BLUETOOTH,
    SYNC,
    AUDIO,
    PERMISSIONS,
    REMOTE,
}

enum class ConnectivityAction {
    GRANT_USB,
    SET_OSC_IP,
    SYNC_LABELS,
    REFRESH_SNAPSHOTS,
    GRANT_BLUETOOTH,
    GRANT_LOCATION,
    ENABLE_BLUETOOTH,
    GRANT_MIC,
    GRANT_NOTIFICATIONS,
    CHOOSE_STORAGE,
    BATTERY_SETTINGS,
    OPEN_REMOTE,
    REFRESH,
}

data class ConnectivityCheckItem(
    val id: String,
    val group: ConnectivityGroup,
    val label: String,
    val status: ConnectivityStatus,
    val detail: String? = null,
    val technicalDetail: String? = null,
    val action: ConnectivityAction? = null,
    val actionLabel: String? = null,
)

data class ConnectivityChecklist(
    val mixerName: String,
    val overall: HealthLevel,
    val updatedAtMs: Long,
    val sections: List<ConnectivitySection>,
)

data class ConnectivitySection(
    val group: ConnectivityGroup,
    val title: String,
    val items: List<ConnectivityCheckItem>,
)
