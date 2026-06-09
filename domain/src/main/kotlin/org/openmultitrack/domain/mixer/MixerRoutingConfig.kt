package org.openmultitrack.domain.mixer

/** Per-mixer USB channel mapping and visibility overrides. */
data class MixerRoutingConfig(
    /** Logical strip index → USB capture input index (0-based). Empty = identity. */
    val inputMap: Map<Int, Int> = emptyMap(),
    /** Logical strip index → USB playback output index (0-based). Empty = identity. */
    val outputMap: Map<Int, Int> = emptyMap(),
    /** Logical channels hidden in multitrack record mode. */
    val hiddenRecord: Set<Int> = emptySet(),
    /** Logical channels hidden in virtual soundcheck mode. */
    val hiddenSoundcheck: Set<Int> = emptySet(),
) {
    fun inputSource(logicalIndex: Int): Int = inputMap[logicalIndex] ?: logicalIndex

    fun outputTarget(logicalIndex: Int): Int = outputMap[logicalIndex] ?: logicalIndex

    fun isHidden(logicalIndex: Int, soundcheckMode: Boolean): Boolean =
        if (soundcheckMode) logicalIndex in hiddenSoundcheck else logicalIndex in hiddenRecord
}
