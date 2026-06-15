package org.openmultitrack.app.ui.daw

import androidx.annotation.DrawableRes
import org.openmultitrack.app.R
import org.openmultitrack.mixer.behringer.MixingStationIcons

/**
 * Drawable resources for Mixing Station / X-Air scribble icons (1–74).
 * Generated from [mamarguerat/behringer-icons](https://github.com/mamarguerat/behringer-icons) BMPs.
 */
object MixingStationIconResources {
    private val DRAWABLES = intArrayOf(
        0, // 0 unused
        0, // 1 blank
        R.drawable.ms_scribble_02,
        R.drawable.ms_scribble_03,
        R.drawable.ms_scribble_04,
        R.drawable.ms_scribble_05,
        R.drawable.ms_scribble_06,
        R.drawable.ms_scribble_07,
        R.drawable.ms_scribble_08,
        R.drawable.ms_scribble_09,
        R.drawable.ms_scribble_10,
        R.drawable.ms_scribble_11,
        R.drawable.ms_scribble_12,
        R.drawable.ms_scribble_13,
        R.drawable.ms_scribble_14,
        R.drawable.ms_scribble_15,
        R.drawable.ms_scribble_16,
        R.drawable.ms_scribble_17,
        R.drawable.ms_scribble_18,
        R.drawable.ms_scribble_19,
        R.drawable.ms_scribble_20,
        R.drawable.ms_scribble_21,
        R.drawable.ms_scribble_22,
        R.drawable.ms_scribble_23,
        R.drawable.ms_scribble_24,
        R.drawable.ms_scribble_25,
        R.drawable.ms_scribble_26,
        R.drawable.ms_scribble_27,
        R.drawable.ms_scribble_28,
        R.drawable.ms_scribble_29,
        R.drawable.ms_scribble_30,
        R.drawable.ms_scribble_31,
        R.drawable.ms_scribble_32,
        R.drawable.ms_scribble_33,
        R.drawable.ms_scribble_34,
        R.drawable.ms_scribble_35,
        R.drawable.ms_scribble_36,
        R.drawable.ms_scribble_37,
        R.drawable.ms_scribble_38,
        R.drawable.ms_scribble_39,
        R.drawable.ms_scribble_40,
        R.drawable.ms_scribble_41,
        R.drawable.ms_scribble_42,
        R.drawable.ms_scribble_43,
        R.drawable.ms_scribble_44,
        R.drawable.ms_scribble_45,
        R.drawable.ms_scribble_46,
        R.drawable.ms_scribble_47,
        R.drawable.ms_scribble_48,
        R.drawable.ms_scribble_49,
        R.drawable.ms_scribble_50,
        R.drawable.ms_scribble_51,
        R.drawable.ms_scribble_52,
        R.drawable.ms_scribble_53,
        R.drawable.ms_scribble_54,
        R.drawable.ms_scribble_55,
        R.drawable.ms_scribble_56,
        R.drawable.ms_scribble_57,
        R.drawable.ms_scribble_58,
        R.drawable.ms_scribble_59,
        R.drawable.ms_scribble_60,
        R.drawable.ms_scribble_61,
        R.drawable.ms_scribble_62,
        R.drawable.ms_scribble_63,
        R.drawable.ms_scribble_64,
        R.drawable.ms_scribble_65,
        R.drawable.ms_scribble_66,
        R.drawable.ms_scribble_67,
        R.drawable.ms_scribble_68,
        R.drawable.ms_scribble_69,
        R.drawable.ms_scribble_70,
        R.drawable.ms_scribble_71,
        R.drawable.ms_scribble_72,
        R.drawable.ms_scribble_73,
        R.drawable.ms_scribble_74,
    )

    @DrawableRes
    fun drawableRes(iconId: Int?): Int? {
        if (iconId == null || iconId !in 1..MixingStationIcons.MAX_ID) return null
        val res = DRAWABLES[iconId]
        return res.takeIf { it != 0 }
    }
}
