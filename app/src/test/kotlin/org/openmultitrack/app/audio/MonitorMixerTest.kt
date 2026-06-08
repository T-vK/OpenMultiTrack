package org.openmultitrack.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MonitorMixerTest {
    @Test
    fun solo_overrides_monitor_set() {
        val config = MonitorMixConfig(
            enabled = true,
            channelMonitoring = setOf(0, 1, 2),
            soloChannel = 1,
            gainLinear = 2f,
            outputDeviceId = 1,
        )
        assertThat(MonitorMixer.effectiveMonitorChannels(config)).containsExactly(1)
    }

    @Test
    fun gain_boosts_output() {
        val scratch = FloatArray(2) { 0.5f }
        val dest = FloatArray(2)
        val config = MonitorMixConfig(
            enabled = true,
            channelMonitoring = setOf(0),
            gainLinear = 2f,
            outputDeviceId = 1,
        )
        MonitorMixer.mixToStereo(scratch, 1, 1, config, dest)
        assertThat(dest[0]).isWithin(0.01f).of(1f)
    }
}
