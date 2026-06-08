package org.openmultitrack.mixer.behringer

/**
 * Emoji stand-ins for Behringer / Mixing Station scribble icon ids (1–74).
 * Source: Patrick-Gilles Maillot icon set (see behringer-icons on GitHub).
 */
object MixingStationIcons {
    const val MAX_ID = 74

    private val EMOJI = arrayOf(
        "",       // 0 unused
        "🥁", "🥁", "🥁", "🪘", "🪘", // 1-5 kicks/snare
        "🥁", "🥁", "🥁", "🎩", "🔔", // 6-10 toms/hat/ride
        "🥁", "🔔", "🪘", "🪘", "🎵", // 11-15 percussion
        "🎹", "🎸", "🎸", "🎻", "🎸", // 16-20 bass/guitar
        "🎸", "🎸", "🎸", "🔊", "🎸", // 21-25 guitars/amps
        "📻", "🎹", "🎹", "🎹", "🎹", // 26-30 keys
        "🎹", "🎹", "🎹", "🎹", "🎺", // 31-35 synth/brass
        "🎺", "🎷", "🎷", "🎻", "🎻", // 36-40 brass/strings
        "🎤", "🎤", "👥", "✋", "🗣", // 41-45 vocals
        "🗣", "🎙", "🎙", "🎙", "🎤", // 46-50 mics
        "🎤", "🎤", "🎧", "🔌", "🔌", // 51-55 connectors
        "🔌", "🔌", "🔌", "🔌", "📼", // 56-60
        "✨", "💻", "🔊", "🔈", "🔉", // 61-65 fx/speakers
        "🔊", "🔊", "🎛", "🎚", "🎚", // 66-70
        "🎚", "🔀", "🔀", "😊", "", // 71-74
    )

    fun emoji(iconId: Int?): String? {
        if (iconId == null || iconId !in 1..MAX_ID) return null
        return EMOJI.getOrNull(iconId)?.takeIf { it.isNotEmpty() }
    }
}
