package org.openmultitrack.app.ui.daw

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransportTimeFormatTest {
    @Test
    fun parseTransportTime_parsesMinutesAndSeconds() {
        assertThat(parseTransportTime("0:05")).isWithin(0.01f).of(5f)
        assertThat(parseTransportTime("1:30")).isWithin(0.01f).of(90f)
        assertThat(parseTransportTime("12:03")).isWithin(0.01f).of(723f)
    }

    @Test
    fun parseTransportTime_parsesHours() {
        assertThat(parseTransportTime("1:02:03")).isWithin(0.01f).of(3723f)
    }

    @Test
    fun parseSoundcheckTransportLabel_parsesPositionAndDuration() {
        val label = DawTransportSemantics.SOUNDCHECK_TRANSPORT_PREFIX + "0:05 of 0:12"
        val parsed = parseSoundcheckTransportLabel(label)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.first).isWithin(0.01f).of(5f)
        assertThat(parsed.second).isWithin(0.01f).of(12f)
    }

    @Test
    fun parseTransportTime_roundTripsAdaptiveFormat() {
        val samples = listOf(5f, 65f, 605f, 3665f)
        for (sec in samples) {
            val formatted = formatAdaptiveTransportTime(sec)
            assertThat(parseTransportTime(formatted)).isWithin(0.51f).of(sec)
        }
    }
}
