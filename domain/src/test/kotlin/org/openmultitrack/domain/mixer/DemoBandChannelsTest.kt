package org.openmultitrack.domain.mixer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DemoBandChannelsTest {
    @Test
    fun channelStripStates_matchDemoChannelCount() {
        val strips = DemoBandChannels.channelStripStates()
        assertThat(strips).hasSize(VirtualMixer.DEMO_CHANNEL_COUNT)
        assertThat(strips.map { it.displayName }).containsAtLeast(
            "Lead Vox",
            "Kick",
            "Snare",
            "Bass",
            "Keys",
        )
        assertThat(strips.all { it.iconId != null }).isTrue()
        assertThat(strips.all { it.monitoring }).isTrue()
    }
}
