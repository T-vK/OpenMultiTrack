package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MixerSnapshotNamesTest {
    @Test
    fun isPlaceholder_treatsDashAsEmpty() {
        assertThat(MixerSnapshotNames.isPlaceholder("-")).isTrue()
        assertThat(MixerSnapshotNames.isPlaceholder("  -  ")).isTrue()
        assertThat(MixerSnapshotNames.isPlaceholder("")).isTrue()
        assertThat(MixerSnapshotNames.isPlaceholder("Record mix")).isFalse()
    }

    @Test
    fun resolveFromReplies_skipsPlaceholderDash() {
        val replies = mapOf(
            OscPath.snapSlotName(2) to listOf("-"),
            OscPath.snapSlotName(3) to listOf("Soundcheck"),
        )
        assertThat(MixerSnapshotNames.resolveFromReplies(2, replies)).isEmpty()
        assertThat(MixerSnapshotNames.resolveFromReplies(3, replies)).isEqualTo("Soundcheck")
    }
}
