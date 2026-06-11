package org.openmultitrack.domain.mixer

import org.openmultitrack.domain.channel.ChannelColors
import org.openmultitrack.domain.channel.ChannelStripState

/** Channel layout and synthetic tone parameters for the built-in Demo mixer. */
object DemoBandChannels {
    data class Spec(
        val label: String,
        val displayName: String,
        val iconId: Int,
        val frequencyHz: Double,
        val amplitude: Float = 0.35f,
        val colorArgb: Int? = null,
    )

    // Mixing Station / X-Air icon ids (see MixingStationIcons in mixer-behringer).
    private const val ICON_KICK = 3
    private const val ICON_SNARE = 4
    private const val ICON_TOM_HI = 6
    private const val ICON_TOM_MID = 7
    private const val ICON_FLOOR_TOM = 8
    private const val ICON_HIHAT = 9
    private const val ICON_BASS = 17
    private const val ICON_LEAD_GTR = 20
    private const val ICON_ACOUSTIC_GTR = 23
    private const val ICON_KEYBOARD = 30
    private const val ICON_MALE_VOX = 41
    private const val ICON_FEMALE_VOX = 42
    private const val ICON_CHOIR = 43
    private const val ICON_TAPE = 60
    private const val ICON_SPEAKER_R = 64
    private const val ICON_SPEAKER_L = 65

    val specs: List<Spec> = listOf(
        Spec("Lead Vox", "Lead Vox", ICON_MALE_VOX, 350.0, 0.38f),
        Spec("BGV 1", "BGV 1", ICON_CHOIR, 330.0, 0.28f),
        Spec("BGV 2", "BGV 2", ICON_FEMALE_VOX, 392.0, 0.26f),
        Spec("Lead Gtr", "Lead Gtr", ICON_LEAD_GTR, 220.0, 0.34f),
        Spec("Rhythm Gtr", "Rhythm Gtr", ICON_ACOUSTIC_GTR, 165.0, 0.30f),
        Spec("Bass", "Bass", ICON_BASS, 82.0, 0.42f),
        Spec("Keys", "Keys", ICON_KEYBOARD, 262.0, 0.32f),
        Spec("Kick", "Kick", ICON_KICK, 60.0, 0.48f),
        Spec("Snare", "Snare", ICON_SNARE, 185.0, 0.33f),
        Spec("Hi-Hat", "Hi-Hat", ICON_HIHAT, 8_000.0, 0.14f),
        Spec("Tom 1", "Tom 1", ICON_TOM_HI, 150.0, 0.28f),
        Spec("Tom 2", "Tom 2", ICON_TOM_MID, 120.0, 0.26f),
        Spec("Floor Tom", "Floor Tom", ICON_FLOOR_TOM, 90.0, 0.30f),
        Spec("OH L", "OH L", ICON_SPEAKER_L, 3_200.0, 0.20f),
        Spec("OH R", "OH R", ICON_SPEAKER_R, 3_350.0, 0.20f),
        Spec("Playback", "Playback", ICON_TAPE, 1_000.0, 0.25f),
    )

    fun channelStripStates(): List<ChannelStripState> =
        specs.mapIndexed { index, spec ->
            ChannelStripState(
                index = index,
                label = spec.label,
                displayName = spec.displayName,
                iconId = spec.iconId,
                colorArgb = spec.colorArgb ?: ChannelColors.defaultForIndex(index),
                armed = true,
                monitoring = true,
            )
        }
}
