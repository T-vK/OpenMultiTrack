package org.openmultitrack.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.audio.SessionPlayer
import org.openmultitrack.app.audio.SessionRecorder
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbAppProcessRule
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.audio.NativeUac2Probe
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService
import java.io.File

/**
 * End-to-end USB audio tests with Flow 8 attached to the emulator.
 *
 * Recording uses Oboe today (often stereo on Android). UAC2 descriptor caps are asserted
 * separately so Phase 2 can tighten the WAV channel assertion to 10 without rewriting tests.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x050c)
class UsbAudioRecordingInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    private val context get() = usbAppProcessRule.appContext
    private val enumerator get() = UsbAudioEnumerator(context)
    private val probeService get() = UsbAudioProbeService(enumerator)

    @Test
    fun probeShowsMultichannelDescriptorEvenWhenOboeIsLimited() {
        val result = usbAppProcessRule.runOnActivity { activity ->
            val flow8 = UsbAudioEnumerator(activity).listUsbDevices().first {
                it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
            }
            UsbAudioProbeService(UsbAudioEnumerator(activity)).probe(flow8)
        }

        assertThat(result.uac2Caps).isNotNull()
        assertThat(result.uac2Caps!!.maxCaptureChannels).isAtLeast(10)
        assertThat(result.uac2Caps!!.maxPlaybackChannels).isAtLeast(4)

        val oboeIn = result.input?.channelCount ?: 0
        if (oboeIn in 1..2) {
            assertThat(result.uac2Caps!!.maxCaptureChannels).isGreaterThan(oboeIn)
        }
    }

    @Test
    fun recordAndPlaybackRoundTrip() = runBlocking {
        val probe = usbAppProcessRule.runOnActivity { activity ->
            val flow8 = UsbAudioEnumerator(activity).listUsbDevices().first {
                it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
            }
            UsbAudioProbeService(UsbAudioEnumerator(activity)).probe(flow8)
        }
        val uac2In = probe.uac2Caps?.maxCaptureChannels ?: 0
        assertThat(uac2In).isAtLeast(10)

        val inputProbe = probe.input
        val outputProbe = probe.output
        org.junit.Assume.assumeTrue(
            "Oboe USB audio device not exposed (emulator has no snd-usb-audio; physical device required for record/playback)",
            inputProbe?.isSuccess == true || outputProbe?.isSuccess == true,
        )

        val recordDeviceId = inputProbe?.takeIf { it.isSuccess }?.deviceId
            ?: outputProbe!!.deviceId
        val recordChannels = inputProbe?.takeIf { it.isSuccess }?.channelCount ?: 2

        val outputDeviceId = outputProbe?.takeIf { it.isSuccess }?.deviceId ?: recordDeviceId
        val playbackChannels = outputProbe?.takeIf { it.isSuccess }?.channelCount ?: 2

        val sessionsDir = File(context.getExternalFilesDir(null), "instrumented-sessions").apply { mkdirs() }
        val recorder = SessionRecorder()
        val player = SessionPlayer()

        val start = recorder.start(
            scope = this,
            deviceId = recordDeviceId,
            channels = recordChannels.coerceAtLeast(2),
            outputDir = sessionsDir,
            sampleRateHz = inputProbe?.sampleRate ?: 48_000,
        )
        assertThat(start.isSuccess).isTrue()
        delay(2_000)
        val session = recorder.stop()
        assertThat(session).isNotNull()
        assertThat(session!!.framesRecorded).isGreaterThan(0)

        val wavFormat = WavReader(File(session.filePath)).use { it.format }
        assertThat(wavFormat.channelCount).isEqualTo(session.channelCount)
        assertThat(session.channelCount).isAtLeast(2)

        if (recordChannels >= 10) {
            assertThat(session.channelCount).isAtLeast(10)
            assertThat(wavFormat.channelCount).isAtLeast(10)
        }

        val playResult = player.play(this, File(session.filePath), outputDeviceId)
        assertThat(playResult.isSuccess).isTrue()
        delay(500)
        player.stop()

        assertThat(playbackChannels).isAtLeast(2)
        if (playbackChannels >= 4) {
            assertThat(probe.uac2Caps!!.maxPlaybackChannels).isAtLeast(4)
        }
    }

    @Test
    fun uac2DescriptorMatchesDirectParse() {
        usbAppProcessRule.runOnActivity { activity ->
            val enumerator = UsbAudioEnumerator(activity)
            val flow8 = enumerator.listUsbDevices().first {
                it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
            }
            val raw = enumerator.getRawConfigDescriptor(flow8.deviceName)
                ?: error("No config descriptor — run scripts/grant-usb-permission.sh")
            val direct = NativeUac2Probe.parseConfigDescriptor(raw)!!
            val viaProbe = UsbAudioProbeService(enumerator).probe(flow8).uac2Caps!!

            assertThat(viaProbe.maxCaptureChannels).isEqualTo(direct.maxCaptureChannels)
            assertThat(viaProbe.maxPlaybackChannels).isEqualTo(direct.maxPlaybackChannels)
        }
    }

    private fun findFlow8Descriptor() =
        enumerator.listUsbDevices().first { it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID }

    private companion object {
        const val FLOW8_VENDOR_ID = 0x1397
        const val FLOW8_PRODUCT_ID = 0x050c
    }
}
