package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScribbleStripLabelTest {
    @Test
    fun parse_stripsIconSuffix() {
        val parsed = ScribbleStripLabel.parse("E-Bass_17")
        assertThat(parsed.displayName).isEqualTo("E-Bass")
        assertThat(parsed.iconId).isEqualTo(17)
    }

    @Test
    fun parse_keepsStereoSideOutsideIcon() {
        val parsed = ScribbleStripLabel.parse("Playback_55 (L)")
        assertThat(parsed.displayName).isEqualTo("Playback")
        assertThat(parsed.iconId).isEqualTo(55)
    }

    @Test
    fun parse_plainName() {
        val parsed = ScribbleStripLabel.parse("Kick")
        assertThat(parsed.displayName).isEqualTo("Kick")
        assertThat(parsed.iconId).isNull()
    }
}
