package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveWaveformDisplayTest {
    @Test
    fun binLiveWaveform_rightAlignsInCapacityWindow() {
        val peaks = FloatArray(30) { 0.5f }
        val capacity = 450
        val pixels = 90
        val columns = binLiveWaveformToPixels(peaks, capacity, pixels)
        assertThat(columns.size).isEqualTo(90)
        val filledPixels = columns.count { it > 0f }
        assertThat(filledPixels).isEqualTo(6)
        assertThat(columns.take(84).all { it == 0f }).isTrue()
        assertThat(columns.drop(84).all { it > 0f }).isTrue()
    }
}
