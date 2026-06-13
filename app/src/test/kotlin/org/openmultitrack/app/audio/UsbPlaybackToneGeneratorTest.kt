package org.openmultitrack.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UsbPlaybackToneGeneratorTest {
    @Test
    fun fill_putsEnergyOnActiveChannelOnly() {
        val gen = UsbPlaybackToneGenerator(channelCount = 4, activeChannel = 2, sampleRate = 48_000)
        val buffer = FloatArray(4 * 256)
        val frames = gen.fill(buffer, 256)
        assertThat(frames).isEqualTo(256)
        var activePeak = 0f
        for (f in 0 until frames) {
            for (ch in 0 until 4) {
                val sample = buffer[f * 4 + ch]
                if (ch == 2) {
                    activePeak = maxOf(activePeak, kotlin.math.abs(sample))
                } else {
                    assertThat(sample).isEqualTo(0f)
                }
            }
        }
        assertThat(activePeak).isGreaterThan(0.01f)
    }

    @Test
    fun frequencyHz_increasesPerChannel() {
        assertThat(UsbPlaybackToneGenerator.frequencyHz(0)).isWithin(0.01).of(220.0)
        assertThat(UsbPlaybackToneGenerator.frequencyHz(3)).isWithin(0.01).of(880.0)
    }
}
