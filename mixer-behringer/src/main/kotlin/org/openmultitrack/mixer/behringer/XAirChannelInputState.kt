package org.openmultitrack.mixer.behringer

/** XR18/X-Air channel strip input routing (OSC read/write subset). */
data class XAirChannelInputState(
    /** `/ch/{nn}/config/insrc` — 0=OFF, 1–16=IN01–IN16 */
    val insrc: Int,
    /** `/ch/{nn}/config/rtnsrc` — 0–17=U01–U18 */
    val rtnsrc: Int,
    /** `/ch/{nn}/preamp/rtnsw` — 0=preamp/A-D, 1=USB return */
    val rtnSw: Int,
) {
    val usesUsbReturn: Boolean get() = rtnSw == 1

    /** True when live routing matches the intended record or soundcheck target. */
    fun matchesRouting(target: XAirChannelInputState): Boolean = when {
        target.usesUsbReturn -> usesUsbReturn && rtnsrc == target.rtnsrc
        else -> !usesUsbReturn && insrc == target.insrc
    }

    fun describe(): String = when {
        usesUsbReturn -> XAirInputSourceCatalog.rtnLabel(rtnsrc)
        insrc == 0 -> "OFF"
        else -> XAirInputSourceCatalog.insrcLabel(insrc)
    }
}
