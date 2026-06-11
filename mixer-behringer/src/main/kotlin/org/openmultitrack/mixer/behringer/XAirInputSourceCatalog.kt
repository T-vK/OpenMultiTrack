package org.openmultitrack.mixer.behringer

/** Human-readable labels for X-Air input routing OSC indices. */
object XAirInputSourceCatalog {
    const val CHANNEL_COUNT = 16

    fun isRoutable(channelIndex: Int): Boolean = channelIndex in 0 until CHANNEL_COUNT

    fun routableIndices(indices: Iterable<Int>): Set<Int> =
        indices.filter { isRoutable(it) }.toSet()

    fun insrcLabel(value: Int): String = when (value) {
        0 -> "OFF"
        in 1..16 -> "IN%02d".format(value)
        else -> "IN?$value"
    }

    fun rtnLabel(value: Int): String = when (value) {
        in 0..17 -> "U%02d".format(value + 1)
        else -> "U?$value"
    }

    fun rtnSwLabel(value: Int): String = when (value) {
        0 -> "A/D"
        1 -> "USB"
        else -> "?$value"
    }

    /** Record routing target for logical channel index 0..15. */
    fun recordTarget(channelIndex: Int): XAirChannelInputState {
        val ch = channelIndex.coerceIn(0, CHANNEL_COUNT - 1)
        return XAirChannelInputState(
            insrc = ch + 1,
            rtnsrc = ch,
            rtnSw = 0,
        )
    }

    /** Soundcheck routing target for logical channel index 0..15. */
    fun soundcheckTarget(channelIndex: Int): XAirChannelInputState {
        val ch = channelIndex.coerceIn(0, CHANNEL_COUNT - 1)
        return XAirChannelInputState(
            insrc = ch + 1,
            rtnsrc = ch,
            rtnSw = 1,
        )
    }
}
