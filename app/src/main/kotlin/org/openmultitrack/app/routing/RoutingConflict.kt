package org.openmultitrack.app.routing

import org.openmultitrack.mixer.behringer.XAirChannelInputState

data class RoutingChannelConflict(
    val channelIndex: Int,
    val baseline: XAirChannelInputState,
    val overrideApplied: XAirChannelInputState,
    val live: XAirChannelInputState,
)
