package org.openmultitrack.domain.remote

object RemoteProtocol {
    const val VERSION = 1
    const val HTTP_PORT = 8765
    const val DISCOVERY_PORT = 8766
    const val WS_PATH = "/api/v1/ws"
    const val DISCOVER_REQUEST = "OMT_DISCOVER"
    const val DISCOVER_REPLY = "OMT_ANNOUNCE"
    const val DELTA_INTERVAL_MS = 50L
    const val LIVE_WAVEFORM_TAIL = 64
    const val MAX_WAVEFORM_POINTS = 400
}
