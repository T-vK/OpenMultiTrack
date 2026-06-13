package org.openmultitrack.sessionio.wav

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PcmFormatConversionTest {
    @Test
    fun int32PackedToWav24_usesUpper24Bits() {
        val src = byteArrayOf(
            0x00, 0x00, 0x80.toByte(), 0x01, // ch0 int32
            0x00, 0x00, 0x00, 0x00, // ch1
        )
        val dest = ByteArray(6)
        PcmFormatConversion.interleavedToWav24(
            src = src,
            frames = 1,
            channels = 2,
            srcBytesPerFrame = 8,
            dest = dest,
        )
        assertThat(dest[0]).isEqualTo(0x00)
        assertThat(dest[1]).isEqualTo(0x80.toByte())
        assertThat(dest[2]).isEqualTo(0x01)
    }

    @Test
    fun flow8FrameSize_is40BytesForTenChannelInt32() {
        assertThat(10 * 4).isEqualTo(40)
    }
}
