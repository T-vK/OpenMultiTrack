package org.openmultitrack.app.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.mixer.behringer.UsbChannelScribble

class ScribbleStripCacheTest {
    @Test
    fun fingerprint_changesWhenLabelsChange() {
        val a = listOf(UsbChannelScribble(1, "Ch01", "Kick", 3, iconId = 50))
        val b = listOf(UsbChannelScribble(1, "Ch01", "Snare", 3, iconId = 50))
        assertThat(ScribbleStripCache.fingerprint(a)).isNotEqualTo(ScribbleStripCache.fingerprint(b))
    }

    @Test
    fun fingerprint_stableForSameLabels() {
        val labels = listOf(
            UsbChannelScribble(1, "Ch01", "Kick", 3, iconId = 50),
            UsbChannelScribble(2, "Ch02", "Snare", 5, iconId = 39),
        )
        assertThat(ScribbleStripCache.fingerprint(labels)).isEqualTo(ScribbleStripCache.fingerprint(labels))
    }
}
