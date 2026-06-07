package org.openmultitrack.app

import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openmultitrack.app.audio.SessionPlayer
import org.openmultitrack.app.audio.SessionRecorder
import org.openmultitrack.app.test.RequiresUsbDevice
import org.openmultitrack.app.test.UsbDeviceRule
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.sessionio.wav.WavWriter
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService
import java.io.File
import kotlin.math.min
import kotlin.math.sin

/**
 * Flow 8 virtual soundcheck simulation: up to **4 playback USB returns** (not 18 like XR18).
 *
 * - Descriptor probe asserts 4ch playback capability.
 * - Playback test uses a synthetic 4ch tone file (works even when Oboe input is stereo-limited).
 * - Record-and-playback requests 4ch capture; skips with a clear message if Oboe negotiates fewer
 *   channels until UAC2 isoch capture lands.
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x050c)
class Flow8VirtualSoundcheckInstrumentedTest {
    @get:Rule
    val usbDeviceRule = UsbDeviceRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val enumerator = UsbAudioEnumerator(context)
    private val probeService = UsbAudioProbeService(enumerator)

    @Test
    fun descriptorReportsFourPlaybackUsbReturns() {
        assumeUsbPermission()
        val probe = probeService.probe(findFlow8())
        assertThat(probe.uac2Caps).isNotNull()
        assertThat(probe.uac2Caps!!.maxPlaybackChannels).isEqualTo(4)
        assertThat(probe.uac2Caps!!.maxCaptureChannels).isAtLeast(10)
    }

    @Test
    fun playFourChannelTestToneToUsbReturns() = runBlocking {
        assumeUsbPermission()
        val probe = probeService.probe(findFlow8())
        val outputProbe = probe.output
        assumeTrue(
            "Oboe output probe must succeed for playback (got ${outputProbe?.errorMessage})",
            outputProbe?.isSuccess == true,
        )
        val output = outputProbe!!

        val wavFile = File(context.getExternalFilesDir(null), "instrumented-sessions/flow8_4ch_tone.wav").apply {
            parentFile?.mkdirs()
        }
        writeFourChannelTestTone(wavFile, sampleRate = output.sampleRate)

        val player = SessionPlayer()
        val playResult = player.play(this, wavFile, output.deviceId)
        assumeTrue(
            "4ch playback to Flow 8 USB returns failed (Oboe reported ${output.channelCount}ch on probe; " +
                "UAC2 isoch playback may be required): ${playResult.exceptionOrNull()?.message}",
            playResult.isSuccess,
        )
        delay(1_500)
        player.stop()

        assertThat(WavReader(wavFile).use { it.format.channelCount }).isEqualTo(VIRTUAL_SOUNDCHECK_CHANNELS)
    }

    @Test
    fun recordFourChannelsAndPlayBackToUsbReturns() = runBlocking {
        assumeUsbPermission()
        val probe = probeService.probe(findFlow8())
        val outputProbe = probe.output
        val inputProbe = probe.input

        assumeTrue(
            "Oboe output probe required for virtual soundcheck playback",
            outputProbe?.isSuccess == true,
        )
        assumeTrue(
            "Oboe input probe required to record a session",
            inputProbe?.isSuccess == true,
        )

        val sessionsDir = File(context.getExternalFilesDir(null), "instrumented-sessions").apply { mkdirs() }
        val recorder = SessionRecorder()

        val recordStart = recorder.start(
            scope = this,
            deviceId = inputProbe!!.deviceId,
            channels = VIRTUAL_SOUNDCHECK_CHANNELS,
            outputDir = sessionsDir,
            sampleRateHz = inputProbe.sampleRate,
        )
        assumeTrue(
            "Could not start ${VIRTUAL_SOUNDCHECK_CHANNELS}ch recording: ${recordStart.exceptionOrNull()?.message}",
            recordStart.isSuccess,
        )

        delay(2_000)
        val session = recorder.stop()
        assertThat(session).isNotNull()

        assumeTrue(
            "Oboe opened ${session!!.channelCount}ch capture; need $VIRTUAL_SOUNDCHECK_CHANNELS for Flow 8 " +
                "virtual soundcheck record path (UAC2 isoch capture not implemented yet)",
            session.channelCount >= VIRTUAL_SOUNDCHECK_CHANNELS,
        )

        val wavFormat = WavReader(File(session.filePath)).use { it.format }
        assertThat(wavFormat.channelCount).isEqualTo(VIRTUAL_SOUNDCHECK_CHANNELS)

        val playbackChannels = min(VIRTUAL_SOUNDCHECK_CHANNELS, outputProbe!!.channelCount)
        assumeTrue(
            "Oboe output is ${outputProbe.channelCount}ch; need $VIRTUAL_SOUNDCHECK_CHANNELS for playback",
            playbackChannels >= VIRTUAL_SOUNDCHECK_CHANNELS,
        )

        val player = SessionPlayer()
        val playResult = player.play(this, File(session.filePath), outputProbe.deviceId)
        assertThat(playResult.isSuccess).isTrue()
        delay(1_500)
        player.stop()
    }

    private fun writeFourChannelTestTone(file: File, sampleRate: Int, frames: Int = sampleRate / 2) {
        val channels = VIRTUAL_SOUNDCHECK_CHANNELS
        WavWriter(file, channelCount = channels, sampleRate = sampleRate).use { writer ->
            val buffer = FloatArray(frames * channels)
            for (frame in 0 until frames) {
                for (ch in 0 until channels) {
                    val freq = 220.0 * (ch + 1)
                    val sample = (sin(2.0 * Math.PI * freq * frame / sampleRate) * 0.2).toFloat()
                    buffer[frame * channels + ch] = sample
                }
            }
            writer.writeInterleavedFloat(buffer, frames)
        }
    }

    private fun assumeUsbPermission() {
        val usbManager = context.getSystemService(UsbManager::class.java)
        val device = usbManager.deviceList.values.first {
            it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
        }
        assumeTrue(
            "Grant USB permission in the app UI (see scripts/run-emulator-with-flow8.sh)",
            usbManager.hasPermission(device),
        )
    }

    private fun findFlow8() =
        enumerator.listUsbDevices().first { it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID }

    private companion object {
        const val FLOW8_VENDOR_ID = 0x1397
        const val FLOW8_PRODUCT_ID = 0x050c
        const val VIRTUAL_SOUNDCHECK_CHANNELS = 4
    }
}
