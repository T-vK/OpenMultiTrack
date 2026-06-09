package org.openmultitrack.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteWaveformUtilTest {
    @Test
    fun mergeLiveWaveformTail_appendsNewSamples() {
        val existing = floatArrayOf(0.1f, 0.2f, 0.3f)
        val tail = floatArrayOf(0.2f, 0.3f, 0.4f, 0.5f)
        val merged = RemoteWaveformUtil.mergeLiveWaveformTail(existing, tail, capacity = 10)
        assertThat(merged).isEqualTo(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f))
    }

    @Test
    fun mergeLiveWaveformTail_trimsToCapacity() {
        val existing = FloatArray(8) { it / 10f }
        val tail = floatArrayOf(0.7f, 0.8f, 0.9f)
        val merged = RemoteWaveformUtil.mergeLiveWaveformTail(existing, tail, capacity = 8)
        assertThat(merged).hasLength(8)
        assertThat(merged.last()).isEqualTo(0.9f)
    }
}
