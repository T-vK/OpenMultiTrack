package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Flow8IconPresetsTest {
    @Test
    fun validatedDynamicMicPreset4IsHandheld() {
        assertThat(Flow8IconPresets.resolve(0, 4)).isEqualTo(MixingStationIcons.HANDHELD_MIC)
    }

    @Test
    fun guitarBassPreset2IsFlowOnlyCrash() {
        assertThat(Flow8IconPresets.resolve(2, 2)).isNull()
    }

    @Test
    fun guitarBassPreset0IsFlowOnlyKickLeft() {
        assertThat(Flow8IconPresets.resolve(2, 0)).isNull()
    }

    @Test
    fun guitarPagePreset2IsSynthesizer1() {
        assertThat(Flow8IconPresets.resolve(4, 2)).isEqualTo(31)
    }

    @Test
    fun lineInstrumentPreset4IsAcousticGuitar() {
        assertThat(Flow8IconPresets.resolve(3, 4)).isEqualTo(MixingStationIcons.ACOUSTIC_GUITAR)
    }

    @Test
    fun playbackPreset7IsFlowOnlyWallSpeaker() {
        assertThat(Flow8IconPresets.resolve(5, 7)).isNull()
    }

    @Test
    fun outOfRangePresetReturnsNull() {
        assertThat(Flow8IconPresets.resolve(0, 99)).isNull()
    }
}
