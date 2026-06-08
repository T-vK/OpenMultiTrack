package org.openmultitrack.mixer.behringer

/**
 * Parses Mixing Station / X-Air scribble names such as `E-Bass_17` or `Playback_55 (L)`.
 * The `_XX` suffix is a 1-based icon id (see [MixingStationIcons]).
 */
data class ScribbleStripLabel(
    val raw: String,
    val displayName: String,
    val iconId: Int?,
) {
    companion object {
        private val stereoSuffix = Regex("""\s+\([LR]\)$""")
        private val iconSuffix = Regex("""_(\d{1,2})$""")

        fun parse(raw: String?): ScribbleStripLabel {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return ScribbleStripLabel("", "", null)

            val withoutStereo = stereoSuffix.find(text)?.let { m ->
                text.removeSuffix(m.value).trimEnd()
            } ?: text

            val match = iconSuffix.find(withoutStereo)
            if (match == null) {
                return ScribbleStripLabel(text, withoutStereo, null)
            }

            val id = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..MixingStationIcons.MAX_ID }
            val name = withoutStereo.removeSuffix(match.value).trimEnd()
            return ScribbleStripLabel(text, name.ifEmpty { withoutStereo }, id)
        }
    }
}
