package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MixingStationIconsTest {
    @Test
    fun drumsUseAbbreviationsNotTopHatForHiHat() {
        assertThat(MixingStationIcons.display(9)?.text).isEqualTo("HH")
        assertThat(MixingStationIcons.display(9)?.style).isEqualTo(MixingStationIcons.GlyphStyle.ABBREV)
        assertThat(MixingStationIcons.emoji(9)).isNull()
    }

    @Test
    fun kickAndTomsAreDistinctAbbreviations() {
        assertThat(MixingStationIcons.display(3)?.text).isEqualTo("BD")
        assertThat(MixingStationIcons.display(4)?.text).isEqualTo("SN")
        assertThat(MixingStationIcons.display(6)?.text).isEqualTo("T1")
        assertThat(MixingStationIcons.display(8)?.text).isEqualTo("FT")
    }

    @Test
    fun connectorsUseJackLabelsNotPowerPlug() {
        assertThat(MixingStationIcons.display(54)?.text).isEqualTo("XLR")
        assertThat(MixingStationIcons.display(55)?.text).isEqualTo("TRS")
        assertThat(MixingStationIcons.display(58)?.text).isEqualTo("C-L")
        assertThat(MixingStationIcons.emoji(54)).isNull()
    }

    @Test
    fun handheldMicStillUsesEmoji() {
        val glyph = MixingStationIcons.display(MixingStationIcons.HANDHELD_MIC)
        assertThat(glyph?.style).isEqualTo(MixingStationIcons.GlyphStyle.EMOJI)
        assertThat(glyph?.text).isEqualTo("🎤")
    }
}
