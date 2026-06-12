package org.openmultitrack.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LogFilterModelsTest {
    @Test
    fun customFilter_hideRegexMatchesTag() {
        val filter = LogCustomFilter(
            id = "1",
            pattern = "Vu.*",
            mode = LogCustomFilterMode.HIDE,
            enabled = true,
        )
        assertThat(filter.matchesTag("VuMeter")).isTrue()
        assertThat(filter.matchesTag("Usb")).isFalse()
    }

    @Test
    fun tagCatalog_mergesKnownAndDiscoveredTags() {
        val lines = listOf(
            "12:00:00.000 I/CustomTag: hello",
            "12:00:00.001 I/VuMeter: peak",
        )
        val tags = LogTagCatalog.allTags(lines)
        assertThat(tags).contains("VuMeter")
        assertThat(tags).contains("CustomTag")
        assertThat(tags).contains("Usb")
    }

    @Test
    fun customFilterCodec_roundTrips() {
        val filters = listOf(
            LogCustomFilter("a", "Vu.*", LogCustomFilterMode.HIDE, enabled = true),
            LogCustomFilter("b", "Mixer", LogCustomFilterMode.ONLY_SHOW, enabled = false),
        )
        val decoded = LogCustomFilterCodec.decode(LogCustomFilterCodec.encode(filters))
        assertThat(decoded).isEqualTo(filters)
    }

    @Test
    fun tagFilterCodec_roundTrips() {
        val disabled = setOf("VuMeter", "Usb")
        val decoded = LogTagFilterCodec.decode(LogTagFilterCodec.encodeDisabledTags(disabled))
        assertThat(decoded).isEqualTo(disabled)
    }
}
