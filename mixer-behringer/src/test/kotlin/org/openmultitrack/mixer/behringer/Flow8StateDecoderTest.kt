package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class Flow8StateDecoderTest {
    @Test
    fun decodeNames_ignoresFalsePositiveBeforeRealChannelRecords() {
        val fixture = readRepoFixture("flow8_dump.bin")
        val falsePositive = byteArrayOf(
            0x04, 0x54, 0x45, 0x53, 0x54, // "TEST" — would shift names if accepted blindly
        )
        val buf = falsePositive + fixture

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
    fun parseIconConfig_doesNotMapGuitarPresetToDrumIcon() {
        val buf = readRepoFixture("flow8_dump.bin")
        val payload = readRepoFixture("icon_config.bin")
        val icons = Flow8StateDecoder.parseIconConfig(payload, buf)

        assertThat(icons[4]).isEqualTo(MixingStationIcons.ACOUSTIC_GUITAR)
        assertThat(icons[4]).isNotEqualTo(2)
        assertThat(icons[5]).isEqualTo(MixingStationIcons.TAPE)
        assertThat(icons[5]).isNotEqualTo(4)
    }

    @Test
    fun decodeIconGroup_returnsNullForUnknownTypedPresetInsteadOfDrumFallback() {
        val icon = Flow8StateDecoder.decodeIconGroup(
            stripIndex = 4,
            marker = 0x03,
            preset = 2,
            mixerState = null,
            nameOffsets = emptyMap(),
        )

        assertThat(icon).isNull()
    }

    @Test
    fun decodeIconGroup_resolvesViolinIconFromLineInstrumentPreset() {
        val buf = readRepoFixture("flow8_dump.bin")
        val offsets = Flow8StateDecoder.nameOffsetsByStrip(buf)
        val icon = Flow8StateDecoder.decodeIconGroup(
            stripIndex = 3,
            marker = 0x03,
            preset = 4,
            mixerState = buf,
            nameOffsets = offsets,
        )

        assertThat(icon).isEqualTo(MixingStationIcons.VIOLIN)
    }

    @Test
    fun scanChannelRecords_assignsNamesByStripIndexNotScanOrder() {
        val records = Flow8StateDecoder.scanChannelRecords(readRepoFixture("flow8_dump.bin"))

        assertThat(records[0]).isEqualTo("SM58 (L)")
        assertThat(records[3]).isEqualTo("Violine")
        assertThat(records[4]).isEqualTo("ELECTRIC1")
        assertThat(records[5]).isEqualTo("Playback")
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
