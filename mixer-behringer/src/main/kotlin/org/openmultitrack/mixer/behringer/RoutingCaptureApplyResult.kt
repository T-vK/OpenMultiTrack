package org.openmultitrack.mixer.behringer

/** Result of a single OSC session: probe, scoped read, optional batched apply. */
data class RoutingCaptureApplyResult(
    val reachable: Boolean,
    val liveByChannel: Map<Int, XAirChannelInputState>,
    val applied: Boolean,
)
