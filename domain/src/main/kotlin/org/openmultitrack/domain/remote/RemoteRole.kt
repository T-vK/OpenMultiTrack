package org.openmultitrack.domain.remote

enum class RemoteRole {
    OFF,
    HOST,
    CLIENT,
}

enum class RemoteConnectionState {
    DISCONNECTED,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    ERROR,
}
