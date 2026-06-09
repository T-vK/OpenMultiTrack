package org.openmultitrack.domain.channel

/** UI + recording state for one input channel (0-based index). */
data class ChannelStripState(
    val index: Int,
    /** Raw scribble name from mixer (may include `_iconId` suffix). */
    val label: String = "",
    val displayName: String = "",
    val iconId: Int? = null,
    val colorArgb: Int = ChannelColors.defaultForIndex(index),
    val armed: Boolean = true,
    val monitoring: Boolean = true,
    val solo: Boolean = false,
    val muted: Boolean = false,
)

object ChannelColors {
    private val palette = intArrayOf(
        0xFFE57373.toInt(), 0xFF81C784.toInt(), 0xFF64B5F6.toInt(), 0xFFFFB74D.toInt(),
        0xFFBA68C8.toInt(), 0xFF4DD0E1.toInt(), 0xFFA1887F.toInt(), 0xFF90A4AE.toInt(),
        0xFFF06292.toInt(), 0xFF7986CB.toInt(), 0xFF4DB6AC.toInt(), 0xFFFFD54F.toInt(),
        0xFF9575CD.toInt(), 0xFF4FC3F7.toInt(), 0xFFAED581.toInt(), 0xFFFF8A65.toInt(),
        0xFFCE93D8.toInt(), 0xFF80DEEA.toInt(),
    )

    fun defaultForIndex(index: Int): Int = palette[index % palette.size]
}
