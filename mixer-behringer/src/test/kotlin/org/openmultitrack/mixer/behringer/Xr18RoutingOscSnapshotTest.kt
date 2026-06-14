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
    fun parseSnapshotNames_returnsAllSlotsIncludingEmpty() {
        val replies = mapOf(
            OscPath.snapSlotName(1) to listOf("  "),
            OscPath.snapSlotName(2) to listOf("Record routing"),
            OscPath.snapSlotName(3) to listOf("Soundcheck"),
        )
        val parsed = Xr18RoutingOsc.parseSnapshotNames(replies)
        assertThat(parsed).hasSize(64)
        assertThat(parsed[0]).isEqualTo(MixerSnapshotOption(1, ""))
        assertThat(parsed[1]).isEqualTo(MixerSnapshotOption(2, "Record routing"))
        assertThat(parsed[2]).isEqualTo(MixerSnapshotOption(3, "Soundcheck"))
        assertThat(parsed[3]).isEqualTo(MixerSnapshotOption(4, ""))
    }
}
