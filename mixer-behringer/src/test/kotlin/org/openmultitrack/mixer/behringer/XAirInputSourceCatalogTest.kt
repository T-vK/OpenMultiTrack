package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class XAirInputSourceCatalogTest {
    @Test
    fun recordTarget_usesPreampAndMatchingInsrc() {
        val st = XAirInputSourceCatalog.recordTarget(2)
        assertThat(st.rtnSw).isEqualTo(0)
        assertThat(st.insrc).isEqualTo(3)
        assertThat(st.describe()).isEqualTo("IN03")
    }

    @Test
    fun soundcheckTarget_usesUsbReturn() {
        val st = XAirInputSourceCatalog.soundcheckTarget(0)
        assertThat(st.rtnSw).isEqualTo(1)
        assertThat(st.rtnsrc).isEqualTo(0)
        assertThat(st.describe()).isEqualTo("U01")
    }

    @Test
    fun insrcAndRtnLabels() {
        assertThat(XAirInputSourceCatalog.insrcLabel(0)).isEqualTo("OFF")
        assertThat(XAirInputSourceCatalog.insrcLabel(5)).isEqualTo("IN05")
        assertThat(XAirInputSourceCatalog.rtnLabel(17)).isEqualTo("U18")
    }
}
