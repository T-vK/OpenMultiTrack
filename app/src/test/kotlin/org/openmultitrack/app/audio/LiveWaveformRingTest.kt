package org.openmultitrack.app.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveWaveformRingTest {
    @Test
    fun seedPeaksPreservesOrderAndRespectsCapacity() {
        val ring = LiveWaveformRing(capacityPeaks = 5)
        ring.seedPeaks(floatArrayOf(0.1f, 0.5f, 0.9f))
        val snap = ring.snapshot()
        assertThat(snap.peakCount).isEqualTo(3)
        assertThat(snap.peaks).hasLength(3)
        assertThat(snap.peaks[0]).isWithin(1e-6f).of(0.1f)
        assertThat(snap.peaks[1]).isWithin(1e-6f).of(0.5f)
        assertThat(snap.peaks[2]).isWithin(1e-6f).of(0.9f)
        assertThat(snap.capacity).isEqualTo(5)
    }

    @Test
    fun seedPeaksRollsOffOldestWhenOverCapacity() {
        val ring = LiveWaveformRing(capacityPeaks = 3)
        ring.seedPeaks(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))
        val snap = ring.snapshot()
        assertThat(snap.peaks).hasLength(3)
        assertThat(snap.peaks[0]).isWithin(1e-6f).of(0.2f)
        assertThat(snap.peaks[2]).isWithin(1e-6f).of(0.4f)
    }
}
