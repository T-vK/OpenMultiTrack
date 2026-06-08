package org.openmultitrack.mixer.behringer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Flow8UsbScribbleMapperTest {
    @Test
    fun mapSlots_duplicatesStereoLinkedPairs_and_setsMainLr() {
        val slots = listOf(
            slot(0, "Mic 1", icon = 3),
            slot(1, "Mic 2"),
            slot(2, "Keys"),
            slot(3, "Vox"),
            slot(4, "Synth", stereoLinked = true, icon = 17),
            slot(5, "Guitar", stereoLinked = true, icon = 42),
            slot(6, "USB"),
        )

        val labels = Flow8UsbScribbleMapper.mapSlotsToUsb(slots)

        assertThat(labels.map { it.usbChannel }).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).inOrder()
        assertThat(labels[4].name).isEqualTo("Synth")
        assertThat(labels[5].name).isEqualTo("Synth")
        assertThat(labels[4].iconId).isEqualTo(17)
        assertThat(labels[6].name).isEqualTo("Guitar")
        assertThat(labels[7].name).isEqualTo("Guitar")
        assertThat(labels[8].name).isEqualTo(Flow8UsbScribbleMapper.MAIN_L_NAME)
        assertThat(labels[9].name).isEqualTo(Flow8UsbScribbleMapper.MAIN_R_NAME)
    }

    @Test
    fun mapSlots_usesSeparateNamesWhenStereoPairsUnlinked() {
        val slots = listOf(
            slot(0, "Ch1"),
            slot(1, "Ch2"),
            slot(2, "Ch3"),
            slot(3, "Ch4"),
            slot(4, "Line L"),
            slot(5, "Line R"),
            slot(6, "Return"),
        )

        val labels = Flow8UsbScribbleMapper.mapSlotsToUsb(slots)

        assertThat(labels.first { it.usbChannel == 5 }.name).isEqualTo("Line L")
        assertThat(labels.first { it.usbChannel == 6 }.name).isEqualTo("Line R")
        assertThat(labels.first { it.usbChannel == 7 }.name).isEqualTo("Return")
        assertThat(labels.any { it.usbChannel == 8 }).isFalse()
    }

    @Test
    fun decodeSlots_readsIconAndStereoFlagFromConfigBytes() {
        val buf = ByteArray(0x0700)
        writeName(buf, Flow8StateDecoder.NAMES_START, "Bass")
        buf[Flow8StateDecoder.NAMES_START + Flow8StateDecoder.SLOT_ICON_OFFSET] = 17
        buf[Flow8StateDecoder.NAMES_START + 0x1E * 4 + Flow8StateDecoder.SLOT_FLAGS_OFFSET] = 0x01

        val slots = Flow8StateDecoder.decodeSlots(buf)

        assertThat(slots[0].name).isEqualTo("Bass")
        assertThat(slots[0].iconId).isEqualTo(17)
        assertThat(slots[4].stereoLinked).isTrue()
    }

    private fun slot(
        index: Int,
        name: String,
        stereoLinked: Boolean = false,
        icon: Int? = null,
    ) = Flow8StateDecoder.MixerChannelSlot(index, name, icon, stereoLinked)

    private fun writeName(buf: ByteArray, offset: Int, name: String) {
        val bytes = name.toByteArray(Charsets.US_ASCII)
        buf[offset] = bytes.size.toByte()
        bytes.copyInto(buf, offset + 1)
    }
}
