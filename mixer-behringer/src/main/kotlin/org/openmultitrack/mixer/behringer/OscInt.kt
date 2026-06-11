package org.openmultitrack.mixer.behringer

import kotlin.math.roundToInt

/** Coerce XR18 OSC numeric arguments (int or float) to [Int]. */
fun oscInt(arg: Any?): Int? = when (arg) {
    is Int -> arg
    is Float -> arg.roundToInt()
    is Double -> arg.roundToInt()
    else -> null
}
