package org.openmultitrack.mixer.behringer

/**
 * Emoji stand-ins for Behringer / Mixing Station scribble icon ids (1–74).
 * Numbering matches the X32 / X-Air icon list (see behringer-icons on GitHub).
 */
object MixingStationIcons {
    const val MAX_ID = 74

    const val ELECTRIC_BASS = 17
    const val ACOUSTIC_GUITAR = 23
    const val VIOLIN = 39
    const val HANDHELD_MIC = 50
    const val TAPE = 60
    const val PC = 62
    const val SPEAKER_RIGHT = 64
    const val SPEAKER_LEFT = 65

    private val EMOJI = arrayOf(
        "", // 0 unused
        "", // 1 blank
        "🥁", // 2 kick-back
        "🥁", // 3 kick-front
        "🪘", // 4 snare-top
        "🪘", // 5 snare-bottom
        "🥁", // 6 tom-high
        "🥁", // 7 tom-medium
        "🥁", // 8 floor tom
        "🎩", // 9 hi-hat
        "🔔", // 10 crash
        "🥁", // 11 drum kit
        "🔔", // 12 cowbell
        "🪘", // 13 bongos
        "🪘", // 14 congas
        "🎵", // 15 tambourine
        "🎵", // 16 vibraphone
        "🎸", // 17 electric bass
        "🎸", // 18 acoustic bass
        "🎸", // 19 contrabass
        "🎸", // 20 les paul
        "🎸", // 21 ibanez
        "🎸", // 22 washburn
        "🎸", // 23 acoustic guitar
        "🔊", // 24 bass amp
        "🔊", // 25 guitar amp
        "🔊", // 26 amp cabinet
        "🎹", // 27 piano
        "🎹", // 28 organ
        "🎹", // 29 harpsichord
        "🎹", // 30 keyboard
        "🎹", // 31 synthesizer 1
        "🎹", // 32 synthesizer 2
        "🎹", // 33 synthesizer 3
        "🎹", // 34 keytar
        "🎺", // 35 trumpet
        "🎺", // 36 trombone
        "🎷", // 37 saxophone
        "🎷", // 38 clarinet
        "🎻", // 39 violin
        "🎻", // 40 cello
        "🎤", // 41 male vocal
        "🎤", // 42 female vocal
        "👥", // 43 choir
        "✋", // 44 hand sign
        "🗣", // 45 talk a
        "🗣", // 46 talk b
        "🎙", // 47 large diaphragm mic
        "🎙", // 48 condenser mic left
        "🎙", // 49 condenser mic right
        "🎤", // 50 handheld mic (SM58)
        "🎤", // 51 wireless mic
        "🎤", // 52 podium mic
        "🎧", // 53 headset / in-ear
        "🔌", // 54 xlr
        "🔌", // 55 trs
        "🔌", // 56 trs left
        "🔌", // 57 trs right
        "🔌", // 58 rca left
        "🔌", // 59 rca right
        "📼", // 60 tape
        "✨", // 61 fx
        "💻", // 62 computer
        "🔊", // 63 wedge
        "🔈", // 64 speaker right
        "🔉", // 65 speaker left
        "🔊", // 66 speaker array
        "🔊", // 67 speaker on pole
        "🎛", // 68 amp rack
        "🎛", // 69 controls
        "🎚", // 70 fader
        "🔀", // 71 mix bus
        "🔀", // 72 matrix
        "🔀", // 73 routing
        "😊", // 74 smiley
    )

    fun emoji(iconId: Int?): String? {
        if (iconId == null || iconId !in 1..MAX_ID) return null
        return EMOJI.getOrNull(iconId)?.takeIf { it.isNotEmpty() }
    }
}
