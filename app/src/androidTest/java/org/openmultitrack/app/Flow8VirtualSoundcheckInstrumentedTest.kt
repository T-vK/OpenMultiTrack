package org.openmultitrack.app

import android.hardware.usb.UsbManager
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.openmultitrack.app.test.UsbInstrumentedPermission
import org.openmultitrack.sessionio.wav.WavReader
import org.openmultitrack.sessionio.wav.WavWriter
import org.openmultitrack.usb.AudioEngineRouter
import org.openmultitrack.usb.FullUsbProbeResult
import org.openmultitrack.usb.UsbAudioEnumerator
import org.openmultitrack.usb.UsbAudioProbeService
import org.openmultitrack.usb.UsbAudioStreamHandle
import java.io.File
import kotlin.math.min
import kotlin.math.sin

/**
 * Flow 8 virtual soundcheck simulation: up to **4 playback USB returns** (not 18 like XR18).
 *
 * Uses UAC2 isoch when Oboe exposes no multichannel USB audio device (emulator passthrough).
 */
@RunWith(AndroidJUnit4::class)
@RequiresUsbDevice(vendorId = 0x1397, productId = 0x050c)
class Flow8VirtualSoundcheckInstrumentedTest {
    @get:Rule(order = 0)
    val usbAppProcessRule = UsbAppProcessRule()

    @get:Rule(order = 1)
    val usbDeviceRule = UsbDeviceRule()

    private val context get() = usbAppProcessRule.appContext

    @Test
    fun descriptorReportsFourPlaybackUsbReturns() {
        val probe = probeOnAppProcess()
        assertThat(probe.uac2Caps).isNotNull()
        assertThat(probe.uac2Caps!!.maxPlaybackChannels).isEqualTo(4)
        assertThat(probe.uac2Caps!!.maxCaptureChannels).isAtLeast(10)
    }

    @Test
    fun playFourChannelTestToneToUsbReturns() = runBlocking {
        withFlow8Stream { probe, stream, device ->
            val route = AudioEngineRouter.resolvePlaybackRoute(
                probe,
                stream,
                VIRTUAL_SOUNDCHECK_CHANNELS,
            ) ?: error("No playback route for Flow 8")

            val wavFile = File(context.getExternalFilesDir(null), "instrumented-sessions/flow8_4ch_tone.wav").apply {
                parentFile?.mkdirs()
            }
            writeFourChannelTestTone(wavFile, sampleRate = route.sampleRate)

            val player = SessionPlayer()
            val playResult = player.play(this, wavFile, route, usbDevice = device)
            assertThat(playResult.isSuccess).isTrue()
            delay(1_500)
            player.stop()

            assertThat(WavReader(wavFile).use { it.format.channelCount }).isEqualTo(VIRTUAL_SOUNDCHECK_CHANNELS)
        }
    }

    @Test
    fun recordFourChannelsAndPlayBackToUsbReturns() = runBlocking {
        withFlow8Stream { probe, stream, device ->
            val sessionsDir = File(context.getExternalFilesDir(null), "instrumented-sessions").apply { mkdirs() }
            val captureRoute = AudioEngineRouter.resolveCaptureRoute(
                probe,
                stream,
                VIRTUAL_SOUNDCHECK_CHANNELS,
                maxChannels = VIRTUAL_SOUNDCHECK_CHANNELS,
            ) ?: error("No capture route for Flow 8")

            val recorder = SessionRecorder()
            val recordStart = recorder.start(
                scope = this,
                route = captureRoute,
                outputDir = sessionsDir,
                usbDevice = device,
            )
            assertThat(recordStart.isSuccess).isTrue()

            delay(2_000)
            val session = recorder.stop()
            assertThat(session).isNotNull()
            assertThat(session!!.channelCount).isAtLeast(VIRTUAL_SOUNDCHECK_CHANNELS)

            val wavFormat = WavReader(File(session.filePath)).use { it.format }
            assertThat(wavFormat.channelCount).isEqualTo(VIRTUAL_SOUNDCHECK_CHANNELS)

            val playbackRoute = AudioEngineRouter.resolvePlaybackRoute(
                probe,
                stream,
                min(VIRTUAL_SOUNDCHECK_CHANNELS, wavFormat.channelCount),
            ) ?: error("No playback route for Flow 8")
            assertThat(playbackRoute.channelCount).isAtLeast(VIRTUAL_SOUNDCHECK_CHANNELS)

            val player = SessionPlayer()
            val playResult = player.play(this, File(session.filePath), playbackRoute, usbDevice = device)
            assertThat(playResult.isSuccess).isTrue()
            delay(1_500)
            player.stop()
        }
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

    private fun probeOnAppProcess() = usbAppProcessRule.runOnActivity { activity ->
        val flow8 = UsbAudioEnumerator(activity).listUsbDevices().first {
            it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
        }
        UsbAudioProbeService(UsbAudioEnumerator(activity)).probe(flow8)
    }

    private fun <T> withFlow8Stream(
        block: suspend (FullUsbProbeResult, UsbAudioStreamHandle, android.hardware.usb.UsbDevice) -> T,
    ): T {
        val probe = probeOnAppProcess()
        val deviceName = usbAppProcessRule.runOnActivity { activity ->
            UsbAudioEnumerator(activity).listUsbDevices().first {
                it.vendorId == FLOW8_VENDOR_ID && it.productId == FLOW8_PRODUCT_ID
            }.deviceName
        }
        return runBlocking {
            val stream = usbAppProcessRule.runOnActivity { activity ->
                val usbManager = activity.getSystemService(UsbManager::class.java)
                val device = usbManager.deviceList[deviceName]
                    ?: error("Flow 8 not in UsbManager device list")
                UsbInstrumentedPermission.ensure(activity, usbManager, device)
                UsbAudioStreamHandle.open(activity, usbManager, device)
            } ?: error("Could not open USB stream — run scripts/grant-usb-permission.sh")
            val device = usbAppProcessRule.runOnActivity { activity ->
                (activity.getSystemService(UsbManager::class.java)).deviceList[deviceName]!!
            }
            try {
                block(probe, stream, device)
            } finally {
                stream.close()
            }
        }
    }

    private companion object {
        const val FLOW8_VENDOR_ID = 0x1397
        const val FLOW8_PRODUCT_ID = 0x050c
        const val VIRTUAL_SOUNDCHECK_CHANNELS = 4
    }
}
