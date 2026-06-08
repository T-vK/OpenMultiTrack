package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Xr18ScribbleImporterTest {
    @Test
    fun resolveSrc_mapsInputChannel() {
        val replies = mapOf(
            "/ch/01/config/name" to listOf("E-Bass"),
            "/ch/01/config/color" to listOf(10),
        )
        val (source, name, color) = Xr18ScribbleImporter.resolveSrc(replies, src = 0)
        assertThat(source).isEqualTo("Ch01")
        assertThat(name).isEqualTo("E-Bass")
        assertThat(color).isEqualTo(10)
    }

    @Test
    fun resolveSrc_mapsAuxLWithSideSuffix() {
        val replies = mapOf(
            "/rtn/aux/config/name" to listOf("Playback"),
            "/rtn/aux/config/color" to listOf(12),
        )
        val (source, name, color) = Xr18ScribbleImporter.resolveSrc(replies, src = 16)
        assertThat(source).isEqualTo("AuxL")
        assertThat(name).isEqualTo("Playback (L)")
        assertThat(color).isEqualTo(12)
    }

    @Test
    fun colorIndex_mapsToArgb() {
        assertThat(XAirScribbleColors.toArgb(1)).isEqualTo(0xFFE53935.toInt())
    }
}
