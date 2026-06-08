package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class Flow8UsbScribbleMapperTest {
    @Test
    fun mapNames_duplicatesCh56AndCh78_and_setsMainLr() {
        val names = listOf("Mic 1", "Mic 2", "Keys", "Vox", "Synth", "Guitar")

        val labels = Flow8UsbScribbleMapper.mapNamesToUsb(names)

        assertThat(labels.map { it.usbChannel }).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).inOrder()
        assertThat(labels[4].name).isEqualTo("Synth")
        assertThat(labels[5].name).isEqualTo("Synth")
        assertThat(labels[6].name).isEqualTo("Guitar")
        assertThat(labels[7].name).isEqualTo("Guitar")
        assertThat(labels[8].name).isEqualTo(Flow8UsbScribbleMapper.MAIN_L_NAME)
        assertThat(labels[9].name).isEqualTo(Flow8UsbScribbleMapper.MAIN_R_NAME)
    }

    @Test
    fun decodeNames_readsSixNamesFromHardwareBleDump() {
        val buf = readRepoFixture("flow8_dump.bin")
        val names = Flow8StateDecoder.decodeNames(buf)

        assertThat(names).containsExactly(
            "SM58 (L)",
            "SM58 (R)",
            "Mic 3",
            "Violine",
            "ELECTRIC1",
            "Playback",
        ).inOrder()
    }

    @Test
    fun mapNames_mapsHardwareBleDumpToUsbChannels() {
        val buf = readRepoFixture("flow8_dump.bin")
        val labels = Flow8UsbScribbleMapper.mapNamesToUsb(Flow8StateDecoder.decodeNames(buf))

        assertThat(labels.first { it.usbChannel == 5 }.name).isEqualTo("ELECTRIC1")
        assertThat(labels.first { it.usbChannel == 6 }.name).isEqualTo("ELECTRIC1")
        assertThat(labels.first { it.usbChannel == 7 }.name).isEqualTo("Playback")
        assertThat(labels.first { it.usbChannel == 8 }.name).isEqualTo("Playback")
    }

    @Test
    fun parseIconConfig_readsHardwareFixture() {
        val buf = readRepoFixture("flow8_dump.bin")
        val payload = readRepoFixture("icon_config.bin")
        val icons = Flow8StateDecoder.parseIconConfig(payload, buf)

        assertThat(icons).containsExactly(50, 50, 50, 39, 23, 60).inOrder()
    }

    @Test
    fun decodeInputType_readsBleCompactHeaders() {
        val buf = readRepoFixture("flow8_dump.bin")
        val offsets = Flow8StateDecoder.scanNameOffsets(buf)

        assertThat(offsets).hasSize(6)
        assertThat(Flow8StateDecoder.decodeInputType(buf, offsets[0])).isEqualTo(0)
        assertThat(Flow8StateDecoder.decodeInputType(buf, offsets[3])).isEqualTo(3)
        assertThat(Flow8StateDecoder.decodeInputType(buf, offsets[4])).isEqualTo(4)
        assertThat(Flow8StateDecoder.decodeInputType(buf, offsets[5])).isEqualTo(5)
    }

    @Test
    fun mapNames_appliesIconsToUsbChannels() {
        val buf = readRepoFixture("flow8_dump.bin")
        val iconPayload = readRepoFixture("icon_config.bin")
        val names = Flow8StateDecoder.decodeNames(buf)
        val icons = Flow8StateDecoder.parseIconConfig(iconPayload, buf)
        val labels = Flow8UsbScribbleMapper.mapNamesToUsb(names, icons)

        assertThat(labels.first { it.usbChannel == 1 }.iconId).isEqualTo(50)
        assertThat(labels.first { it.usbChannel == 4 }.iconId).isEqualTo(39)
        assertThat(labels.first { it.usbChannel == 5 }.iconId).isEqualTo(23)
        assertThat(labels.first { it.usbChannel == 6 }.iconId).isEqualTo(23)
        assertThat(labels.first { it.usbChannel == 7 }.iconId).isEqualTo(60)
        assertThat(labels.first { it.usbChannel == 8 }.iconId).isEqualTo(60)
        assertThat(labels.first { it.usbChannel == 9 }.iconId).isEqualTo(MixingStationIcons.SPEAKER_LEFT)
        assertThat(labels.first { it.usbChannel == 10 }.iconId).isEqualTo(MixingStationIcons.SPEAKER_RIGHT)
    }

    private fun readRepoFixture(name: String): ByteArray {
        val candidates = listOf(
            Paths.get("docs/flow8-reverse-engineering/tools", name),
            Paths.get("../docs/flow8-reverse-engineering/tools", name),
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("Missing hardware fixture: $name")
        return Files.readAllBytes(path)
    }
}
