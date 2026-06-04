package org.openmultitrack.domain.mixer

import org.junit.Assert.assertEquals
import org.junit.Test

/** Placeholder tests for OSC path helpers added in mixer-behringer. */
class OscPathTest {
    @Test
    fun channelPath_formatsTwoDigitIndex() {
        val path = formatChannelPath("/ch/{nn}/in/src", 3)
        assertEquals("/ch/03/in/src", path)
    }

    private fun formatChannelPath(template: String, channel: Int): String {
        return template.replace("{nn}", channel.toString().padStart(2, '0'))
    }
}
