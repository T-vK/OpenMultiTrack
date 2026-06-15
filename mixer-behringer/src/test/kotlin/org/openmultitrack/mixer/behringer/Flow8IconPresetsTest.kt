package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Flow8IconPresetsTest {
    @Test
    fun validatedDynamicMicPreset4IsHandheld() {
        assertThat(Flow8IconPresets.resolve(0, 4)).isEqualTo(MixingStationIcons.HANDHELD_MIC)
    }

    @Test
    fun guitarBassPreset2IsAcousticGuitar() {
        assertThat(Flow8IconPresets.resolve(2, 2)).isEqualTo(MixingStationIcons.ACOUSTIC_GUITAR)
    }

    @Test
    fun guitarBassPreset0IsElectricBass() {
        assertThat(Flow8IconPresets.resolve(2, 0)).isEqualTo(MixingStationIcons.ELECTRIC_BASS)
    }

    @Test
    fun guitarPagePreset2IsAcousticGuitar() {
        assertThat(Flow8IconPresets.resolve(4, 2)).isEqualTo(MixingStationIcons.ACOUSTIC_GUITAR)
    }

    @Test
    fun lineInstrumentPreset4IsViolin() {
        assertThat(Flow8IconPresets.resolve(3, 4)).isEqualTo(MixingStationIcons.VIOLIN)
    }

    @Test
    fun playbackPreset7IsTape() {
        assertThat(Flow8IconPresets.resolve(5, 7)).isEqualTo(MixingStationIcons.TAPE)
    }

    @Test
    fun outOfRangePresetReturnsNull() {
        assertThat(Flow8IconPresets.resolve(0, 99)).isNull()
    }
}
