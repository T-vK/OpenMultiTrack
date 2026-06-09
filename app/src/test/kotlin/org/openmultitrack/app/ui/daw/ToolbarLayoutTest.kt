package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.openmultitrack.domain.session.AppMode
import androidx.compose.ui.unit.dp

class ToolbarLayoutTest {
    @Test
    fun wideLayoutKeepsSecondaryControlsInTopBar() {
        val layout = computeToolbarLayout(
            barWidth = 1000.dp,
            appMode = AppMode.VIRTUAL_SOUNDCHECK,
            showRecordingStorageInfoButton = true,
        )
        assertThat(layout.showMixerSettingsInBar).isTrue()
        assertThat(layout.showModeInBar).isTrue()
        assertThat(layout.showStorageInBar).isTrue()
        assertThat(layout.showOpenInBar).isTrue()
        assertThat(layout.showRemoteInBar).isTrue()
        assertThat(layout.showSettingsInBar).isTrue()
        assertThat(layout.showModeInDrawer).isFalse()
    }

    @Test
    fun narrowLayoutOverflowsSecondaryControlsToDrawer() {
        val layout = computeToolbarLayout(
            barWidth = 400.dp,
            appMode = AppMode.MULTITRACK_RECORD,
            showRecordingStorageInfoButton = true,
        )
        assertThat(layout.showMixerSettingsInBar).isFalse()
        assertThat(layout.showModeInBar).isFalse()
        assertThat(layout.showStorageInBar).isFalse()
        assertThat(layout.showSettingsInBar).isFalse()
        assertThat(layout.showMixerSettingsInDrawer).isTrue()
        assertThat(layout.showModeInDrawer).isTrue()
        assertThat(layout.showStorageInDrawer).isTrue()
    }
}
