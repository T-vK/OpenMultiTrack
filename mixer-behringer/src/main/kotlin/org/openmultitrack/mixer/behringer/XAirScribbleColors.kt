package org.openmultitrack.mixer.behringer

/** X-Air / X32 scribble color index (0–15) to display ARGB. */
object XAirScribbleColors {
    private val palette = intArrayOf(
        0xFF757575.toInt(), // OFF
        0xFFE53935.toInt(), // RD
        0xFF43A047.toInt(), // GN
        0xFFFDD835.toInt(), // YE
        0xFF1E88E5.toInt(), // BL
        0xFF8E24AA.toInt(), // MG
        0xFF00ACC1.toInt(), // CY
        0xFFFAFAFA.toInt(), // WH
        0xFF424242.toInt(), // OFFi
        0xFFC62828.toInt(), // RDi
        0xFF2E7D32.toInt(), // GNi
        0xFFF9A825.toInt(), // YEi
        0xFF1565C0.toInt(), // BLi
        0xFF6A1B9A.toInt(), // MGi
        0xFF00838F.toInt(), // CYi
        0xFFE0E0E0.toInt(), // WHi
    )

    fun toArgb(index: Int?): Int? {
        if (index == null || index < 0) return null
        return palette.getOrElse(index) { palette[index % palette.size] }
    }
}
