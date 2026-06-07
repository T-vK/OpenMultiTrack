package org.openmultitrack.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Uac2FixtureInstrumentedTest {
    @Test
    fun parseFlow8FixtureReportsTenCaptureFourPlayback() {
        val raw = loadAsset("uac2/flow8_recording_mode.bin")
        val caps = NativeUac2Probe.parseConfigDescriptor(raw)

        assertThat(caps).isNotNull()
        assertThat(caps!!.parseOk).isTrue()
        assertThat(caps.uacVersion).isEqualTo(2)
        assertThat(caps.maxCaptureChannels).isAtLeast(10)
        assertThat(caps.maxPlaybackChannels).isAtLeast(4)

        val capture = NativeUac2Probe.selectBestCaptureAlt(caps, minChannels = 10)
        val playback = NativeUac2Probe.selectBestPlaybackAlt(caps, minChannels = 4)

        assertThat(capture).isNotNull()
        assertThat(capture!!.channels).isAtLeast(10)
        assertThat(capture.isInput).isTrue()
        assertThat(capture.sampleRateHz).isEqualTo(48_000)

        assertThat(playback).isNotNull()
        assertThat(playback!!.channels).isAtLeast(4)
        assertThat(playback.isInput).isFalse()
    }

  /** Synthetic XR18 stand-in until physical hardware is available for virtual soundcheck. */
    @Test
    fun syntheticXr18FixtureSupportsEighteenChannelPlayback() {
        val raw = buildSyntheticXr18Descriptor()
        val caps = NativeUac2Probe.parseConfigDescriptor(raw)

        assertThat(caps).isNotNull()
        assertThat(caps!!.parseOk).isTrue()
        assertThat(caps.maxCaptureChannels).isEqualTo(18)
        assertThat(caps.maxPlaybackChannels).isEqualTo(18)

        val playback = NativeUac2Probe.selectBestPlaybackAlt(caps, minChannels = 18)
        assertThat(playback).isNotNull()
        assertThat(playback!!.channels).isEqualTo(18)
    }

    private fun loadAsset(path: String): ByteArray {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets.open(path).use { it.readBytes() }
    }

    private fun buildSyntheticXr18Descriptor(): ByteArray {
        val d = ArrayList<Byte>()

        fun add(bytes: ByteArray) {
            bytes.forEach { d.add(it) }
        }

        add(byteArrayOf(0x09, 0x02, 0x00, 0x00, 0x01, 0x01, 0x00, 0x80.toByte(), 0xFA.toByte()))

        fun addIface(num: Int, alt: Int, endpoints: Int, protocol: Int) {
            add(byteArrayOf(0x09, 0x04, num.toByte(), alt.toByte(), endpoints.toByte(), 0x01, 0x02, protocol.toByte(), 0x00))
        }

        fun addAsGeneral(terminal: Int, channels: Int) {
            add(
                byteArrayOf(
                    0x10, 0x24, 0x01, terminal.toByte(), 0x00, 0x01,
                    0x01, 0x00, 0x00, 0x00,
                    channels.toByte(),
                    0x00, 0x00, 0x00, 0x00, 0x00,
                ),
            )
        }

        fun addTypeI(subslot: Int, bits: Int) {
            add(byteArrayOf(0x06, 0x24, 0x02, 0x01, subslot.toByte(), bits.toByte()))
        }

        fun addEp(addr: Int, maxPacket: Int) {
            add(
                byteArrayOf(
                    0x07, 0x05, addr.toByte(), 0x05,
                    (maxPacket and 0xFF).toByte(),
                    ((maxPacket shr 8) and 0xFF).toByte(),
                    0x01,
                ),
            )
        }

        addIface(1, 1, 1, 0x20)
        addAsGeneral(0x02, 18)
        addTypeI(4, 32)
        addEp(0x01, 512)

        addIface(2, 1, 1, 0x20)
        addAsGeneral(0x0B, 18)
        addTypeI(4, 32)
        addEp(0x81, 1024)

        val total = d.size
        d[2] = (total and 0xFF).toByte()
        d[3] = ((total shr 8) and 0xFF).toByte()
        return d.toByteArray()
    }
}
