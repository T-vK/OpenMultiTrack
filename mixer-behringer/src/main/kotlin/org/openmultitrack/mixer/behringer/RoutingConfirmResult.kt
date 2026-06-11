package org.openmultitrack.mixer.behringer

/**
 * Result of querying the mixer after a routing write.
 *
 * "Confirm" means: send OSC set commands, then **separately** query
 * `/ch/NN/preamp/rtnsw`, `/config/insrc`, `/config/rtnsrc` and compare to the target.
 */
data class RoutingConfirmResult(
    val channelIndex: Int,
    val target: XAirChannelInputState,
    val live: XAirChannelInputState?,
    val replyPaths: Set<String> = emptySet(),
) {
    val confirmed: Boolean get() = live != null && live.matchesRouting(target)

    fun report(): String = buildString {
        val ch = channelIndex + 1
        append("CH").append(ch)
        append(" wanted=").append(target.describe())
        append(" (rtnsw=").append(target.rtnSw)
        append(" insrc=").append(target.insrc)
        append(" rtnsrc=").append(target.rtnsrc).append(')')
        append(" got=")
        if (live == null) {
            append("? (no OSC reply")
            if (replyPaths.isNotEmpty()) append(", partial paths=").append(replyPaths)
            append(')')
        } else {
            append(live.describe())
            append(" (rtnsw=").append(live.rtnSw)
            append(" insrc=").append(live.insrc)
            append(" rtnsrc=").append(live.rtnsrc).append(')')
        }
        append(" confirmed=").append(confirmed)
    }
}
