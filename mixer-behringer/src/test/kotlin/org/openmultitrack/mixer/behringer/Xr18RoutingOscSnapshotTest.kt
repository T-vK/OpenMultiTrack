package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Xr18RoutingOscSnapshotTest {
    @Test
    fun snapSlotName_usesTwoDigitSlot() {
        assertThat(OscPath.snapSlotName(1)).isEqualTo("/-snap/01/name/01")
        assertThat(OscPath.snapSlotName(12)).isEqualTo("/-snap/12/name/01")
        assertThat(OscPath.snapSlotName(64)).isEqualTo("/-snap/64/name/01")
    }

    @Test
    fun parseSnapshotNames_keepsOnlyNonBlankNames() {
        val replies = mapOf(
            OscPath.snapSlotName(1) to listOf("  "),
            OscPath.snapSlotName(2) to listOf("Record routing"),
            OscPath.snapSlotName(3) to listOf("Soundcheck"),
        )
        assertThat(Xr18RoutingOsc.parseSnapshotNames(replies)).containsExactly(
            MixerSnapshotOption(2, "Record routing"),
            MixerSnapshotOption(3, "Soundcheck"),
        )
    }
}
